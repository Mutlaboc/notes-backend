@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

import kotlin.uuid.Uuid

data class AuthUserModel(
    val id: Uuid,
    val email: String?,
    val passwordHash: String?,
    val firebaseUid: String?,
    val displayName: String?,
    val isActive: Boolean
)
