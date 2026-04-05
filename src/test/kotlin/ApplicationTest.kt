package com.example.mutlabocnotes

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

// Набор тестов для проверки поведения приложения.
class ApplicationTest {

    // Реализует шаг «test root» в рамках текущего процесса.
    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

}
