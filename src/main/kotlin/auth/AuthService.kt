@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.example.mutlabocnotes.auth

import java.time.OffsetDateTime
import kotlin.uuid.Uuid

// Сервис с прикладной бизнес-логикой модуля.
class AuthService(
    private val authRepository: AuthRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordHasher: PasswordHasher,
    private val jwtTokenService: JwtTokenService,
    private val refreshTokenService: RefreshTokenService
) {

    // Регистрирует нового пользователя и возвращает авторизационную сессию.
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

    // Проверяет учётные данные и возвращает авторизационную сессию.
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

    // Переиздаёт access/refresh токены по валидному refresh-токену.
    suspend fun refresh(request: RefreshTokenRequestDto): AuthResponseDto {
        val rawRefreshToken = request.refreshToken.trim()
        require(rawRefreshToken.isNotEmpty()) { "Refresh token is required" }

        // Ищем refresh-токен по хешу, чтобы не хранить «сырой» токен в БД.
        val tokenHash = refreshTokenService.hash(rawRefreshToken)
        val storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
            ?: throw UnauthorizedAuthException()

        // Проверяем, что токен ещё валиден и не был отозван ранее.
        if (storedToken.revokedAt != null || storedToken.expiresAt.isBefore(OffsetDateTime.now())) {
            throw UnauthorizedAuthException()
        }

        val user = authRepository.findById(storedToken.userId)
            ?: throw UnauthorizedAuthException()

        if (!user.isActive) {
            throw UnauthorizedAuthException()
        }

        // Делаем refresh-токен одноразовым: отзываем старый и выпускаем новую пару.
        refreshTokenRepository.revokeByTokenHash(tokenHash)
        authRepository.updateLastLogin(user.id)

        return buildAuthResponse(user)
    }

    // Revokes the current session refresh token without revealing whether it existed.
    suspend fun logout(request: LogoutRequestDto) {
        val rawRefreshToken = request.refreshToken.trim()
        require(rawRefreshToken.isNotEmpty()) { "Refresh token is required" }

        val tokenHash = refreshTokenService.hash(rawRefreshToken)
        refreshTokenRepository.revokeByTokenHash(tokenHash)
    }

    // Returns the profile for the current authenticated user.
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
            displayName = user.displayName,
            bridgeUserKey = user.firebaseUid
        )
    }

    // Выпускает новую сессию для пользователя после внешней аутентификации.
    suspend fun issueSessionForUserId(userId: Uuid): AuthResponseDto {
        val user = requireActiveUser(userId)
        authRepository.updateLastLogin(user.id)
        return buildAuthResponse(user)
    }

    // Проверяет, что пользователь существует, активен и имеет email.
    private suspend fun requireActiveUser(userId: Uuid): AuthUserModel {
        val user = authRepository.findById(userId)
            ?: throw UnauthorizedAuthException()

        if (!user.isActive) {
            throw UnauthorizedAuthException()
        }

        if (user.email.isNullOrBlank()) {
            throw UnauthorizedAuthException()
        }

        return user
    }

    // Формирует единый ответ авторизации с токенами и данными пользователя.
    private suspend fun buildAuthResponse(user: AuthUserModel): AuthResponseDto {
        val email = user.email ?: error("User email is null")
        val refreshToken = issueRefreshToken(user.id)

        // Возвращаем токены и согласованный профиль пользователя в одном ответе.
        return AuthResponseDto(
            accessToken = jwtTokenService.generateAccessToken(user),
            refreshToken = refreshToken,
            expiresInSeconds = jwtTokenService.expiresInSeconds(),
            refreshExpiresInSeconds = refreshTokenService.refreshExpiresInSeconds(),
            bridgeUserKey = user.firebaseUid,
            user = AuthUserResponseDto(
                id = user.id.toString(),
                email = email,
                displayName = user.displayName,
                bridgeUserKey = user.firebaseUid
            )
        )
    }

    // Создаёт и сохраняет новый refresh-токен в хранилище.
    private suspend fun issueRefreshToken(userId: Uuid): String {
        val rawToken = refreshTokenService.generateToken()
        val tokenHash = refreshTokenService.hash(rawToken)

        refreshTokenRepository.create(
            userId = userId,
            tokenHash = tokenHash,
            expiresAt = refreshTokenService.expiresAt()
        )

        return rawToken
    }

    // Нормализует email и валидирует базовые ограничения формата.
    private fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase()

        require(normalized.isNotBlank()) { "Email is required" }
        require(normalized.length <= 320) { "Email is too long" }
        require(" " !in normalized) { "Invalid email" }
        require(normalized.count { it == '@' } == 1) { "Invalid email" }

        return normalized
    }

    // Очищает и валидирует отображаемое имя пользователя.
    private fun normalizeDisplayName(displayName: String?): String? {
        val normalized = displayName?.trim()?.takeIf { it.isNotEmpty() }
        require(normalized == null || normalized.length <= 120) {
            "Display name is too long"
        }
        return normalized
    }

    // Проверяет длину пароля по правилам безопасности.
    private fun validatePassword(password: String) {
        require(password.length >= 8) {
            "Password must be at least 8 characters long"
        }
        require(password.length <= 72) {
            "Password is too long"
        }
    }
}
