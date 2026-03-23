@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package com.example.mutlabocnotes.database.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object UsersTable : Table("users") {
    val firebaseUid = varchar("firebase_uid", 255).nullable()
    val email = varchar("email", 320).nullable().uniqueIndex("ux_users_email")
    val passwordHash = text("password_hash").nullable()
    val displayName = text("display_name").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val lastLoginAt = timestampWithTimeZone("last_login_at").nullable()
}