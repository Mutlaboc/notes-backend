@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

import kotlin.uuid.Uuid

interface SocialUserResolver {
    suspend fun resolveOrCreateUserId(identity: VerifiedSocialIdentity): Uuid
}

class NotReadySocialUserResolver : SocialUserResolver {
    override suspend fun resolveOrCreateUserId(identity: VerifiedSocialIdentity): Uuid {
        throw SocialAuthNotReadyException("social_user_resolver_not_implemented")
    }
}