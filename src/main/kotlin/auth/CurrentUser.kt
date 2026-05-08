@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

import com.example.mutlabocnotes.api.ApiErrorCodes
import com.example.mutlabocnotes.api.respondApiError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import kotlin.uuid.Uuid

// Реализует шаг «require current user id» в рамках текущего процесса.
suspend fun ApplicationCall.requireCurrentUserId(): Uuid? {
    val principal = principal<JWTPrincipal>()
    val subject = principal?.payload?.subject
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: run {
            respondApiError(HttpStatusCode.Unauthorized, ApiErrorCodes.UNAUTHORIZED)
            return null
        }

    return runCatching { Uuid.parse(subject) }.getOrElse {
        respondApiError(HttpStatusCode.Unauthorized, ApiErrorCodes.UNAUTHORIZED)
        null
    }
}
