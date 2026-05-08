package com.example.mutlabocnotes

import com.example.mutlabocnotes.api.ApiErrorCodes
import com.example.mutlabocnotes.auth.AuthRepository
import com.example.mutlabocnotes.auth.AuthService
import com.example.mutlabocnotes.auth.AuthUserModel
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
import com.example.mutlabocnotes.homecards.HomeCardDto
import com.example.mutlabocnotes.homecards.HomeCardUpsertRequestDto
import com.example.mutlabocnotes.homecards.HomeCardsRepository
import com.example.mutlabocnotes.homecards.homeCardsRoutes
import com.example.mutlabocnotes.notes.CreateNoteRequestDto
import com.example.mutlabocnotes.notes.NoteResponseDto
import com.example.mutlabocnotes.notes.NotesRepository
import com.example.mutlabocnotes.notes.NotesService
import com.example.mutlabocnotes.notes.UpdateNoteCompletionRequestDto
import com.example.mutlabocnotes.notes.UpdateNoteRequestDto
import com.example.mutlabocnotes.notes.notesRoutes
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalUuidApi::class)
class ApiErrorContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun authValidationErrorReturnsUnifiedSchema() = testApplication {
        val jwtConfig = testJwtConfig()

        application {
            installContractRoutes(jwtConfig)
        }

        val response = client.post("/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("{}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertApiError(response, ApiErrorCodes.INVALID_REQUEST)
    }

    @Test
    fun missingJwtReturnsUnifiedUnauthorizedSchema() = testApplication {
        val jwtConfig = testJwtConfig()

        application {
            installContractRoutes(jwtConfig)
        }

        val response = client.get("/notes/not-a-uuid")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertApiError(response, ApiErrorCodes.UNAUTHORIZED)
    }

    @Test
    fun invalidNoteIdReturnsUnifiedSchema() = testApplication {
        val jwtConfig = testJwtConfig()

        application {
            installContractRoutes(jwtConfig)
        }

        val response = client.get("/notes/not-a-uuid") {
            bearerAuth(testToken(jwtConfig))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertApiError(response, ApiErrorCodes.INVALID_NOTE_ID)
    }

    @Test
    fun missingNoteReturnsUnifiedSchema() = testApplication {
        val jwtConfig = testJwtConfig()

        application {
            installContractRoutes(jwtConfig)
        }

        val response = client.get("/notes/00000000-0000-0000-0000-000000000001") {
            bearerAuth(testToken(jwtConfig))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertApiError(response, ApiErrorCodes.NOTE_NOT_FOUND)
    }

    @Test
    fun invalidHomeCardIdReturnsUnifiedSchema() = testApplication {
        val jwtConfig = testJwtConfig()

        application {
            installContractRoutes(jwtConfig)
        }

        val response = client.get("/home-cards/not-a-uuid") {
            bearerAuth(testToken(jwtConfig))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertApiError(response, ApiErrorCodes.INVALID_CARD_ID)
    }

    @Test
    fun missingHomeCardReturnsUnifiedSchema() = testApplication {
        val jwtConfig = testJwtConfig()

        application {
            installContractRoutes(jwtConfig)
        }

        val response = client.delete("/home-cards/00000000-0000-0000-0000-000000000001") {
            bearerAuth(testToken(jwtConfig))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertApiError(response, ApiErrorCodes.CARD_NOT_FOUND)
    }

    private fun io.ktor.server.application.Application.installContractRoutes(jwtConfig: JwtConfig) {
        install(ContentNegotiation) {
            json()
        }
        configureApiErrorHandling()
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
            notesRoutes(NotFoundNotesService())
            homeCardsRoutes(NotFoundHomeCardsRepository())
        }
    }

    private suspend fun assertApiError(response: HttpResponse, expectedCode: String) {
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(expectedCode, body["code"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("error"))
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

    private fun testToken(jwtConfig: JwtConfig): String =
        JwtTokenService(jwtConfig).generateAccessToken(
            AuthUserModel(
                id = Uuid.parse("00000000-0000-0000-0000-000000000100"),
                email = "tester@example.com",
                passwordHash = null,
                firebaseUid = null,
                displayName = null,
                isActive = true
            )
        )

    private class NotFoundNotesService : NotesService(NotesRepository()) {
        override suspend fun getById(userId: Uuid, noteId: Uuid): NoteResponseDto? = null

        override suspend fun update(
            userId: Uuid,
            noteId: Uuid,
            request: UpdateNoteRequestDto
        ): NoteResponseDto? = null

        override suspend fun delete(userId: Uuid, noteId: Uuid): Boolean = false

        override suspend fun updateCompletion(
            userId: Uuid,
            noteId: Uuid,
            isCompleted: Boolean
        ): NoteResponseDto? = null

        override suspend fun getAll(userId: Uuid): List<NoteResponseDto> = emptyList()

        override suspend fun create(userId: Uuid, request: CreateNoteRequestDto): NoteResponseDto {
            error("Not used in error contract tests")
        }
    }

    private class NotFoundHomeCardsRepository : HomeCardsRepository() {
        override fun getByIdForUser(userId: java.util.UUID, cardId: String): HomeCardDto? = null

        override fun updateForUser(
            userId: java.util.UUID,
            cardId: String,
            request: HomeCardUpsertRequestDto
        ): HomeCardDto? = null

        override fun deleteForUser(userId: java.util.UUID, cardId: String): Boolean = false

        override fun getAllForUser(userId: java.util.UUID): List<HomeCardDto> = emptyList()

        override fun createForUser(
            userId: java.util.UUID,
            request: HomeCardUpsertRequestDto
        ): HomeCardDto {
            error("Not used in error contract tests")
        }
    }
}
