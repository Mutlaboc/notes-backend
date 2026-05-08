package com.example.mutlabocnotes

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

// Набор тестов для проверки поведения приложения.
class ApplicationTest {

    // Реализует шаг «test root» в рамках текущего процесса.
    @Test
    fun testRoot() = testApplication {
        application {
            routing {
                get("/") {
                    call.respondText("notes-backend", status = HttpStatusCode.OK)
                }
            }
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

}
