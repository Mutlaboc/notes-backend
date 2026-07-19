package com.example.mutlabocnotes.achievements

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// Метрики-счётчики достижений: одна строка на (пользователь, ключ метрики).
object AchievementMetricsTable : Table("achievement_metrics") {
    val userId = javaUUID("user_id")
    val metricKey = text("metric_key")
    val value = long("value")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId, metricKey)
}

// Взятые ступени достижений; points — снапшот на момент взятия.
object AchievementUnlocksTable : Table("achievement_unlocks") {
    val userId = javaUUID("user_id")
    val achievementId = text("achievement_id")
    val tier = text("tier")
    val points = integer("points")
    val unlockedAt = long("unlocked_at")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(userId, achievementId, tier)
}

// Идемпотентность операций достижений (паттерн — character_xp_operations).
object AchievementOperationsTable : Table("achievement_operations") {
    val operationId = javaUUID("operation_id")
    val userId = javaUUID("user_id")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(userId, operationId)
}
