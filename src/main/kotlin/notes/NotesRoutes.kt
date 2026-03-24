@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.notes

import com.example.mutlabocnotes.auth.requireCurrentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
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
import kotlin.uuid.Uuid

fun Route.notesRoutes(
    notesService: NotesService,
) {
    authenticate("auth-jwt") {
        route("/notes") {

            get {
                val userId = call.requireCurrentUserId() ?: return@get
                call.respond(HttpStatusCode.OK, notesService.getAll(userId))
            }

            get("/{id}") {
                val userId = call.requireCurrentUserId() ?: return@get
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
                val userId = call.requireCurrentUserId() ?: return@post
                val request = call.receive<CreateNoteRequestDto>()
                val created = notesService.create(userId, request)
                call.respond(HttpStatusCode.Created, created)
            }

            put("/{id}") {
                val userId = call.requireCurrentUserId() ?: return@put
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
                val userId = call.requireCurrentUserId() ?: return@delete
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
                val userId = call.requireCurrentUserId() ?: return@patch
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
}

private fun String.toUuidOrNull(): Uuid? =
    runCatching { Uuid.parse(this) }.getOrNull()
