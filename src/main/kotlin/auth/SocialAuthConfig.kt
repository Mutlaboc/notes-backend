package com.example.mutlabocnotes.auth

import io.ktor.server.config.ApplicationConfig

// Модель данных, используемая в бизнес-логике.
data class SocialAuthConfig(
    val googleWebClientId: String?,
    val yandexClientId: String?,
    val yandexClientSecret: String?
)

// Реализует шаг «read social auth config» в рамках текущего процесса.
fun ApplicationConfig.readSocialAuthConfig(): SocialAuthConfig {
    return SocialAuthConfig(
        googleWebClientId = propertyOrNull("socialAuth.google.webClientId")?.getString()?.trim(),
        yandexClientId = propertyOrNull("socialAuth.yandex.clientId")?.getString()?.trim(),
        yandexClientSecret = propertyOrNull("socialAuth.yandex.clientSecret")?.getString()?.trim()
    )
}

// Реализует шаг «property or null» в рамках текущего процесса.
private fun ApplicationConfig.propertyOrNull(path: String) =
    try {
        property(path)
    } catch (_: Exception) {
        null
    }