package com.example.mutlabocnotes.auth

import kotlinx.serialization.Serializable

@Serializable
data class GoogleSocialLoginRequestDto(
    val idToken: String
)

@Serializable
data class YandexSocialLoginRequestDto(
    val accessToken: String
)

@Serializable
data class ErrorResponseDto(
    val error: String
)