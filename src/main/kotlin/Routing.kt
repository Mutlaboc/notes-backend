package com.example.mutlabocnotes

import com.example.mutlabocnotes.api.ApiErrorCodes
import com.example.mutlabocnotes.api.respondApiError
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.event.*

// Настраивает выбранную подсистему приложения.
fun Application.configureRouting() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error", cause)
            call.respondApiError(HttpStatusCode.InternalServerError, ApiErrorCodes.INTERNAL_SERVER_ERROR)
        }
    }
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
    }
}
