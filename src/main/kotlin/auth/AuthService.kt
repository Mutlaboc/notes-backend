@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

import kotlin.uuid.Uuid

class AuthService(
    private val authRepository: AuthRepository,
    private val passwordHasher: PasswordHasher,
    private val jwtTokenService: JwtTokenService
) {

    suspend fun register(request: RegisterRequestDto): AuthResponseDto {
        val email = normalizeEmail(request.email)
        validatePassword(request.password)
        val displayName = normalizeDisplayName(request.displayName)

        val existing = authRepository.findByEmail(email)
        if (existing != null) {
            throw EmailAlreadyRegisteredException()
        }

        val createdUser = authRepository.createLocalUser(
            email = email,
            passwordHash = passwordHasher.hash(request.password),
            displayName = displayName
        )

        authRepository.updateLastLogin(createdUser.id)
        return buildAuthResponse(createdUser)
    }

    suspend fun login(request: LoginRequestDto): AuthResponseDto {
        val email = normalizeEmail(request.email)
        val user = authRepository.findByEmail(email)
            ?: throw InvalidCredentialsException()

        if (!user.isActive) {
            throw InactiveUserException()
        }

        val storedHash = user.passwordHash
            ?: throw InvalidCredentialsException()

        if (!passwordHasher.verify(request.password, storedHash)) {
            throw InvalidCredentialsException()
        }

        authRepository.updateLastLogin(user.id)
        return buildAuthResponse(user)
    }

    suspend fun me(userId: Uuid): AuthUserResponseDto {
        val user = authRepository.findById(userId)
            ?: throw UnauthorizedAuthException()

        if (!user.isActive) {
            throw UnauthorizedAuthException()
        }

        val email = user.email ?: throw UnauthorizedAuthException()

        return AuthUserResponseDto(
            id = user.id.toString(),
            email = email,
            displayName = user.displayName
        )
    }

    private fun buildAuthResponse(user: AuthUserModel): AuthResponseDto {
        val email = user.email ?: error("User email is null")

        return AuthResponseDto(
            accessToken = jwtTokenService.generateAccessToken(user),
            expiresInSeconds = jwtTokenService.expiresInSeconds(),
            user = AuthUserResponseDto(
                id = user.id.toString(),
                email = email,
                displayName = user.displayName
            )
        )
    }

    private fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase()

        require(normalized.isNotBlank()) { "Email is required" }
        require(normalized.length <= 320) { "Email is too long" }
        require(" " !in normalized) { "Invalid email" }
        require(normalized.count { it == '@' } == 1) { "Invalid email" }

        return normalized
    }

    private fun normalizeDisplayName(displayName: String?): String? {
        val normalized = displayName?.trim()?.takeIf { it.isNotEmpty() }
        require(normalized == null || normalized.length <= 120) {
            "Display name is too long"
        }
        return normalized
    }

    private fun validatePassword(password: String) {
        require(password.length >= 8) {
            "Password must be at least 8 characters long"
        }
        require(password.length <= 72) {
            "Password is too long"
        }
    }
}
