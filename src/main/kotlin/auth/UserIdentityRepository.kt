@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

import com.example.mutlabocnotes.database.DatabaseFactory
import com.example.mutlabocnotes.database.table.UserIdentitiesTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset
import kotlin.uuid.Uuid

data class UserIdentityModel(
    val id: Uuid,
    val userId: Uuid,
    val provider: SocialProvider,
    val providerUserId: String,
    val email: String?,
    val emailVerified: Boolean,
    val displayName: String?,
    val avatarUrl: String?,
    val providerUsername: String?,
    val createdAt: java.time.OffsetDateTime,
    val updatedAt: java.time.OffsetDateTime,
    val lastLoginAt: java.time.OffsetDateTime?
)

class UserIdentityRepository {

    suspend fun findByProviderAndProviderUserId(
        provider: SocialProvider,
        providerUserId: String
    ): UserIdentityModel? =
        DatabaseFactory.dbQuery {
            UserIdentitiesTable
                .selectAll()
                .where {
                    (UserIdentitiesTable.provider eq provider.name) and
                        (UserIdentitiesTable.providerUserId eq providerUserId)
                }
                .singleOrNull()
                ?.toUserIdentityModel()
        }

    suspend fun create(
        userId: Uuid,
        identity: VerifiedSocialIdentity,
        verifiedEmail: String
    ): UserIdentityModel {
        val identityId = Uuid.random()
        val now = Instant.now().atOffset(ZoneOffset.UTC)

        DatabaseFactory.dbQuery {
            UserIdentitiesTable.insert {
                it[id] = identityId
                it[this.userId] = userId
                it[provider] = identity.provider.name
                it[providerUserId] = identity.providerUserId
                it[email] = verifiedEmail
                it[emailVerified] = identity.emailVerified
                it[displayName] = identity.displayName
                it[avatarUrl] = identity.avatarUrl
                it[providerUsername] = identity.providerUsername
                it[createdAt] = now
                it[updatedAt] = now
                it[lastLoginAt] = now
            }
        }

        return findById(identityId) ?: error("Created user identity not found")
    }

    suspend fun touchLogin(
        identityId: Uuid,
        identity: VerifiedSocialIdentity,
        verifiedEmailOrNull: String?
    ) {
        val now = Instant.now().atOffset(ZoneOffset.UTC)

        DatabaseFactory.dbQuery {
            UserIdentitiesTable.update(
                where = { UserIdentitiesTable.id eq identityId }
            ) {
                if (verifiedEmailOrNull != null) {
                    it[email] = verifiedEmailOrNull
                    it[emailVerified] = true
                }

                it[displayName] = identity.displayName
                it[avatarUrl] = identity.avatarUrl
                it[providerUsername] = identity.providerUsername
                it[updatedAt] = now
                it[lastLoginAt] = now
            }
        }
    }

    private suspend fun findById(identityId: Uuid): UserIdentityModel? =
        DatabaseFactory.dbQuery {
            UserIdentitiesTable
                .selectAll()
                .where { UserIdentitiesTable.id eq identityId }
                .singleOrNull()
                ?.toUserIdentityModel()
        }

    private fun ResultRow.toUserIdentityModel(): UserIdentityModel =
        UserIdentityModel(
            id = this[UserIdentitiesTable.id],
            userId = this[UserIdentitiesTable.userId],
            provider = SocialProvider.valueOf(this[UserIdentitiesTable.provider]),
            providerUserId = this[UserIdentitiesTable.providerUserId],
            email = this[UserIdentitiesTable.email],
            emailVerified = this[UserIdentitiesTable.emailVerified],
            displayName = this[UserIdentitiesTable.displayName],
            avatarUrl = this[UserIdentitiesTable.avatarUrl],
            providerUsername = this[UserIdentitiesTable.providerUsername],
            createdAt = this[UserIdentitiesTable.createdAt],
            updatedAt = this[UserIdentitiesTable.updatedAt],
            lastLoginAt = this[UserIdentitiesTable.lastLoginAt]
        )
}
