package com.example.mutlabocnotes.notes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

fun Route.notesRoutes(
    notesService: NotesService,
    firebaseUserResolver: FirebaseUserResolver
) {
    route("/notes") {

        get {
            val userId = firebaseUserResolver.resolveUserId(call)
            call.respond(HttpStatusCode.OK, notesService.getAll(userId))
        }

        get("/{id}") {
            val userId = firebaseUserResolver.resolveUserId(call)
            val noteId = call.parameters["id"]?.toUuidOrNull()
                ?: throw BadRequestException("Invalid note id")

            val note = notesService.getById(userId, noteId)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "note_not_found")
                )

            call.respond(HttpStatusCode.OK, note)
        }

        post {
            val userId = firebaseUserResolver.resolveUserId(call)
            val request = call.receive<CreateNoteRequestDto>()
            val created = notesService.create(userId, request)
            call.respond(HttpStatusCode.Created, created)
        }

        put("/{id}") {
            val userId = firebaseUserResolver.resolveUserId(call)
            val noteId = call.parameters["id"]?.toUuidOrNull()
                ?: throw BadRequestException("Invalid note id")
            val request = call.receive<UpdateNoteRequestDto>()

            val updated = notesService.update(userId, noteId, request)
                ?: return@put call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "note_not_found")
                )

            call.respond(HttpStatusCode.OK, updated)
        }

        delete("/{id}") {
            val userId = firebaseUserResolver.resolveUserId(call)
            val noteId = call.parameters["id"]?.toUuidOrNull()
                ?: throw BadRequestException("Invalid note id")

            val deleted = notesService.delete(userId, noteId)
            if (!deleted) {
                return@delete call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "note_not_found")
                )
            }

            call.respond(HttpStatusCode.NoContent)
        }

        patch("/{id}/completion") {
            val userId = firebaseUserResolver.resolveUserId(call)
            val noteId = call.parameters["id"]?.toUuidOrNull()
                ?: throw BadRequestException("Invalid note id")
            val request = call.receive<UpdateNoteCompletionRequestDto>()

            val updated = notesService.updateCompletion(userId, noteId, request.isCompleted)
                ?: return@patch call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "note_not_found")
                )

            call.respond(HttpStatusCode.OK, updated)
        }
    }
}

private fun String.toUuidOrNull(): UUID? =
    runCatching { UUID.fromString(this) }.getOrNull()