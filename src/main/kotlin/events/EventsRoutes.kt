@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.events

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
import io.ktor.server.routing.route
import java.util.UUID

// Маршруты событий фокус-таймера: каталог для кэша клиента и идемпотентный клейм наград.
fun Route.eventsRoutes(
    repository: EventsRepository = EventsRepository(),
) {
    authenticate("auth-jwt") {
        route("/events") {
            get {
                call.requireCurrentUserId() ?: return@get
                call.respond(repository.catalog())
            }

            post("/claims") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@post
                val request = call.receive<FocusEventClaimRequestDto>()
                val operationId = runCatching { UUID.fromString(request.operationId) }.getOrNull()
                    ?: return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                if (request.eventKey.isBlank()) {
                    return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                }
                val response = runCatching {
                    repository.claim(
                        userId = userId,
                        operationId = operationId,
                        eventKey = request.eventKey.trim(),
                        skillKey = request.skillKey?.trim()?.takeIf { it.isNotEmpty() },
                        locale = request.locale,
                    )
                }.getOrElse { error ->
                    return@post when (error) {
                        is NoSuchElementException ->
                            call.respondApiError(HttpStatusCode.NotFound, ApiErrorCodes.ITEM_NOT_FOUND)
                        else -> throw error
                    }
                }
                call.respond(response)
            }
        }
    }
}
