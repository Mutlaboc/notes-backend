@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

import kotlin.uuid.Uuid

interface SocialUserResolver {
    suspend fun resolveOrCreateUserId(identity: VerifiedSocialIdentity): Uuid
}

class DatabaseSocialUserResolver(
    private val authRepository: AuthRepository,
    private val userIdentityRepository: UserIdentityRepository
) : SocialUserResolver {

    override suspend fun resolveOrCreateUserId(identity: VerifiedSocialIdentity): Uuid {
        val normalizedIdentity = identity.copy(
            displayName = normalizeDisplayName(identity.displayName)
        )

        val existingIdentity = userIdentityRepository.findByProviderAndProviderUserId(
            provider = normalizedIdentity.provider,
            providerUserId = normalizedIdentity.providerUserId
        )

        if (existingIdentity != null) {
            userIdentityRepository.touchLogin(
                identityId = existingIdentity.id,
                identity = normalizedIdentity,
                verifiedEmailOrNull = normalizeVerifiedEmailOrNull(normalizedIdentity)
            )
            return existingIdentity.userId
        }

        val verifiedEmail = requireVerifiedEmail(normalizedIdentity)

        val existingUser = authRepository.findByEmail(verifiedEmail)
        val userId = if (existingUser != null) {
            if (!existingUser.isActive) {
                throw SocialIdentityResolutionException("social_target_user_inactive")
            }
            existingUser.id
        } else {
            authRepository.createSocialUser(
                email = verifiedEmail,
                displayName = normalizedIdentity.displayName
            ).id
        }

        userIdentityRepository.create(
            userId = userId,
            identity = normalizedIdentity,
            verifiedEmail = verifiedEmail
        )

        return userId
    }

    private fun requireVerifiedEmail(identity: VerifiedSocialIdentity): String {
        val verifiedEmail = normalizeVerifiedEmailOrNull(identity)
        if (verifiedEmail != null) return verifiedEmail

        val rawEmail = identity.email?.trim()
        if (rawEmail.isNullOrEmpty()) {
            throw SocialIdentityResolutionException("social_email_missing")
        }

        if (!identity.emailVerified) {
            throw SocialIdentityResolutionException("social_email_not_verified")
        }

        throw SocialIdentityResolutionException("social_email_invalid")
    }

    private fun normalizeVerifiedEmailOrNull(identity: VerifiedSocialIdentity): String? {
        val email = identity.email
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        if (!identity.emailVerified) {
            return null
        }

        if (email.length > 320) {
            return null
        }

        if (" " in email) {
            return null
        }

        if (email.count { it == '@' } != 1) {
            return null
        }

        return email
    }

    private fun normalizeDisplayName(displayName: String?): String? {
        val normalized = displayName?.trim()?.takeIf { it.isNotEmpty() }
        return normalized?.take(120)
    }
}
