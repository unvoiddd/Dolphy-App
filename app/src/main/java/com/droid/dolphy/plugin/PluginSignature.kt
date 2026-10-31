package com.droid.dolphy.plugin

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Integrity for user-supplied `.dolphyplugin` packages.
 *
 * A signed plugin carries one header line anywhere in the source:
 *
 * ```
 * # @dolphy-signature v1 keyId=<id> sig=<base64 ECDSA-P256/SHA-256>
 * ```
 *
 * (`//` is accepted as the comment marker for JavaScript plugins.)
 *
 * The signed payload is the plugin source with every signature header line removed and
 * line endings normalised to `\n`. Removing the header before verifying is what makes the
 * signature stable when a plugin is re-shared, and prevents a forged header from being
 * signed by a valid key.
 */
sealed interface PluginSignatureStatus {
    /** Signed by a key present in the trust store. */
    data class Trusted(
        val keyId: String,
        val signer: String,
        val fingerprint: String,
    ) : PluginSignatureStatus

    /** Signed, but the key is not trusted by this build. */
    data class UnknownKey(
        val keyId: String,
        val fingerprint: String,
    ) : PluginSignatureStatus

    /** A signature header exists but it does not match the payload. */
    data class Invalid(val reason: String) : PluginSignatureStatus

    /** No signature header at all (e.g. hand-written or bundled plugins). */
    data object Unsigned : PluginSignatureStatus

    val isTrusted: Boolean get() = this is Trusted
    val isTampered: Boolean get() = this is Invalid
}

data class TrustedPluginKey(
    val keyId: String,
    val signer: String,
    /** X.509 SubjectPublicKeyInfo, base64 (no PEM armour). */
    val publicKeyBase64: String,
)

object PluginSignature {
    private const val ALGORITHM = "SHA256withECDSA"
    private const val HEADER_MARK = "@dolphy-signature"
    private const val VERSION = "v1"

    private val headerRegex = Regex(
        """^\s*(?:#|//)\s*@dolphy-signature\s+(\S+)\s+keyId=(\S+)\s+sig=(\S+)\s*$""",
    )

    /** Canonical payload: every signature header line removed, CRLF collapsed to LF. */
    fun canonicalPayload(raw: String): String {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        return normalized.lineSequence()
            .filterNot { isSignatureHeader(it) }
            .joinToString("\n")
    }

    fun hasSignature(raw: String): Boolean =
        raw.replace("\r\n", "\n").lineSequence().any { isSignatureHeader(it) }

    fun keyFingerprint(publicKeyDer: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(publicKeyDer)
        return digest.joinToString("") { "%02X".format(it) }.chunked(4).joinToString(":").take(47)
    }

    /**
     * Verifies [raw] against [trustedKeys].
     *
     * Never throws: malformed keys or signatures are reported as [PluginSignatureStatus.Invalid].
     */
    fun verify(raw: String, trustedKeys: Collection<TrustedPluginKey>): PluginSignatureStatus {
        val header = raw.replace("\r\n", "\n").lineSequence()
            .firstOrNull { isSignatureHeader(it) }
            ?.let { headerRegex.matchEntire(it) }
            ?: return PluginSignatureStatus.Unsigned

        val version = header.groupValues[1]
        val keyId = header.groupValues[2]
        val signatureBase64 = header.groupValues[3]
        if (version != VERSION) {
            return PluginSignatureStatus.Invalid("unsupported signature version: $version")
        }

        val signatureBytes = runCatching { Base64.getDecoder().decode(signatureBase64) }
            .getOrNull()
            ?: return PluginSignatureStatus.Invalid("signature is not valid base64")

        val expected = trustedKeys.firstOrNull { it.keyId == keyId }
            ?: return PluginSignatureStatus.UnknownKey(keyId = keyId, fingerprint = "unknown")

        val publicKeyDer = runCatching { Base64.getDecoder().decode(expected.publicKeyBase64) }
            .getOrElse { return PluginSignatureStatus.Invalid("trusted key $keyId is not valid base64") }

        val fingerprint = keyFingerprint(publicKeyDer)
        val publicKey = runCatching { decodePublicKey(publicKeyDer) }
            .getOrElse { return PluginSignatureStatus.Invalid("trusted key $keyId cannot be decoded") }

        val valid = runCatching {
            Signature.getInstance(ALGORITHM).run {
                initVerify(publicKey)
                update(canonicalPayload(raw).toByteArray(Charsets.UTF_8))
                verify(signatureBytes)
            }
        }.getOrElse { false }

        return if (valid) {
            PluginSignatureStatus.Trusted(
                keyId = keyId,
                signer = expected.signer,
                fingerprint = fingerprint,
            )
        } else {
            PluginSignatureStatus.Invalid("payload does not match signature")
        }
    }

    fun decodePublicKey(publicKeyDer: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyDer))

    private fun isSignatureHeader(line: String): Boolean = line.contains(HEADER_MARK)
}

/**
 * Keys this build trusts for plugin signatures.
 *
 * The list is intentionally empty in the open build: official plugins ship inside the APK and
 * are therefore trusted by build provenance, while every externally supplied plugin is
 * reported as [PluginSignatureStatus.Unsigned] or [PluginSignatureStatus.UnknownKey] and needs
 * an explicit opt-in from the user. Releases that want verifiable third-party plugins can add
 * their publishing key here.
 */
object PluginTrustStore {
    val keys: List<TrustedPluginKey> = emptyList()

    fun find(keyId: String): TrustedPluginKey? = keys.firstOrNull { it.keyId == keyId }
}
