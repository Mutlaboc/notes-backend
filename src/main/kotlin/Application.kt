package com.example.mutlabocnotes

import com.example.mutlabocnotes.auth.AuthRepository
import com.example.mutlabocnotes.auth.AuthService
import com.example.mutlabocnotes.auth.BcryptPasswordHasher
import com.example.mutlabocnotes.auth.DatabaseSocialUserResolver
import com.example.mutlabocnotes.api.ApiErrorCodes
import com.example.mutlabocnotes.api.respondApiError
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
import com.example.mutlabocnotes.achievements.achievementsRoutes
import com.example.mutlabocnotes.character.characterRoutes
import com.example.mutlabocnotes.database.DatabaseFactory
import com.example.mutlabocnotes.events.eventsRoutes
import com.example.mutlabocnotes.homecards.homeCardsRoutes
import com.example.mutlabocnotes.inventory.inventoryRoutes
import com.example.mutlabocnotes.notes.NotesRepository
import com.example.mutlabocnotes.notes.NotesService
import com.example.mutlabocnotes.notes.notesRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

// Запускает серверное приложение.
fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

// Собирает инфраструктуру приложения: плагины, базу данных и роуты.
fun Application.module() {
    // Сначала применяем миграции, затем инициализируем подключение к БД.
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

    // Явно собираем зависимости авторизации, чтобы упростить поддержку и дебаг.
    val authService = AuthService(
        authRepository = authRepository,
        refreshTokenRepository = refreshTokenRepository,
        passwordHasher = passwordHasher,
        jwtTokenService = jwtTokenService,
        refreshTokenService = refreshTokenService
    )

    // Подключаем социальную аутентификацию с резолвером связки identity -> user.
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
        json(ApiJson)
    }

    configureJwtAuthentication(
        jwtConfig = jwtConfig,
        jwtTokenService = jwtTokenService
    )

    // Централизованная обработка ошибок для единообразных ответов API.
    configureApiErrorHandling()

    routing {
        // Технические эндпоинты для проверки доступности сервиса и базы.
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

        authenticate("auth-jwt") {
            get("/health/db") {
                DatabaseFactory.testConnection()

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "status" to "ok",
                        "database" to "reachable"
                    )
                )
            }
        }

        authRoutes(
            authService = authService,
            socialAuthService = socialAuthService
        )

        notesRoutes(
            notesService = notesService,
        )

        homeCardsRoutes()

        characterRoutes()

        inventoryRoutes()

        eventsRoutes()

        achievementsRoutes()
    }
}

// Centralized API error handling keeps route failures machine-readable.
fun Application.configureApiErrorHandling() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.BadRequest,
                code = ApiErrorCodes.INVALID_REQUEST,
                message = cause.message
            )
        }

        exception<SocialTokenValidationException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.Unauthorized,
                code = cause.message ?: ApiErrorCodes.UNAUTHORIZED
            )
        }

        exception<SocialIdentityResolutionException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.BadRequest,
                code = cause.message ?: ApiErrorCodes.INVALID_REQUEST
            )
        }

        exception<SocialAuthNotReadyException> { call, cause ->
            call.respondApiError(
                status = HttpStatusCode.NotImplemented,
                code = cause.message ?: ApiErrorCodes.INTERNAL_SERVER_ERROR
            )
        }

        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error", cause)
            call.respondApiError(
                status = HttpStatusCode.InternalServerError,
                code = ApiErrorCodes.INTERNAL_SERVER_ERROR
            )
        }
    }
}
