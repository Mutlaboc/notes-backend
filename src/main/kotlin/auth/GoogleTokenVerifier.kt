package com.example.mutlabocnotes.auth

// Компонент для проверки внешних токенов и данных.
interface GoogleTokenVerifier {
    // Проверяет корректность входных данных и условий доступа.
    suspend fun verifyIdToken(idToken: String): VerifiedSocialIdentity
}

// Компонент для проверки внешних токенов и данных.
class NotReadyGoogleTokenVerifier(
    private val config: SocialAuthConfig
) : GoogleTokenVerifier {

    // Проверяет корректность входных данных и условий доступа.
    override suspend fun verifyIdToken(idToken: String): VerifiedSocialIdentity {
        if (config.googleWebClientId.isNullOrBlank()) {
            throw SocialAuthNotReadyException("google_auth_not_configured")
        }

        throw SocialAuthNotReadyException("google_auth_not_implemented")
    }
}