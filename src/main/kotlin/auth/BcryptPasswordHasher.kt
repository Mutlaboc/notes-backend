package com.example.mutlabocnotes.auth

import org.mindrot.jbcrypt.BCrypt

// Компонент для хеширования и проверки секретов.
class BcryptPasswordHasher(
    private val cost: Int = 12
) : PasswordHasher {

    // Вычисляет хеш для переданного значения.
    override fun hash(rawPassword: String): String =
        BCrypt.hashpw(rawPassword, BCrypt.gensalt(cost))

    // Проверяет корректность входных данных и условий доступа.
    override fun verify(rawPassword: String, storedHash: String): Boolean =
        BCrypt.checkpw(rawPassword, storedHash)
}
