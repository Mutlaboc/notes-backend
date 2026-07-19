package com.example.mutlabocnotes.achievements

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Хранилище достижений. Клиент — источник прогресса (считает офлайн), сервер
 * агрегирует между устройствами: метрики сливаются по max (счётчики монотонные,
 * отставшее устройство не может откатить прогресс), анлоки — union по первичному
 * ключу. Обе записи идемпотентны по (user_id, operation_id).
 */
open class AchievementsRepository {

    // Полное состояние достижений пользователя для pull на клиенте.
    open fun snapshot(userId: UUID): AchievementsSnapshotDto = transaction {
        AchievementsSnapshotDto(
            metrics = AchievementMetricsTable.selectAll()
                .where { AchievementMetricsTable.userId eq userId }
                .orderBy(AchievementMetricsTable.metricKey, SortOrder.ASC)
                .map {
                    AchievementMetricDto(
                        key = it[AchievementMetricsTable.metricKey],
                        value = it[AchievementMetricsTable.value],
                    )
                },
            unlocks = AchievementUnlocksTable.selectAll()
                .where { AchievementUnlocksTable.userId eq userId }
                .orderBy(AchievementUnlocksTable.unlockedAt, SortOrder.ASC)
                .map {
                    AchievementUnlockDto(
                        achievementId = it[AchievementUnlocksTable.achievementId],
                        tier = it[AchievementUnlocksTable.tier],
                        points = it[AchievementUnlocksTable.points],
                        unlockedAt = it[AchievementUnlocksTable.unlockedAt],
                    )
                },
        )
    }

    // Слияние снапшота метрик клиента по max; повторная операция — no-op.
    open fun mergeMetrics(userId: UUID, operationId: UUID, metrics: List<AchievementMetricDto>) {
        transaction {
            if (!tryRecordOperation(userId, operationId)) return@transaction

            val existing = AchievementMetricsTable.selectAll()
                .where { AchievementMetricsTable.userId eq userId }
                .associate { it[AchievementMetricsTable.metricKey] to it[AchievementMetricsTable.value] }
            val now = OffsetDateTime.now(ZoneOffset.UTC)

            metrics.forEach { metric ->
                val current = existing[metric.key]
                when {
                    current == null -> AchievementMetricsTable.insert {
                        it[AchievementMetricsTable.userId] = userId
                        it[metricKey] = metric.key
                        it[value] = metric.value
                        it[updatedAt] = now
                    }

                    metric.value > current -> AchievementMetricsTable.update({
                        (AchievementMetricsTable.userId eq userId) and
                            (AchievementMetricsTable.metricKey eq metric.key)
                    }) {
                        it[value] = metric.value
                        it[updatedAt] = now
                    }
                }
            }
        }
    }

    /**
     * Регистрирует взятую ступень; дубликат ступени (пришла с другого устройства
     * раньше) молча пропускается. Ошибки: [IllegalArgumentException] — неизвестная
     * ступень (400 на роуте).
     */
    open fun registerUnlock(userId: UUID, operationId: UUID, unlock: AchievementUnlockDto) {
        require(unlock.tier in ALLOWED_TIERS) { "Unknown tier: ${unlock.tier}" }
        require(unlock.points >= 0) { "Negative points" }
        require(unlock.achievementId.isNotBlank()) { "Blank achievement id" }

        transaction {
            if (!tryRecordOperation(userId, operationId)) return@transaction

            val exists = AchievementUnlocksTable.selectAll().where {
                (AchievementUnlocksTable.userId eq userId) and
                    (AchievementUnlocksTable.achievementId eq unlock.achievementId) and
                    (AchievementUnlocksTable.tier eq unlock.tier)
            }.singleOrNull() != null
            if (exists) return@transaction

            AchievementUnlocksTable.insert {
                it[AchievementUnlocksTable.userId] = userId
                it[achievementId] = unlock.achievementId
                it[tier] = unlock.tier
                it[points] = unlock.points
                it[unlockedAt] = unlock.unlockedAt
                it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
    }

    // Отмечает операцию выполненной; false — операция уже применялась (внутри транзакции).
    private fun tryRecordOperation(userId: UUID, operationId: UUID): Boolean {
        val seen = AchievementOperationsTable.selectAll().where {
            (AchievementOperationsTable.userId eq userId) and
                (AchievementOperationsTable.operationId eq operationId)
        }.singleOrNull() != null
        if (seen) return false

        AchievementOperationsTable.insert {
            it[AchievementOperationsTable.operationId] = operationId
            it[AchievementOperationsTable.userId] = userId
            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        return true
    }

    private companion object {
        val ALLOWED_TIERS = setOf("BRONZE", "SILVER", "GOLD", "PLATINUM")
    }
}
