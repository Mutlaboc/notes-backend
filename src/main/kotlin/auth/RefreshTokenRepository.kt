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

// Модель данных, используемая в бизнес-логике.
data class RefreshTokenModel(
    val id: Uuid,
    val userId: Uuid,
    val tokenHash: String,
    val expiresAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?
)

// Репозиторий для доступа к данным и работы с БД.
class RefreshTokenRepository {

    // Создаёт новую заметку и связанный чеклист.
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

    // Возвращает данные по заданным параметрам запроса.
    suspend fun findByTokenHash(tokenHash: String): RefreshTokenModel? =
        DatabaseFactory.dbQuery {
            RefreshTokensTable
                .selectAll()
                .where { RefreshTokensTable.tokenHash eq tokenHash }
                .singleOrNull()
                ?.toRefreshTokenModel()
        }

    // Удаляет или отзывает данные в рамках текущего сценария.
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

    // Удаляет или отзывает данные в рамках текущего сценария.
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

    // Преобразует данные в нужный формат представления.
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
