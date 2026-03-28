@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.database.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object UserIdentitiesTable : Table("user_identities") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val provider = varchar("provider", 32)
    val providerUserId = text("provider_user_id")
    val email = text("email").nullable()
    val emailVerified = bool("email_verified").default(false)
    val displayName = text("display_name").nullable()
    val avatarUrl = text("avatar_url").nullable()
    val providerUsername = text("provider_username").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val lastLoginAt = timestampWithTimeZone("last_login_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
