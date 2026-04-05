package com.example.mutlabocnotes.auth

import kotlinx.serialization.Serializable

// DTO-модель для обмена данными между API и доменом.
@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val displayName: String? = null
)

// DTO-модель для обмена данными между API и доменом.
@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

// DTO-модель для обмена данными между API и доменом.
@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String
)

// DTO-модель для обмена данными между API и доменом.
@Serializable
data class AuthUserResponseDto(
    val id: String,
    val email: String,
    val displayName: String? = null,
    val bridgeUserKey: String? = null
)

// DTO-модель для обмена данными между API и доменом.
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
