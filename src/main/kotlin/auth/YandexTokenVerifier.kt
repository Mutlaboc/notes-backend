package com.example.mutlabocnotes.auth

interface YandexTokenVerifier {
    suspend fun verifyAccessToken(accessToken: String): VerifiedSocialIdentity
}

class NotReadyYandexTokenVerifier(
    private val config: SocialAuthConfig
) : YandexTokenVerifier {

    override suspend fun verifyAccessToken(accessToken: String): VerifiedSocialIdentity {
        if (config.yandexClientId.isNullOrBlank() || config.yandexClientSecret.isNullOrBlank()) {
            throw SocialAuthNotReadyException("yandex_auth_not_configured")
        }

        throw SocialAuthNotReadyException("yandex_auth_not_implemented")
    }
}