@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.homecards

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

            get("{id}") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@get

                val cardId = call.parameters["id"]
                if (cardId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Missing card id")
                    return@get
                }

                val card = repository.getByIdForUser(userId, cardId)
                if (card == null) {
                    call.respond(HttpStatusCode.NotFound, "Card not found")
                    return@get
                }

                call.respond(card)
            }

            post {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@post
                val request = call.receive<HomeCardUpsertRequestDto>()
                val created = repository.createForUser(userId, request)
                call.respond(HttpStatusCode.Created, created)
            }

            put("{id}") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@put

                val cardId = call.parameters["id"]
                if (cardId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Missing card id")
                    return@put
                }

                val request = call.receive<HomeCardUpsertRequestDto>()
                val updated = repository.updateForUser(userId, cardId, request)
                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, "Card not found")
                    return@put
                }

                call.respond(updated)
            }

            delete("{id}") {
                val userId = call.requireCurrentUserId()?.let { UUID.fromString(it.toString()) } ?: return@delete

                val cardId = call.parameters["id"]
                if (cardId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Missing card id")
                    return@delete
                }

                val deleted = repository.deleteForUser(userId, cardId)
                if (!deleted) {
                    call.respond(HttpStatusCode.NotFound, "Card not found")
                    return@delete
                }

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
