package com.example.mutlabocnotes.auth

data class VerifiedSocialIdentity(
    val provider: SocialProvider,
    val providerUserId: String,
    val email: String?,
    val emailVerified: Boolean,
    val displayName: String?,
    val avatarUrl: String?,
    val providerUsername: String? = null
)