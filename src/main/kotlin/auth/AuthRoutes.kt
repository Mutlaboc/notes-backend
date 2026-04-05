@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

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
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid_request")))
            } catch (e: EmailAlreadyRegisteredException) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "email_already_registered"))
            }
        }

        post("/login") {
            try {
                val request = call.receive<LoginRequestDto>()
                val response = authService.login(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid_request")))
            } catch (e: InvalidCredentialsException) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "invalid_email_or_password"))
            } catch (e: InactiveUserException) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "user_inactive"))
            }
        }

        post("/refresh") {
            try {
                val request = call.receive<RefreshTokenRequestDto>()
                val response = authService.refresh(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid_request")))
            } catch (e: UnauthorizedAuthException) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
            }
        }

        route("/social") {
            post("/google") {
                try {
                    val request = call.receive<GoogleSocialLoginRequestDto>()
                    val response = socialAuthService.loginWithGoogle(request)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponseDto(e.message ?: "invalid_request")
                    )
                }
            }

            post("/yandex") {
                try {
                    val request = call.receive<YandexSocialLoginRequestDto>()
                    val response = socialAuthService.loginWithYandex(request)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponseDto(e.message ?: "invalid_request")
                    )
                }
            }
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.subjectAsUuid()
                    ?: return@get call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "unauthorized")
                    )

                try {
                    val response = authService.me(userId)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: UnauthorizedAuthException) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
                }
            }
        }
    }
}

// Реализует шаг «subject as uuid» в рамках текущего процесса.
private fun JWTPrincipal.subjectAsUuid(): Uuid? =
    runCatching { Uuid.parse(payload.subject) }.getOrNull()