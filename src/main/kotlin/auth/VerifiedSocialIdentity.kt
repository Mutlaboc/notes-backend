package com.example.mutlabocnotes.auth

// Модель данных, используемая в бизнес-логике.
data class VerifiedSocialIdentity(
    val provider: SocialProvider,
    val providerUserId: String,
    val email: String?,
    val emailVerified: Boolean,
    val displayName: String?,
    val avatarUrl: String?,
    val providerUsername: String? = null
)