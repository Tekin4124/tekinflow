package com.tekinflow.backend.auth

import at.favre.lib.crypto.bcrypt.BCrypt

class PasswordHasher(
    private val cost: Int = 12
) {

    fun hash(password: String): String {
        return BCrypt.withDefaults().hashToString(cost, password.toCharArray())
    }

    fun verify(password: String, passwordHash: String): Boolean {
        return BCrypt.verifyer().verify(password.toCharArray(), passwordHash).verified
    }

    // Precomputed throwaway hash used to spend comparable CPU time when the
    // supplied username does not exist, so response timing cannot be used to
    // enumerate valid usernames. The result is intentionally ignored.
    private val timingEqualizerHash: String = hash("timing-equalizer-placeholder")

    fun verifyDummy(password: String) {
        verify(password, timingEqualizerHash)
    }
}
