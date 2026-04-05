package com.example.mutlabocnotes.auth

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)

// Сервис с прикладной бизнес-логикой модуля.
class SocialAuthService(
    private val authService: AuthService,
    private val googleTokenVerifier: GoogleTokenVerifier,
    private val yandexTokenVerifier: YandexTokenVerifier,
    private val socialUserResolver: SocialUserResolver
) {

    // Реализует шаг «login with google» в рамках текущего процесса.
    suspend fun loginWithGoogle(request: GoogleSocialLoginRequestDto): AuthResponseDto {
        val idToken = request.idToken.trim()
        require(idToken.isNotEmpty()) { "Google idToken is required" }

        val verifiedIdentity = googleTokenVerifier.verifyIdToken(idToken)
        validateVerifiedIdentity(
            identity = verifiedIdentity,
            expectedProvider = SocialProvider.GOOGLE
        )

        val userId = socialUserResolver.resolveOrCreateUserId(verifiedIdentity)
        return authService.issueSessionForUserId(userId)
    }

    // Реализует шаг «login with yandex» в рамках текущего процесса.
    suspend fun loginWithYandex(request: YandexSocialLoginRequestDto): AuthResponseDto {
        val accessToken = request.accessToken.trim()
        require(accessToken.isNotEmpty()) { "Yandex accessToken is required" }

        val verifiedIdentity = yandexTokenVerifier.verifyAccessToken(accessToken)
        validateVerifiedIdentity(
            identity = verifiedIdentity,
            expectedProvider = SocialProvider.YANDEX
        )

        val userId = socialUserResolver.resolveOrCreateUserId(verifiedIdentity)
        return authService.issueSessionForUserId(userId)
    }

    // Проверяет корректность входных данных и условий доступа.
    private fun validateVerifiedIdentity(
        identity: VerifiedSocialIdentity,
        expectedProvider: SocialProvider
    ) {
        if (identity.provider != expectedProvider) {
            throw SocialTokenValidationException("social_provider_mismatch")
        }

        if (identity.providerUserId.isBlank()) {
            throw SocialTokenValidationException("social_provider_user_id_blank")
        }
    }
}