package com.example.mutlabocnotes.homecards

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.homeCardsRoutes(
    repository: HomeCardsRepository = HomeCardsRepository(),
) {
    route("/home-cards") {
        get {
            val firebaseUid = call.request.headers["X-Firebase-Uid"]
            if (firebaseUid.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, "Missing X-Firebase-Uid header")
                return@get
            }

            val cards = repository.getAllForFirebaseUid(firebaseUid)
            call.respond(cards)
        }

        get("{id}") {
            val firebaseUid = call.request.headers["X-Firebase-Uid"]
            if (firebaseUid.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, "Missing X-Firebase-Uid header")
                return@get
            }

            val cardId = call.parameters["id"]
            if (cardId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing card id")
                return@get
            }

            val card = repository.getByIdForFirebaseUid(firebaseUid, cardId)
            if (card == null) {
                call.respond(HttpStatusCode.NotFound, "Card not found")
                return@get
            }

            call.respond(card)
        }

        post {
            val firebaseUid = call.request.headers["X-Firebase-Uid"]
            if (firebaseUid.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, "Missing X-Firebase-Uid header")
                return@post
            }

            val request = call.receive<HomeCardUpsertRequestDto>()
            val created = repository.createForFirebaseUid(firebaseUid, request)
            call.respond(HttpStatusCode.Created, created)
        }

        put("{id}") {
            val firebaseUid = call.request.headers["X-Firebase-Uid"]
            if (firebaseUid.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, "Missing X-Firebase-Uid header")
                return@put
            }

            val cardId = call.parameters["id"]
            if (cardId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing card id")
                return@put
            }

            val request = call.receive<HomeCardUpsertRequestDto>()
            val updated = repository.updateForFirebaseUid(firebaseUid, cardId, request)
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound, "Card not found")
                return@put
            }

            call.respond(updated)
        }

        delete("{id}") {
            val firebaseUid = call.request.headers["X-Firebase-Uid"]
            if (firebaseUid.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, "Missing X-Firebase-Uid header")
                return@delete
            }

            val cardId = call.parameters["id"]
            if (cardId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing card id")
                return@delete
            }

            val deleted = repository.deleteForFirebaseUid(firebaseUid, cardId)
            if (!deleted) {
                call.respond(HttpStatusCode.NotFound, "Card not found")
                return@delete
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
