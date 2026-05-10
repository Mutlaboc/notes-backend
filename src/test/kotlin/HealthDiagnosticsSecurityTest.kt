@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes

import com.example.mutlabocnotes.api.ApiErrorCodes
import com.example.mutlabocnotes.auth.AuthUserModel
import com.example.mutlabocnotes.auth.JwtConfig
import com.example.mutlabocnotes.auth.JwtTokenService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AssumptionViolatedException
import org.junit.BeforeClass
import org.testcontainers.containers.PostgreSQLContainer

class HealthDiagnosticsSecurityTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun healthRemainsPublic() = testApplication {
        environment {
            config = testApplicationConfig()
        }
        application {
            module()
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", json.parseToJsonElement(response.bodyAsText()).jsonObject["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun dbHealthRejectsAnonymousRequests() = testApplication {
        environment {
            config = testApplicationConfig()
        }
        application {
            module()
        }

        val response = client.get("/health/db")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(
            ApiErrorCodes.UNAUTHORIZED,
            json.parseToJsonElement(response.bodyAsText()).jsonObject["code"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun dbHealthReturnsSanitizedPayloadForAuthenticatedRequests() = testApplication {
        environment {
            config = testApplicationConfig()
        }
        application {
            module()
        }

        val response = client.get("/health/db") {
            header(HttpHeaders.Authorization, "Bearer ${testAccessToken()}")
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", json.parseToJsonElement(body).jsonObject["status"]?.jsonPrimitive?.content)
        assertEquals("reachable", json.parseToJsonElement(body).jsonObject["database"]?.jsonPrimitive?.content)
        assertFalse(body.contains("PostgreSQL", ignoreCase = true))
        assertFalse(body.contains("version", ignoreCase = true))
        assertFalse(body.contains(postgres.databaseName, ignoreCase = true))
    }

    private fun testAccessToken(): String =
        JwtTokenService(testJwtConfig()).generateAccessToken(
            AuthUserModel(
                id = Uuid.random(),
                email = "health-${UUID.randomUUID()}@example.com",
                passwordHash = null,
                firebaseUid = null,
                displayName = null,
                isActive = true
            )
        )

    private fun testJwtConfig(): JwtConfig =
        JwtConfig(
            issuer = "test-issuer",
            audience = "test-audience",
            realm = "test-realm",
            secret = "test-secret-that-is-long-enough-for-hmac",
            accessTokenTtlSeconds = 1800,
            refreshTokenTtlSeconds = 1209600
        )

    companion object {
        private val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @BeforeClass
        fun startDatabase() {
            try {
                postgres.start()
            } catch (e: IllegalStateException) {
                throw AssumptionViolatedException("Docker is required for PostgreSQL integration tests", e)
            }
        }

        private fun testApplicationConfig(): MapApplicationConfig =
            MapApplicationConfig(
                "db.driverClassName" to postgres.driverClassName,
                "db.jdbcUrl" to postgres.jdbcUrl,
                "db.username" to postgres.username,
                "db.password" to postgres.password,
                "db.maximumPoolSize" to "3",
                "db.minimumIdle" to "1",
                "db.connectionTimeoutMs" to "10000",
                "db.idleTimeoutMs" to "600000",
                "db.maxLifetimeMs" to "1800000",
                "db.autoCommit" to "false",
                "flyway.enabled" to "true",
                "flyway.locations" to "classpath:db/migration",
                "flyway.validateMigrationNaming" to "true",
                "jwt.issuer" to "test-issuer",
                "jwt.audience" to "test-audience",
                "jwt.realm" to "test-realm",
                "jwt.secret" to "test-secret-that-is-long-enough-for-hmac",
                "jwt.accessTokenTtlSeconds" to "1800",
                "jwt.refreshTokenTtlSeconds" to "1209600"
            )
    }
}
