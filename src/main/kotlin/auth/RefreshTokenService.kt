package com.example.mutlabocnotes.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class RefreshTokenService(
    private val jwtConfig: JwtConfig
) {
    private val secureRandom = SecureRandom()

    fun generateToken(): String {
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") { byte ->
            "%02x".format(byte)
        }
    }

    fun expiresAt() =
        Instant.now()
            .plusSeconds(jwtConfig.refreshTokenTtlSeconds)
            .atOffset(ZoneOffset.UTC)

    fun refreshExpiresInSeconds(): Long = jwtConfig.refreshTokenTtlSeconds
}
