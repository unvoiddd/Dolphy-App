package com.droid.dolphy.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec

class SecureEnvelopeTest {

    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    @Test
    fun roundTripsUtf8Payload() {
        val plaintext = "شبكة-المنزل|p@ssw0rd-✓".toByteArray(StandardCharsets.UTF_8)
        val encrypted = SecureEnvelope.encrypt(plaintext, key)

        assertTrue(SecureEnvelope.isEncrypted(encrypted))
        assertTrue(encrypted.startsWith(SecureEnvelope.PREFIX))
        assertArrayEquals(plaintext, SecureEnvelope.decrypt(encrypted, key))
    }

    @Test
    fun usesFreshIvForEveryEncryption() {
        val plaintext = "same input".toByteArray(StandardCharsets.UTF_8)
        val first = SecureEnvelope.encrypt(plaintext, key)
        val second = SecureEnvelope.encrypt(plaintext, key)

        assertNotEquals(first, second)
        assertArrayEquals(plaintext, SecureEnvelope.decrypt(first, key))
        assertArrayEquals(plaintext, SecureEnvelope.decrypt(second, key))
    }

    @Test(expected = AEADBadTagException::class)
    fun rejectsTamperedCiphertext() {
        val encrypted = SecureEnvelope.encrypt("secret".toByteArray(StandardCharsets.UTF_8), key)
        val index = SecureEnvelope.PREFIX.length + 1
        val replacement = if (encrypted[index] == 'A') 'B' else 'A'
        val tampered = encrypted.substring(0, index) + replacement + encrypted.substring(index + 1)
        SecureEnvelope.decrypt(tampered, key)
    }

    @Test
    fun detectsEnvelopePrefix() {
        assertTrue(SecureEnvelope.isEncrypted("v1:abc:def"))
        assertFalse(SecureEnvelope.isEncrypted("plain text"))
        assertFalse(SecureEnvelope.isEncrypted(null))
    }
}
