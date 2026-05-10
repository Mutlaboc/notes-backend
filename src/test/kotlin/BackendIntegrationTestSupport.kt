package com.example.mutlabocnotes

import com.example.mutlabocnotes.auth.AuthResponseDto
import com.example.mutlabocnotes.database.DatabaseFactory
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.AssumptionViolatedException
import org.junit.BeforeClass
import org.testcontainers.containers.PostgreSQLContainer

abstract class BackendIntegrationTestSupport {

    protected val json = Json { ignoreUnknownKeys = true }
    protected val defaultPassword: String
        get() = DEFAULT_PASSWORD

    @BeforeTest
    fun resetDatabase() {
        transaction {
            exec(
                """
                TRUNCATE TABLE
                    user_identities,
                    refresh_tokens,
                    note_checklist_items,
                    notes,
                    home_card_fields,
                    home_card_links,
                    home_cards,
                    users
                RESTART IDENTITY CASCADE;
                """.trimIndent()
            )
        }
    }

    protected fun ApplicationTestBuilder.startBackend() {
        environment {
            config = testApplicationConfig()
        }
        application {
            module()
        }
    }

    protected fun HttpRequestBuilder.jsonBody(body: String) {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(body)
    }

    protected suspend fun ApplicationTestBuilder.registerUser(
        email: String = "user-${UUID.randomUUID()}@example.com",
        password: String = DEFAULT_PASSWORD,
        displayName: String? = null
    ): AuthResponseDto {
        val displayNameJson = displayName?.let { ""","displayName":"$it"""" }.orEmpty()
        val response = client.post("/auth/register") {
            jsonBody("""{"email":"$email","password":"$password"$displayNameJson}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        return json.decodeFromString<AuthResponseDto>(response.bodyAsText())
    }

    protected suspend fun HttpResponse.jsonObject(): JsonObject =
        json.parseToJsonElement(bodyAsText()).jsonObject

    protected suspend fun assertApiError(response: HttpResponse, expectedCode: String) {
        val body = response.jsonObject()
        assertEquals(expectedCode, body["code"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("error"))
    }

    companion object {
        const val DEFAULT_PASSWORD = "12345678"

        private val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @BeforeClass
        fun initializeDatabase() {
            try {
                postgres.start()
            } catch (e: IllegalStateException) {
                throw AssumptionViolatedException("Docker is required for PostgreSQL integration tests", e)
            }

            val config = testApplicationConfig()
            FlywayRunner.migrate(config)
            DatabaseFactory.init(config)
        }

        fun testApplicationConfig(): MapApplicationConfig =
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
