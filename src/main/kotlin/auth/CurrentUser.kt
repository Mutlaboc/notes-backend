@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import kotlin.uuid.Uuid

// Реализует шаг «require current user id» в рамках текущего процесса.
suspend fun ApplicationCall.requireCurrentUserId(): Uuid? {
    val principal = principal<JWTPrincipal>()
    val subject = principal?.payload?.subject
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: run {
            respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
            return null
        }

    return runCatching { Uuid.parse(subject) }.getOrElse {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
        null
    }
}
