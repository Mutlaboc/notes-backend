package com.example.mutlabocnotes.auth

import io.ktor.server.config.ApplicationConfig

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val realm: String,
    val secret: String,
    val accessTokenTtlSeconds: Long,
    val refreshTokenTtlSeconds: Long
)

fun ApplicationConfig.readJwtConfig(): JwtConfig {
    val config = config("jwt")
    val secret = config.property("secret").getString().trim()

    require(secret.isNotEmpty()) {
        "JWT secret is empty. Check JWT_SECRET in environment."
    }

    return JwtConfig(
        issuer = config.property("issuer").getString(),
        audience = config.property("audience").getString(),
        realm = config.property("realm").getString(),
        secret = secret,
        accessTokenTtlSeconds = config.property("accessTokenTtlSeconds").getString().toLong(),
        refreshTokenTtlSeconds = config.property("refreshTokenTtlSeconds").getString().toLong()
    )
}
