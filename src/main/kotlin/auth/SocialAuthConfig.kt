package com.example.mutlabocnotes.auth

import io.ktor.server.config.ApplicationConfig

data class SocialAuthConfig(
    val googleWebClientId: String?,
    val yandexClientId: String?,
    val yandexClientSecret: String?
)

fun ApplicationConfig.readSocialAuthConfig(): SocialAuthConfig {
    return SocialAuthConfig(
        googleWebClientId = propertyOrNull("socialAuth.google.webClientId")?.getString()?.trim(),
        yandexClientId = propertyOrNull("socialAuth.yandex.clientId")?.getString()?.trim(),
        yandexClientSecret = propertyOrNull("socialAuth.yandex.clientSecret")?.getString()?.trim()
    )
}

private fun ApplicationConfig.propertyOrNull(path: String) =
    try {
        property(path)
    } catch (_: Exception) {
        null
    }