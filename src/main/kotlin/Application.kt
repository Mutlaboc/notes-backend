package com.example.mutlabocnotes

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val appLog = environment.log

    install(CallLogging)

    install(ContentNegotiation) {
        json()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            appLog.error("Unhandled error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "internal_server_error")
            )
        }
    }

    routing {
        get("/") {
            call.respond(
                HttpStatusCode.OK,
                mapOf("service" to "notes-backend", "status" to "running")
            )
        }

        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                mapOf("status" to "ok")
            )
        }
    }
}