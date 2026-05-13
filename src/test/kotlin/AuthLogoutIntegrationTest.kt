package com.example.mutlabocnotes

import com.example.mutlabocnotes.api.ApiErrorCodes
import com.example.mutlabocnotes.auth.AuthRepository
import com.example.mutlabocnotes.auth.AuthService
import com.example.mutlabocnotes.auth.BcryptPasswordHasher
import com.example.mutlabocnotes.auth.DatabaseSocialUserResolver
import com.example.mutlabocnotes.auth.JwtConfig
import com.example.mutlabocnotes.auth.JwtTokenService
import com.example.mutlabocnotes.auth.NotReadyGoogleTokenVerifier
import com.example.mutlabocnotes.auth.NotReadyYandexTokenVerifier
import com.example.mutlabocnotes.auth.RefreshTokenRepository
import com.example.mutlabocnotes.auth.RefreshTokenService
import com.example.mutlabocnotes.auth.SocialAuthConfig
import com.example.mutlabocnotes.auth.SocialAuthService
import com.example.mutlabocnotes.auth.UserIdentityRepository
import com.example.mutlabocnotes.auth.authRoutes
import com.example.mutlabocnotes.auth.configureJwtAuthentication
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.BeforeClass

class AuthLogoutIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun logoutRevokesCurrentRefreshToken() = testApplication {
        application {
            installAuthRoutes()
        }

        val refreshToken = registerAndExtractRefreshToken()

        val logoutResponse = client.post("/auth/logout") {
            jsonBody("""{"refreshToken":"$refreshToken"}""")
        }

        assertEquals(HttpStatusCode.NoContent, logoutResponse.status)

        val refreshResponse = client.post("/auth/refresh") {
            jsonBody("""{"refreshToken":"$refreshToken"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, refreshResponse.status)
        assertApiError(refreshResponse, ApiErrorCodes.UNAUTHORIZED)
    }

    @Test
    fun logoutRejectsBlankRefreshToken() = testApplication {
        application {
            installAuthRoutes()
        }

        val response = client.post("/auth/logout") {
            jsonBody("""{"refreshToken":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertApiError(response, ApiErrorCodes.INVALID_REQUEST)
    }

    @Test
    fun repeatedLogoutRemainsNoContent() = testApplication {
        application {
            installAuthRoutes()
        }

        val refreshToken = registerAndExtractRefreshToken()

        repeat(2) {
            val response = client.post("/auth/logout") {
                jsonBody("""{"refreshToken":"$refreshToken"}""")
            }

            assertEquals(HttpStatusCode.NoContent, response.status)
        }
    }

    private suspend fun ApplicationTestBuilder.registerAndExtractRefreshToken(): String {
        val email = "logout-${UUID.randomUUID()}@example.com"
        val response = client.post("/auth/register") {
            jsonBody("""{"email":"$email","password":"12345678"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        return json.parseToJsonElement(response.bodyAsText())
            .jsonObject["refreshToken"]
            ?.jsonPrimitive
            ?.content
            ?: error("refreshToken missing from auth response")
    }

    private fun HttpRequestBuilder.jsonBody(body: String) {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(body)
    }

    private suspend fun assertApiError(response: HttpResponse, expectedCode: String) {
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(expectedCode, body["code"]?.jsonPrimitive?.content)
    }

    private fun Application.installAuthRoutes() {
        install(ContentNegotiation) {
            json(ApiJson)
        }
        configureApiErrorHandling()

        val jwtConfig = testJwtConfig()
        configureJwtAuthentication(
            jwtConfig = jwtConfig,
            jwtTokenService = JwtTokenService(jwtConfig)
        )

        val authRepository = AuthRepository()
        val refreshTokenRepository = RefreshTokenRepository()
        val userIdentityRepository = UserIdentityRepository()
        val passwordHasher = BcryptPasswordHasher(cost = 4)
        val jwtTokenService = JwtTokenService(jwtConfig)
        val refreshTokenService = RefreshTokenService(jwtConfig)
        val authService = AuthService(
            authRepository = authRepository,
            refreshTokenRepository = refreshTokenRepository,
            passwordHasher = passwordHasher,
            jwtTokenService = jwtTokenService,
            refreshTokenService = refreshTokenService
        )
        val socialConfig = SocialAuthConfig(
            googleWebClientId = null,
            yandexClientId = null,
            yandexClientSecret = null
        )

        routing {
            authRoutes(
                authService = authService,
                socialAuthService = SocialAuthService(
                    authService = authService,
                    googleTokenVerifier = NotReadyGoogleTokenVerifier(socialConfig),
                    yandexTokenVerifier = NotReadyYandexTokenVerifier(socialConfig),
                    socialUserResolver = DatabaseSocialUserResolver(
                        authRepository = authRepository,
                        userIdentityRepository = userIdentityRepository
                    )
                )
            )
        }
    }

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
        @JvmStatic
        @BeforeClass
        fun initializeDatabase() {
            BackendTestDatabase.initialize(includeJwt = false)
        }
    }
}
