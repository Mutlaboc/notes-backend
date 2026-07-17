@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.homecards

import com.example.mutlabocnotes.api.ApiErrorCodes
import com.example.mutlabocnotes.api.respondApiError
import com.example.mutlabocnotes.auth.requireCurrentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

// Реализует шаг «home cards routes» в рамках текущего процесса.
fun Route.homeCardsRoutes(
    repository: HomeCardsRepository = HomeCardsRepository(),
) {
    authenticate("auth-jwt") {
        route("/home-cards") {
            get {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@get
                val cards = repository.getAllForUser(userId)
                call.respond(cards)
            }

            get("/{id}") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@get

                val cardId = call.parameters["id"]?.validatedCardIdOrNull()
                    ?: return@get call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_CARD_ID)

                val card = repository.getByIdForUser(userId, cardId)
                if (card == null) {
                    call.respondApiError(HttpStatusCode.NotFound, ApiErrorCodes.CARD_NOT_FOUND)
                    return@get
                }

                call.respond(card)
            }

            post {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@post
                val request = call.receive<HomeCardUpsertRequestDto>()
                if (!request.isValid()) {
                    return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                }
                val created = repository.createForUser(userId, request)
                call.respond(HttpStatusCode.Created, created)
            }

            put("/{id}") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@put

                val cardId = call.parameters["id"]?.validatedCardIdOrNull()
                    ?: return@put call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_CARD_ID)

                val request = call.receive<HomeCardUpsertRequestDto>()
                if (!request.isValid()) {
                    return@put call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                }
                val updated = repository.updateForUser(userId, cardId, request)
                if (updated == null) {
                    call.respondApiError(HttpStatusCode.NotFound, ApiErrorCodes.CARD_NOT_FOUND)
                    return@put
                }

                call.respond(updated)
            }

            delete("/{id}") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@delete

                val cardId = call.parameters["id"]?.validatedCardIdOrNull()
                    ?: return@delete call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_CARD_ID)

                val deleted = repository.deleteForUser(userId, cardId)
                if (!deleted) {
                    call.respondApiError(HttpStatusCode.NotFound, ApiErrorCodes.CARD_NOT_FOUND)
                    return@delete
                }

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun String.validatedCardIdOrNull(): String? =
    trim().takeIf { it.isNotEmpty() }?.takeIf { id ->
        runCatching { UUID.fromString(id) }.isSuccess
    }

private val validHomeCardSections = setOf(
    "METERS",
    "APPLIANCES",
    "LIGHTING",
    "DOCUMENTS",
    "CONTACTS",
    "OTHER"
)

private fun HomeCardUpsertRequestDto.isValid(): Boolean =
    section in validHomeCardSections && links.none { it.isBlank() } &&
        (clientMutationId == null || runCatching { UUID.fromString(clientMutationId) }.isSuccess)
