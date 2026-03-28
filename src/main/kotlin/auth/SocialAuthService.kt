package com.example.mutlabocnotes.auth

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)

class SocialAuthService(
    private val authService: AuthService,
    private val googleTokenVerifier: GoogleTokenVerifier,
    private val yandexTokenVerifier: YandexTokenVerifier,
    private val socialUserResolver: SocialUserResolver
) {

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