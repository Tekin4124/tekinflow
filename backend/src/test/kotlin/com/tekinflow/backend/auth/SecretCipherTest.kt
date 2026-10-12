package com.tekinflow.backend.auth
import kotlin.test.*
class SecretCipherTest {
    @Test fun cipherRoundTripsWithoutStoringPlaintext() {
        val cipher=SecretCipher(ByteArray(32){it.toByte()});val secret="JBSWY3DPEHPK3PXP";val encrypted=cipher.encrypt(secret)
        assertTrue(encrypted.startsWith(SecretCipher.PREFIX));assertFalse(encrypted.contains(secret));assertEquals(secret,cipher.decrypt(encrypted));assertNotEquals(encrypted,cipher.encrypt(secret))
    }
    @Test fun wrongKeysAndPlaintextFailClosed() {
        val value=SecretCipher(ByteArray(32){1}).encrypt("JBSWY3DPEHPK3PXP")
        assertFails{SecretCipher(ByteArray(32){2}).decrypt(value)}
        assertFails{SecretCipher(ByteArray(32){1}).decrypt("JBSWY3DPEHPK3PXP")}
        assertFails{SecretCipher(ByteArray(16))}
    }
}
