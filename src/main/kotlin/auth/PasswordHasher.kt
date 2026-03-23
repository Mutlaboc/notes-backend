package com.example.mutlabocnotes.auth

interface PasswordHasher {
    fun hash(rawPassword: String): String
    fun verify(rawPassword: String, storedHash: String): Boolean
}
