package com.example.mutlabocnotes.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDto(
    val code: String,
    val message: String? = null
)

object ApiErrorCodes {
    const val UNAUTHORIZED = "unauthorized"
    const val INVALID_REQUEST = "invalid_request"
    const val EMAIL_ALREADY_REGISTERED = "email_already_registered"
    const val INVALID_EMAIL_OR_PASSWORD = "invalid_email_or_password"
    const val USER_INACTIVE = "user_inactive"
    const val NOTE_NOT_FOUND = "note_not_found"
    const val INVALID_NOTE_ID = "invalid_note_id"
    const val CARD_NOT_FOUND = "card_not_found"
    const val INVALID_CARD_ID = "invalid_card_id"
    const val ITEM_NOT_FOUND = "item_not_found"
    const val INTERNAL_SERVER_ERROR = "internal_server_error"
}

suspend fun ApplicationCall.respondApiError(
    status: HttpStatusCode,
    code: String,
    message: String? = null
) {
    respond(
        status = status,
        message = ErrorResponseDto(
            code = code,
            message = message?.trim()?.takeIf { it.isNotEmpty() && it != code }
        )
    )
}
