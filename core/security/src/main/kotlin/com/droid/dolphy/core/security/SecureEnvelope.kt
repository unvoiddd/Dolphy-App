package com.droid.dolphy.core.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small, dependency-free AES-256-GCM envelope used for data at rest.
 *
 * Format: `v1:<base64url(iv)>:<base64url(ciphertext+tag)>`
 */
object SecureEnvelope {

    const val PREFIX = "v1:"

    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val SEPARATOR = ':'

    private val secureRandom = SecureRandom()

    fun isEncrypted(value: String?): Boolean = value?.startsWith(PREFIX) == true

    fun encrypt(plaintext: ByteArray, key: SecretKey): String {
        require(plaintext.isNotEmpty()) { "plaintext must not be empty" }
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return buildString(plaintext.size + 32) {
            append(PREFIX)
            append(encoder.encodeToString(iv))
            append(SEPARATOR)
            append(encoder.encodeToString(ciphertext))
        }
    }

    fun decrypt(encoded: String, key: SecretKey): ByteArray {
        require(isEncrypted(encoded)) { "unsupported envelope format" }
        val parts = encoded.removePrefix(PREFIX).split(SEPARATOR)
        require(parts.size == 2) { "malformed envelope" }
        val decoder = Base64.getUrlDecoder()
        val iv = decoder.decode(parts[0])
        require(iv.size == IV_BYTES) { "invalid iv" }
        val ciphertext = decoder.decode(parts[1])
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
