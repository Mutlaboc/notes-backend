package com.example.mutlabocnotes.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt

// Настраивает выбранную подсистему приложения.
fun Application.configureJwtAuthentication(
    jwtConfig: JwtConfig,
    jwtTokenService: JwtTokenService
) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtConfig.realm
            verifier(jwtTokenService.verifier())
            validate { credential ->
                val subject = credential.payload.subject
                if (!subject.isNullOrBlank()) {
                    io.ktor.server.auth.jwt.JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}