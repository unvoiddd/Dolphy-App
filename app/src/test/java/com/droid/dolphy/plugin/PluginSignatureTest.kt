package com.droid.dolphy.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class PluginSignatureTest {

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(256) }
        .generateKeyPair()

    private val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private val trustedKey = TrustedPluginKey(
        keyId = "dolphy-test",
        signer = "Dolphy Test",
        publicKeyBase64 = publicKeyBase64,
    )

    private val pythonBody = """
        __plugin__ = {
            "id": "wifi_probe",
            "name": "WiFi Probe",
            "version": "1.0"
        }

        def on_start():
            return "ok"
    """.trimIndent()

    private fun sign(raw: String, alias: String = "dolphy-test"): String {
        val payload = PluginSignature.canonicalPayload(raw)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(payload.toByteArray(Charsets.UTF_8))
            sign()
        }
        val header = "# @dolphy-signature v1 keyId=$alias sig=${Base64.getEncoder().encodeToString(signature)}"
        return "$header\n$raw"
    }

    @Test
    fun `unsigned plugin is reported as unsigned`() {
        assertEquals(PluginSignatureStatus.Unsigned, PluginSignature.verify(pythonBody, emptyList()))
        assertFalse(PluginSignature.hasSignature(pythonBody))
    }

    @Test
    fun `valid signature with trusted key is trusted`() {
        val signed = sign(pythonBody)
        assertTrue(PluginSignature.hasSignature(signed))

        val status = PluginSignature.verify(signed, listOf(trustedKey))

        assertTrue(status is PluginSignatureStatus.Trusted)
        assertEquals("Dolphy Test", (status as PluginSignatureStatus.Trusted).signer)
        assertEquals("dolphy-test", status.keyId)
    }

    @Test
    fun `signature from unknown key is not trusted`() {
        val status = PluginSignature.verify(sign(pythonBody), emptyList())

        assertTrue(status is PluginSignatureStatus.UnknownKey)
        assertEquals("dolphy-test", (status as PluginSignatureStatus.UnknownKey).keyId)
    }

    @Test
    fun `tampered payload is rejected`() {
        val signed = sign(pythonBody)
        val tampered = signed.replace("return \"ok\"", "return \"pwned\"")

        val status = PluginSignature.verify(tampered, listOf(trustedKey))

        assertTrue(status is PluginSignatureStatus.Invalid)
    }

    @Test
    fun `forged header cannot reuse a valid signature`() {
        val signed = sign(pythonBody)
        val forgedHeader = "# @dolphy-signature v1 keyId=dolphy-test sig=${Base64.getEncoder().encodeToString(ByteArray(70))}"

        val status = PluginSignature.verify("$forgedHeader\n$pythonBody", listOf(trustedKey))

        assertTrue(status is PluginSignatureStatus.Invalid)
    }

    @Test
    fun `payload ignores signature header lines and line ending style`() {
        val signed = sign(pythonBody)

        val canonical = PluginSignature.canonicalPayload(signed)
        val crlf = signed.replace("\n", "\r\n")

        assertEquals(pythonBody, canonical)
        assertEquals(canonical, PluginSignature.canonicalPayload(crlf))
    }

    @Test
    fun `javascript comment marker is accepted`() {
        val payload = PluginSignature.canonicalPayload(pythonBody)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(payload.toByteArray(Charsets.UTF_8))
            sign()
        }
        val signed = "// @dolphy-signature v1 keyId=dolphy-test sig=${Base64.getEncoder().encodeToString(signature)}\n$pythonBody"

        assertTrue(PluginSignature.verify(signed, listOf(trustedKey)) is PluginSignatureStatus.Trusted)
    }

    @Test
    fun `malformed signature does not throw`() {
        val broken = "# @dolphy-signature v1 keyId=dolphy-test sig=not-base64!!\n$pythonBody"

        assertTrue(PluginSignature.verify(broken, listOf(trustedKey)) is PluginSignatureStatus.Invalid)
    }

    @Test
    fun `unsupported signature version is rejected`() {
        val signed = sign(pythonBody).replace("v1", "v2")

        assertTrue(PluginSignature.verify(signed, listOf(trustedKey)) is PluginSignatureStatus.Invalid)
    }
}
