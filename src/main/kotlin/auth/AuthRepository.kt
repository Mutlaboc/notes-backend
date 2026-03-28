@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

import com.example.mutlabocnotes.database.DatabaseFactory
import com.example.mutlabocnotes.database.table.UsersTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.Uuid

class AuthRepository {

    suspend fun findById(userId: Uuid): AuthUserModel? =
        DatabaseFactory.dbQuery {
            UsersTable
                .selectAll()
                .where { UsersTable.id eq userId }
                .singleOrNull()
                ?.toAuthUser()
        }

    suspend fun findByEmail(email: String): AuthUserModel? =
        DatabaseFactory.dbQuery {
            UsersTable
                .selectAll()
                .where { UsersTable.email eq email }
                .singleOrNull()
                ?.toAuthUser()
        }

    suspend fun createLocalUser(
        email: String,
        passwordHash: String,
        displayName: String?
    ): AuthUserModel =
        createUser(
            email = email,
            passwordHash = passwordHash,
            displayName = displayName
        )

    suspend fun createSocialUser(
        email: String,
        displayName: String?
    ): AuthUserModel =
        createUser(
            email = email,
            passwordHash = null,
            displayName = displayName
        )

    private suspend fun createUser(
        email: String,
        passwordHash: String?,
        displayName: String?
    ): AuthUserModel {
        val userId = Uuid.random()
        val bridgeUserKey = buildBridgeUserKey(userId)
        val now = Instant.now().atOffset(ZoneOffset.UTC)

        DatabaseFactory.dbQuery {
            UsersTable.insert {
                it[id] = userId
                it[UsersTable.email] = email
                it[UsersTable.passwordHash] = passwordHash
                it[UsersTable.displayName] = displayName
                it[UsersTable.firebaseUid] = bridgeUserKey
                it[UsersTable.isActive] = true
                it[UsersTable.createdAt] = now
                it[UsersTable.updatedAt] = now
                it[UsersTable.lastLoginAt] = null
            }
        }

        return findById(userId) ?: error("Created user not found")
    }

    suspend fun updateLastLogin(userId: Uuid) {
        DatabaseFactory.dbQuery {
            UsersTable.update(
                where = { UsersTable.id eq userId }
            ) {
                it[lastLoginAt] = Instant.now().atOffset(ZoneOffset.UTC)
            }
        }
    }

    private fun ResultRow.toAuthUser(): AuthUserModel =
        AuthUserModel(
            id = this[UsersTable.id],
            email = this[UsersTable.email],
            passwordHash = this[UsersTable.passwordHash],
            firebaseUid = this[UsersTable.firebaseUid],
            displayName = this[UsersTable.displayName],
            isActive = this[UsersTable.isActive]
        )

    private fun buildBridgeUserKey(userId: Uuid): String = "local:${userId}"
}
