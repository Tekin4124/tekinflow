package com.tekinflow.backend.auth

import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object Totp {
    private const val issuer = "TekinFlow SVS"
    private const val timeStepSeconds = 30L
    private const val digits = 6
    private const val secretBytes = 20
    private const val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val secureRandom = SecureRandom()

    fun generateSecret(): String {
        val bytes = ByteArray(secretBytes)
        secureRandom.nextBytes(bytes)
        return base32Encode(bytes)
    }

    fun otpauthUrl(username: String, secret: String): String {
        val label = url("$issuer:${username.lowercase()}")
        val encodedIssuer = url(issuer)
        return "otpauth://totp/$label?secret=$secret&issuer=$encodedIssuer&algorithm=SHA1&digits=$digits&period=$timeStepSeconds"
    }

    fun verify(secret: String?, code: String?): Boolean {
        val normalizedSecret = secret?.trim()?.uppercase()?.takeIf { it.matches(Regex("[A-Z2-7]{16,64}")) } ?: return false
        val normalizedCode = code?.filter(Char::isDigit)?.takeIf { it.length == digits } ?: return false
        val counter = System.currentTimeMillis() / 1000L / timeStepSeconds
        return generateCode(normalizedSecret, counter) == normalizedCode
    }

    private fun generateCode(secret: String, counter: Long): String {
        val key = base32Decode(secret)
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val modulus = 10.0.pow(digits).toInt()
        return (binary % modulus).toString().padStart(digits, '0')
    }

    private fun base32Encode(bytes: ByteArray): String {
        val output = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        bytes.forEach { byte ->
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                output.append(alphabet[(buffer shr (bitsLeft - 5)) and 31])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            output.append(alphabet[(buffer shl (5 - bitsLeft)) and 31])
        }
        return output.toString()
    }

    private fun base32Decode(value: String): ByteArray {
        val bytes = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        value.trim().uppercase().forEach { char ->
            val index = alphabet.indexOf(char)
            require(index >= 0) { "Invalid base32 character" }
            buffer = (buffer shl 5) or index
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bytes += ((buffer shr (bitsLeft - 8)) and 0xff).toByte()
                bitsLeft -= 8
            }
        }
        return bytes.toByteArray()
    }

    private fun url(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
