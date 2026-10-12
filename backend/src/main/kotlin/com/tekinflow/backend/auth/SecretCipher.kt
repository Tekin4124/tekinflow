package com.tekinflow.backend.auth

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** TOTP needs reversible encryption; the key must be kept outside the database. */
class SecretCipher(key: ByteArray) {
    private val keySpec = SecretKeySpec(key.copyOf(), "AES")
    init { require(key.size == 32) { "TOTP_ENCRYPTION_KEY must decode to 32 bytes" } }
    fun encrypt(value: String): String {
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
        return PREFIX + Base64.getEncoder().encodeToString(iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }
    fun decrypt(value: String): String {
        require(value.startsWith(PREFIX)) { "TOTP secret must be encrypted before use" }
        val bytes = Base64.getDecoder().decode(value.removePrefix(PREFIX))
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
    companion object { const val PREFIX = "enc:v1:" }
}
object TotpSecretVault {
    val cipher: SecretCipher by lazy {
        val encoded = System.getenv("TOTP_ENCRYPTION_KEY") ?: error("TOTP_ENCRYPTION_KEY must be configured separately from JWT_SECRET")
        SecretCipher(Base64.getDecoder().decode(encoded.trim()))
    }
    fun encrypt(secret: String) = cipher.encrypt(secret)
    fun decrypt(secret: String) = cipher.decrypt(secret)
}
