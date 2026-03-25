@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

import com.example.mutlabocnotes.database.DatabaseFactory
import com.example.mutlabocnotes.database.table.RefreshTokensTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

data class RefreshTokenModel(
    val id: Uuid,
    val userId: Uuid,
    val tokenHash: String,
    val expiresAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?
)

class RefreshTokenRepository {

    suspend fun create(
        userId: Uuid,
        tokenHash: String,
        expiresAt: OffsetDateTime
    ): RefreshTokenModel = DatabaseFactory.dbQuery {
        val tokenId = Uuid.random()
        val createdAt = OffsetDateTime.now()

        RefreshTokensTable.insert {
            it[id] = tokenId
            it[RefreshTokensTable.userId] = userId
            it[RefreshTokensTable.tokenHash] = tokenHash
            it[RefreshTokensTable.expiresAt] = expiresAt
            it[RefreshTokensTable.createdAt] = createdAt
            it[revokedAt] = null
        }

        RefreshTokenModel(
            id = tokenId,
            userId = userId,
            tokenHash = tokenHash,
            expiresAt = expiresAt,
            createdAt = createdAt,
            revokedAt = null
        )
    }

    suspend fun findByTokenHash(tokenHash: String): RefreshTokenModel? =
        DatabaseFactory.dbQuery {
            RefreshTokensTable
                .selectAll()
                .where { RefreshTokensTable.tokenHash eq tokenHash }
                .singleOrNull()
                ?.toRefreshTokenModel()
        }

    suspend fun revokeByTokenHash(tokenHash: String) {
        DatabaseFactory.dbQuery {
            RefreshTokensTable.update(
                where = {
                    (RefreshTokensTable.tokenHash eq tokenHash) and
                        (RefreshTokensTable.revokedAt eq null)
                }
            ) {
                it[revokedAt] = OffsetDateTime.now()
            }
        }
    }

    suspend fun revokeAllByUserId(userId: Uuid) {
        DatabaseFactory.dbQuery {
            RefreshTokensTable.update(
                where = {
                    (RefreshTokensTable.userId eq userId) and
                        (RefreshTokensTable.revokedAt eq null)
                }
            ) {
                it[revokedAt] = OffsetDateTime.now()
            }
        }
    }

    private fun ResultRow.toRefreshTokenModel(): RefreshTokenModel =
        RefreshTokenModel(
            id = this[RefreshTokensTable.id],
            userId = this[RefreshTokensTable.userId],
            tokenHash = this[RefreshTokensTable.tokenHash],
            expiresAt = this[RefreshTokensTable.expiresAt],
            createdAt = this[RefreshTokensTable.createdAt],
            revokedAt = this[RefreshTokensTable.revokedAt]
        )
}
