package com.example.mutlabocnotes

import com.example.mutlabocnotes.auth.AuthRepository
import com.example.mutlabocnotes.auth.AuthService
import com.example.mutlabocnotes.auth.BcryptPasswordHasher
import com.example.mutlabocnotes.auth.DatabaseSocialUserResolver
import com.example.mutlabocnotes.auth.ErrorResponseDto
import com.example.mutlabocnotes.auth.GoogleTokenVerifierImpl
import com.example.mutlabocnotes.auth.JwtTokenService
import com.example.mutlabocnotes.auth.NotReadyYandexTokenVerifier
import com.example.mutlabocnotes.auth.RefreshTokenRepository
import com.example.mutlabocnotes.auth.RefreshTokenService
import com.example.mutlabocnotes.auth.SocialAuthNotReadyException
import com.example.mutlabocnotes.auth.SocialAuthService
import com.example.mutlabocnotes.auth.SocialIdentityResolutionException
import com.example.mutlabocnotes.auth.SocialTokenValidationException
import com.example.mutlabocnotes.auth.UserIdentityRepository
import com.example.mutlabocnotes.auth.authRoutes
import com.example.mutlabocnotes.auth.configureJwtAuthentication
import com.example.mutlabocnotes.auth.readJwtConfig
import com.example.mutlabocnotes.auth.readSocialAuthConfig
import com.example.mutlabocnotes.database.DatabaseFactory
import com.example.mutlabocnotes.homecards.homeCardsRoutes
import com.example.mutlabocnotes.notes.NotesRepository
import com.example.mutlabocnotes.notes.NotesService
import com.example.mutlabocnotes.notes.notesRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val appLog = environment.log

    FlywayRunner.migrate(environment.config)
    DatabaseFactory.init(environment.config)

    val notesRepository = NotesRepository()
    val notesService = NotesService(notesRepository)

    val jwtConfig = environment.config.readJwtConfig()
    val socialAuthConfig = environment.config.readSocialAuthConfig()

    val authRepository = AuthRepository()
    val refreshTokenRepository = RefreshTokenRepository()
    val userIdentityRepository = UserIdentityRepository()
    val passwordHasher = BcryptPasswordHasher(cost = 12)
    val jwtTokenService = JwtTokenService(jwtConfig)
    val refreshTokenService = RefreshTokenService(jwtConfig)

    val authService = AuthService(
        authRepository = authRepository,
        refreshTokenRepository = refreshTokenRepository,
        passwordHasher = passwordHasher,
        jwtTokenService = jwtTokenService,
        refreshTokenService = refreshTokenService
    )

    val socialAuthService = SocialAuthService(
        authService = authService,
        googleTokenVerifier = GoogleTokenVerifierImpl(socialAuthConfig),
        yandexTokenVerifier = NotReadyYandexTokenVerifier(socialAuthConfig),
        socialUserResolver = DatabaseSocialUserResolver(
            authRepository = authRepository,
            userIdentityRepository = userIdentityRepository
        )
    )

    install(CallLogging)

    install(ContentNegotiation) {
        json()
    }

    configureJwtAuthentication(
        jwtConfig = jwtConfig,
        jwtTokenService = jwtTokenService
    )

    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (cause.message ?: "bad_request"))
            )
        }

        exception<SocialTokenValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponseDto(cause.message ?: "invalid_social_token")
            )
        }

        exception<SocialIdentityResolutionException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(cause.message ?: "social_identity_resolution_failed")
            )
        }

        exception<SocialAuthNotReadyException> { call, cause ->
            call.respond(
                HttpStatusCode.NotImplemented,
                ErrorResponseDto(cause.message ?: "social_auth_not_ready")
            )
        }

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

        authRoutes(
            authService = authService,
            socialAuthService = socialAuthService
        )

        notesRoutes(
            notesService = notesService,
        )

        homeCardsRoutes()
    }
}
