package com.example.mutlabocnotes.auth

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val displayName: String? = null
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String
)

@Serializable
data class AuthUserResponseDto(
    val id: String,
    val email: String,
    val displayName: String? = null,
    val bridgeUserKey: String? = null
)

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
    val refreshExpiresInSeconds: Long,
    val bridgeUserKey: String? = null,
    val user: AuthUserResponseDto
)
