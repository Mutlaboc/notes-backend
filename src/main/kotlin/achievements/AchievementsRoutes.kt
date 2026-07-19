@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.achievements

import com.example.mutlabocnotes.api.ApiErrorCodes
import com.example.mutlabocnotes.api.respondApiError
import com.example.mutlabocnotes.auth.requireCurrentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

// Маршруты достижений: снапшот для pull, merge метрик и идемпотентная регистрация ступеней.
fun Route.achievementsRoutes(
    repository: AchievementsRepository = AchievementsRepository(),
) {
    authenticate("auth-jwt") {
        route("/achievements") {
            get {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@get
                call.respond(repository.snapshot(userId))
            }

            put("/metrics") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@put
                val request = call.receive<AchievementMetricsRequestDto>()
                val operationId = runCatching { UUID.fromString(request.operationId) }.getOrNull()
                    ?: return@put call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                if (request.metrics.any { it.key.isBlank() }) {
                    return@put call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                }
                repository.mergeMetrics(userId, operationId, request.metrics)
                call.respond(AchievementsAckDto())
            }

            post("/unlocks") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@post
                val request = call.receive<AchievementUnlockRequestDto>()
                val operationId = runCatching { UUID.fromString(request.operationId) }.getOrNull()
                    ?: return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                runCatching {
                    repository.registerUnlock(
                        userId = userId,
                        operationId = operationId,
                        unlock = AchievementUnlockDto(
                            achievementId = request.achievementId.trim(),
                            tier = request.tier.trim().uppercase(),
                            points = request.points,
                            unlockedAt = request.unlockedAt,
                        ),
                    )
                }.getOrElse { error ->
                    return@post when (error) {
                        is IllegalArgumentException ->
                            call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                        else -> throw error
                    }
                }
                call.respond(AchievementsAckDto())
            }
        }
    }
}
