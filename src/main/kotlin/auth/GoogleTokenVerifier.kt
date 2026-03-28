package com.example.mutlabocnotes.auth

interface GoogleTokenVerifier {
    suspend fun verifyIdToken(idToken: String): VerifiedSocialIdentity
}

class NotReadyGoogleTokenVerifier(
    private val config: SocialAuthConfig
) : GoogleTokenVerifier {

    override suspend fun verifyIdToken(idToken: String): VerifiedSocialIdentity {
        if (config.googleWebClientId.isNullOrBlank()) {
            throw SocialAuthNotReadyException("google_auth_not_configured")
        }

        throw SocialAuthNotReadyException("google_auth_not_implemented")
    }
}