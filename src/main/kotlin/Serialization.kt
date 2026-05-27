package com.example.mutlabocnotes

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.*

val ApiJson = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
}

// Настраивает выбранную подсистему приложения.
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(ApiJson)
    }
    routing {
        get("/json/kotlinx-serialization") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}
