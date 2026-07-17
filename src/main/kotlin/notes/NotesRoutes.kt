@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.notes

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
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlin.uuid.Uuid

// Реализует шаг «notes routes» в рамках текущего процесса.
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
                    ?: return@get call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_NOTE_ID)

                val note = notesService.getById(userId, noteId)
                    ?: return@get call.respondApiError(HttpStatusCode.NotFound, ApiErrorCodes.NOTE_NOT_FOUND)

                call.respond(HttpStatusCode.OK, note)
            }

            post {
                val userId = call.requireCurrentUserId() ?: return@post
                val request = call.receive<CreateNoteRequestDto>()
                if (request.coinCount < 0 || !request.hasValidSchedule() ||
                    request.clientMutationId?.let { runCatching { Uuid.parse(it) }.isFailure } == true) {
                    return@post call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                }
                val created = notesService.create(userId, request)
                call.respond(HttpStatusCode.Created, created)
            }

            put("/{id}") {
                val userId = call.requireCurrentUserId() ?: return@put
                val noteId = call.parameters["id"]?.toUuidOrNull()
                    ?: return@put call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_NOTE_ID)
                val request = call.receive<UpdateNoteRequestDto>()
                if (request.coinCount < 0 || !request.hasValidSchedule()) {
                    return@put call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST)
                }

                val updated = notesService.update(userId, noteId, request)
                    ?: return@put call.respondApiError(HttpStatusCode.NotFound, ApiErrorCodes.NOTE_NOT_FOUND)

                call.respond(HttpStatusCode.OK, updated)
            }

            delete("/{id}") {
                val userId = call.requireCurrentUserId() ?: return@delete
                val noteId = call.parameters["id"]?.toUuidOrNull()
                    ?: return@delete call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_NOTE_ID)

                val deleted = notesService.delete(userId, noteId)
                if (!deleted) {
                    return@delete call.respondApiError(HttpStatusCode.NotFound, ApiErrorCodes.NOTE_NOT_FOUND)
                }

                call.respond(HttpStatusCode.NoContent)
            }

            patch("/{id}/completion") {
                val userId = call.requireCurrentUserId() ?: return@patch
                val noteId = call.parameters["id"]?.toUuidOrNull()
                    ?: return@patch call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_NOTE_ID)
                val request = call.receive<UpdateNoteCompletionRequestDto>()

                val updated = notesService.updateCompletion(userId, noteId, request.isCompleted)
                    ?: return@patch call.respondApiError(HttpStatusCode.NotFound, ApiErrorCodes.NOTE_NOT_FOUND)

                call.respond(HttpStatusCode.OK, updated)
            }
        }
    }
}

// Преобразует данные в нужный формат представления.
private fun String.toUuidOrNull(): Uuid? =
    runCatching { Uuid.parse(this) }.getOrNull()

private fun CreateNoteRequestDto.hasValidSchedule(): Boolean = when (category) {
    NoteCategory.RECURRING_TASKS -> startAtMillis != null && durationMinutes != null &&
        durationMinutes > 0 && repeatRule != RepeatRule.NONE && deadlineMillis == null
    NoteCategory.TASKS -> startAtMillis == null && durationMinutes == null && repeatRule == RepeatRule.NONE
    NoteCategory.SHOPPING -> deadlineMillis == null && startAtMillis == null &&
        durationMinutes == null && repeatRule == RepeatRule.NONE
}

private fun UpdateNoteRequestDto.hasValidSchedule(): Boolean = when (category) {
    NoteCategory.RECURRING_TASKS -> startAtMillis != null && durationMinutes != null &&
        durationMinutes > 0 && repeatRule != RepeatRule.NONE && deadlineMillis == null
    NoteCategory.TASKS -> startAtMillis == null && durationMinutes == null && repeatRule == RepeatRule.NONE
    NoteCategory.SHOPPING -> deadlineMillis == null && startAtMillis == null &&
        durationMinutes == null && repeatRule == RepeatRule.NONE
}
