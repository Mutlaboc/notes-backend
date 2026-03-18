package com.example.mutlabocnotes

import com.example.mutlabocnotes.database.DatabaseFactory
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

    // Инициализируем БД при старте приложения.
    // Если подключение не работает, backend должен упасть сразу,
    // а не в момент первого реального запроса.
    DatabaseFactory.init(environment.config)

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
                mapOf(
                    "service" to "notes-backend",
                    "status" to "running"
                )
            )
        }

        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                mapOf("status" to "ok")
            )
        }

        // Временный endpoint для проверки подключения к PostgreSQL
        get("/health/db") {
            val dbInfo = DatabaseFactory.testConnection()

            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "status" to "ok",
                    "database" to dbInfo
                )
            )
        }
    }
}