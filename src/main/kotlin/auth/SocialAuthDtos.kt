package com.example.mutlabocnotes.auth

import kotlinx.serialization.Serializable

// DTO-модель для обмена данными между API и доменом.
@Serializable
data class GoogleSocialLoginRequestDto(
    val idToken: String
)

// DTO-модель для обмена данными между API и доменом.
@Serializable
data class YandexSocialLoginRequestDto(
    val accessToken: String
)

// DTO-модель для обмена данными между API и доменом.
@Serializable
data class ErrorResponseDto(
    val error: String
)