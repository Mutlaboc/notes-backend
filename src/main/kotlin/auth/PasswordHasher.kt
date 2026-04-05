package com.example.mutlabocnotes.auth

// Компонент для хеширования и проверки секретов.
interface PasswordHasher {
    // Вычисляет хеш для переданного значения.
    fun hash(rawPassword: String): String
    // Проверяет корректность входных данных и условий доступа.
    fun verify(rawPassword: String, storedHash: String): Boolean
}
