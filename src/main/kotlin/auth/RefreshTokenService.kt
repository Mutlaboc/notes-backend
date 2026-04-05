package com.example.mutlabocnotes.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

// Сервис с прикладной бизнес-логикой модуля.
class RefreshTokenService(
    private val jwtConfig: JwtConfig
) {
    private val secureRandom = SecureRandom()

    // Генерирует токен или идентификатор для дальнейшего использования.
    fun generateToken(): String {
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    // Вычисляет хеш для переданного значения.
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") { byte ->
            "%02x".format(byte)
        }
    }

    // Реализует шаг «expires at» в рамках текущего процесса.
    fun expiresAt() =
        Instant.now()
            .plusSeconds(jwtConfig.refreshTokenTtlSeconds)
            .atOffset(ZoneOffset.UTC)

    // Обновляет сессионные токены пользователя.
    fun refreshExpiresInSeconds(): Long = jwtConfig.refreshTokenTtlSeconds
}
