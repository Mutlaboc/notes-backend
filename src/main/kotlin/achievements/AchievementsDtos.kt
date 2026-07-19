package com.example.mutlabocnotes.achievements

import kotlinx.serialization.Serializable

// Метрика достижений: ключ счётчика и значение (контракт совпадает с клиентским AchievementsApi).
@Serializable
data class AchievementMetricDto(
    val key: String,
    val value: Long,
)

// Взятая ступень достижения.
@Serializable
data class AchievementUnlockDto(
    val achievementId: String,
    val tier: String,
    val points: Int,
    val unlockedAt: Long,
)

// Ответ GET /achievements: серверное состояние метрик и анлоков пользователя.
@Serializable
data class AchievementsSnapshotDto(
    val metrics: List<AchievementMetricDto> = emptyList(),
    val unlocks: List<AchievementUnlockDto> = emptyList(),
)

// Тело PUT /achievements/metrics: полный снапшот локальных метрик клиента.
@Serializable
data class AchievementMetricsRequestDto(
    val operationId: String,
    val metrics: List<AchievementMetricDto>,
)

// Тело POST /achievements/unlocks; идемпотентно по operationId.
@Serializable
data class AchievementUnlockRequestDto(
    val operationId: String,
    val achievementId: String,
    val tier: String,
    val points: Int,
    val unlockedAt: Long,
)

@Serializable
data class AchievementsAckDto(
    val ok: Boolean = true,
)
