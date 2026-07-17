@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.character

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

// Маршруты листа персонажа: чтение (с авто-созданием дефолтного) и полное обновление.
fun Route.characterRoutes(
    repository: CharacterRepository = CharacterRepository(),
) {
    authenticate("auth-jwt") {
        route("/character") {
            get {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@get
                call.respond(repository.getOrCreateForUser(userId))
            }

            put {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@put
                val request = call.receive<CharacterUpdateRequestDto>()
                if (!request.isValid()) {
                    return@put call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                }
                call.respond(repository.updateForUser(userId, request))
            }

            post("/xp") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@post
                val request = call.receive<CharacterXpRequestDto>()
                if (request.characterXp < 0 || request.skillXp < 0) {
                    return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                }
                val operationId = request.operationId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (request.operationId != null && operationId == null) {
                    return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                }
                call.respond(
                    repository.addExperience(
                        userId = userId,
                        characterXp = request.characterXp,
                        skillKey = request.skillKey,
                        skillXp = request.skillXp,
                        operationId = operationId,
                    )
                )
            }

            post("/stat-upgrades") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@post
                val request = call.receive<CharacterStatUpgradeRequestDto>()
                val operationId = runCatching { UUID.fromString(request.operationId) }.getOrNull()
                    ?: return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                if (request.statKey.isBlank()) {
                    return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                }
                val updated = runCatching { repository.upgradeStat(userId, operationId, request.statKey) }
                    .getOrElse {
                        return@post call.respondApiError(
                            if (it is IllegalStateException) HttpStatusCode.Conflict else HttpStatusCode.BadRequest,
                            ApiErrorCodes.INVALID_REQUEST,
                        )
                    }
                call.respond(updated)
            }

            post("/rename") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@post
                val request = call.receive<CharacterRenameRequestDto>()
                val operationId = runCatching { UUID.fromString(request.operationId) }.getOrNull()
                    ?: return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                if (request.name.isBlank()) {
                    return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                }
                call.respond(repository.rename(userId, operationId, request.name.trim()))
            }
        }
    }
}

private fun CharacterUpdateRequestDto.isValid(): Boolean {
    if (level < 1 || xp < 0 || xpToNext < 1) return false
    if (stats.any { it.key.isBlank() || it.name.isBlank() }) return false
    if (skills.any { it.key.isBlank() || it.name.isBlank() || it.level < 1 || it.progress < 0.0 || it.progress > 1.0 }) {
        return false
    }
    return true
}
