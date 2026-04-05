package com.example.mutlabocnotes.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// Компонент для проверки внешних токенов и данных.
interface YandexTokenVerifier {
    // Проверяет корректность входных данных и условий доступа.
    suspend fun verifyAccessToken(accessToken: String): VerifiedSocialIdentity
}

// Компонент для проверки внешних токенов и данных.
class NotReadyYandexTokenVerifier(
    private val config: SocialAuthConfig
) : YandexTokenVerifier {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Проверяет корректность входных данных и условий доступа.
    override suspend fun verifyAccessToken(accessToken: String): VerifiedSocialIdentity {
        val expectedClientId = config.yandexClientId?.takeIf { it.isNotBlank() }
            ?: throw SocialAuthNotReadyException("yandex_auth_not_configured")

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://login.yandex.ru/info?format=json"))
            .header("Authorization", "OAuth $accessToken")
            .header("Accept", "application/json")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        when (response.statusCode()) {
            401 -> throw SocialTokenValidationException("yandex_access_token_invalid")
            in 200..299 -> Unit
            else -> throw SocialTokenValidationException(
                "yandex_userinfo_request_failed_${response.statusCode()}"
            )
        }

        val payload = runCatching {
            json.decodeFromString<YandexUserInfoResponseDto>(response.body())
        }.getOrElse {
            throw SocialTokenValidationException("yandex_userinfo_invalid_json")
        }

        val providerUserId = payload.id?.trim().orEmpty()
        if (providerUserId.isBlank()) {
            throw SocialTokenValidationException("yandex_user_id_missing")
        }

        val tokenClientId = payload.clientId?.trim()
        if (!tokenClientId.isNullOrBlank() && tokenClientId != expectedClientId) {
            throw SocialTokenValidationException("yandex_client_id_mismatch")
        }

        val email = payload.defaultEmail?.trim()?.takeIf { it.isNotBlank() }
            ?: payload.emails
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val displayName = payload.displayName?.trim()?.takeIf { it.isNotBlank() }
            ?: payload.realName?.trim()?.takeIf { it.isNotBlank() }
            ?: listOf(payload.firstName, payload.lastName)
                .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
                .joinToString(" ")
                .takeIf(String::isNotBlank)
            ?: payload.login?.trim()?.takeIf { it.isNotBlank() }

        val avatarUrl = payload.defaultAvatarId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { avatarId ->
                "https://avatars.yandex.net/get-yapic/$avatarId/islands-200"
            }

        return VerifiedSocialIdentity(
            provider = SocialProvider.YANDEX,
            providerUserId = providerUserId,
            email = email,
            emailVerified = !email.isNullOrBlank(),
            displayName = displayName,
            avatarUrl = avatarUrl,
            providerUsername = payload.login?.trim()?.takeIf { it.isNotBlank() }
        )
    }
}

// DTO-модель для обмена данными между API и доменом.
@Serializable
private data class YandexUserInfoResponseDto(
    val id: String? = null,
    val login: String? = null,
    @SerialName("client_id")
    val clientId: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("real_name")
    val realName: String? = null,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    @SerialName("default_email")
    val defaultEmail: String? = null,
    val emails: List<String>? = null,
    @SerialName("default_avatar_id")
    val defaultAvatarId: String? = null
)
