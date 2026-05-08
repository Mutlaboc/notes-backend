@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

import com.example.mutlabocnotes.api.ApiErrorCodes
import com.example.mutlabocnotes.api.respondApiError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.uuid.Uuid

// Реализует шаг «auth routes» в рамках текущего процесса.
fun Route.authRoutes(
    authService: AuthService,
    socialAuthService: SocialAuthService
) {
    route("/auth") {
        post("/register") {
            try {
                val request = call.receive<RegisterRequestDto>()
                val response = authService.register(request)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: IllegalArgumentException) {
                call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, e.message)
            } catch (e: EmailAlreadyRegisteredException) {
                call.respondApiError(HttpStatusCode.Conflict, ApiErrorCodes.EMAIL_ALREADY_REGISTERED)
            }
        }

        post("/login") {
            try {
                val request = call.receive<LoginRequestDto>()
                val response = authService.login(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: IllegalArgumentException) {
                call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, e.message)
            } catch (e: InvalidCredentialsException) {
                call.respondApiError(HttpStatusCode.Unauthorized, ApiErrorCodes.INVALID_EMAIL_OR_PASSWORD)
            } catch (e: InactiveUserException) {
                call.respondApiError(HttpStatusCode.Forbidden, ApiErrorCodes.USER_INACTIVE)
            }
        }

        post("/refresh") {
            try {
                val request = call.receive<RefreshTokenRequestDto>()
                val response = authService.refresh(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: IllegalArgumentException) {
                call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, e.message)
            } catch (e: UnauthorizedAuthException) {
                call.respondApiError(HttpStatusCode.Unauthorized, ApiErrorCodes.UNAUTHORIZED)
            }
        }

        route("/social") {
            post("/google") {
                try {
                    val request = call.receive<GoogleSocialLoginRequestDto>()
                    val response = socialAuthService.loginWithGoogle(request)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, e.message)
                }
            }

            post("/yandex") {
                try {
                    val request = call.receive<YandexSocialLoginRequestDto>()
                    val response = socialAuthService.loginWithYandex(request)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respondApiError(HttpStatusCode.BadRequest, ApiErrorCodes.INVALID_REQUEST, e.message)
                }
            }
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subjectAsUuid()
                    ?: return@get call.respondApiError(HttpStatusCode.Unauthorized, ApiErrorCodes.UNAUTHORIZED)

                try {
                    val response = authService.me(userId)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: UnauthorizedAuthException) {
                    call.respondApiError(HttpStatusCode.Unauthorized, ApiErrorCodes.UNAUTHORIZED)
                }
            }
        }
    }
}

// Реализует шаг «subject as uuid» в рамках текущего процесса.
private fun JWTPrincipal.subjectAsUuid(): Uuid? =
    runCatching { Uuid.parse(payload.subject) }.getOrNull()
