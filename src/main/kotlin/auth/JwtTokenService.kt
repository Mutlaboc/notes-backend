@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import java.time.Instant
import java.util.Date

// Сервис с прикладной бизнес-логикой модуля.
class JwtTokenService(
    private val config: JwtConfig
) {
    private val algorithm = Algorithm.HMAC256(config.secret)

    // Генерирует токен или идентификатор для дальнейшего использования.
    fun generateAccessToken(user: AuthUserModel): String {
        val email = user.email ?: error("Cannot issue JWT for user without email")
        val expiresAt = Instant.now().plusSeconds(config.accessTokenTtlSeconds)

        val builder = JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(user.id.toString())
            .withClaim("email", email)
            .withExpiresAt(Date.from(expiresAt))

        if (!user.displayName.isNullOrBlank()) {
            builder.withClaim("displayName", user.displayName)
        }

        return builder.sign(algorithm)
    }

    // Реализует шаг «verifier» в рамках текущего процесса.
    fun verifier(): JWTVerifier =
        JWT.require(algorithm)
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .build()

    // Реализует шаг «expires in seconds» в рамках текущего процесса.
    fun expiresInSeconds(): Long = config.accessTokenTtlSeconds
}
