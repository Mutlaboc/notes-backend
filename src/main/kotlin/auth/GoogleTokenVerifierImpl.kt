package com.example.mutlabocnotes.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory

// Класс с основной логикой данного модуля.
class GoogleTokenVerifierImpl(
    private val config: SocialAuthConfig
) : GoogleTokenVerifier {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val transport = GoogleNetHttpTransport.newTrustedTransport()

    private val verifier: GoogleIdTokenVerifier by lazy {
        val audience = config.googleWebClientId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw SocialAuthNotReadyException("google_auth_not_configured")

        GoogleIdTokenVerifier.Builder(transport, jsonFactory)
            .setAudience(listOf(audience))
            .build()
    }

    // Проверяет корректность входных данных и условий доступа.
    override suspend fun verifyIdToken(idToken: String): VerifiedSocialIdentity {
        val token = verifier.verify(idToken)
            ?: throw SocialTokenValidationException("google_id_token_invalid")

        val payload = token.payload

        val issuer = payload.issuer
        if (issuer != "accounts.google.com" && issuer != "https://accounts.google.com") {
            throw SocialTokenValidationException("google_invalid_issuer")
        }

        val providerUserId = payload.subject
            ?.takeIf { it.isNotBlank() }
            ?: throw SocialTokenValidationException("google_subject_missing")

        val email = payload.email?.trim()?.takeIf { it.isNotEmpty() }

        val emailVerified = when (val raw = payload["email_verified"]) {
            is Boolean -> raw
            is String -> raw.toBoolean()
            else -> false
        }

        val displayName = (payload["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val avatarUrl = (payload["picture"] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        return VerifiedSocialIdentity(
            provider = SocialProvider.GOOGLE,
            providerUserId = providerUserId,
            email = email,
            emailVerified = emailVerified,
            displayName = displayName,
            avatarUrl = avatarUrl,
            providerUsername = null
        )
    }
}
