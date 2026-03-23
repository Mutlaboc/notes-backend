package com.example.mutlabocnotes.auth

import org.mindrot.jbcrypt.BCrypt

class BcryptPasswordHasher(
    private val cost: Int = 12
) : PasswordHasher {

    override fun hash(rawPassword: String): String =
        BCrypt.hashpw(rawPassword, BCrypt.gensalt(cost))

    override fun verify(rawPassword: String, storedHash: String): Boolean =
        BCrypt.checkpw(rawPassword, storedHash)
}
