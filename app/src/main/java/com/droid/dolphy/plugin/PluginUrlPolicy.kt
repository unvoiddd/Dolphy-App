package com.droid.dolphy.plugin

import java.net.URL

/**
 * Transport policy for plugin downloads.
 *
 * Plugin packages are executable code, so they are only fetched over TLS: a plain-HTTP link or
 * a redirect that downgrades to HTTP is rejected instead of being silently upgraded, because an
 * attacker on the network could otherwise substitute the payload.
 */
object PluginUrlPolicy {
    const val MAX_REDIRECTS = 5

    private val allowedSchemes = setOf("https")

    /** Normalises a pasted link; bare hosts get `https://`. Returns null when unusable. */
    fun normalize(raw: String): URL? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> return null
            trimmed.contains("://") -> return null
            else -> "https://$trimmed"
        }
        return runCatching { URL(withScheme) }.getOrNull()
    }

    fun isAllowed(url: URL): Boolean =
        url.protocol.lowercase() in allowedSchemes && !url.host.isNullOrBlank()

    /**
     * Validates a redirect target. Relative locations are resolved against [base]; anything that
     * is not HTTPS is rejected.
     */
    fun resolveRedirect(base: URL, location: String): URL? {
        val target = runCatching { URL(base, location) }.getOrNull() ?: return null
        return target.takeIf { isAllowed(it) }
    }

    /** True when the final response URL differs from the request (redirect happened). */
    fun isSameOrigin(first: URL, last: URL): Boolean =
        first.protocol.equals(last.protocol, ignoreCase = true) &&
            first.host.equals(last.host, ignoreCase = true) &&
            effectivePort(first) == effectivePort(last)

    private fun effectivePort(url: URL): Int = if (url.port == -1) url.defaultPort else url.port
}
