package com.droid.dolphy.core.security

import java.net.URI

/**
 * Validates HTTP Host and WebSocket Origin headers for local servers.
 * Defeats DNS rebinding and cross-site WebSocket hijacking.
 */
object LocalRequestPolicy {

    private val ALLOWED_LOOPBACK = setOf("localhost", "127.0.0.1", "::1")

    fun isAllowedHost(
        hostHeader: String?,
        expectedHost: String,
        expectedPort: Int,
    ): Boolean {
        val parsed = parseAuthority(hostHeader) ?: return false
        if (parsed.port != null && parsed.port != expectedPort) return false

        val allowed = ALLOWED_LOOPBACK + expectedHost.lowercase()
        return parsed.host in allowed
    }

    fun isAllowedOrigin(
        origin: String?,
        expectedHost: String,
        expectedPort: Int,
    ): Boolean {
        if (origin.isNullOrBlank() || origin == "null") return false
        val uri = try {
            URI(origin)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (!uri.userInfo.isNullOrEmpty()) return false
        val host = uri.host?.lowercase() ?: return false
        val port = if (uri.port != -1) uri.port else if (scheme == "https") 443 else 80
        if (port != expectedPort) return false
        val allowed = ALLOWED_LOOPBACK + expectedHost.lowercase()
        return host in allowed
    }

    internal fun parseAuthority(value: String?): ParsedAuthority? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty() || raw.length > 255) return null
        if (raw.contains('/') || raw.contains('@') || raw.contains('\\')) return null

        if (raw.startsWith("[")) {
            val end = raw.indexOf(']')
            if (end <= 1) return null
            val host = raw.substring(1, end).lowercase()
            if (host.contains('[') || host.contains(']')) return null
            val rest = raw.substring(end + 1)
            val port = when {
                rest.isEmpty() -> null
                rest.startsWith(":") -> rest.substring(1).toIntOrNull()?.takeIf { it in 1..65535 }
                else -> return null
            }
            if (rest.startsWith(":") && port == null) return null
            return ParsedAuthority(host, port)
        }

        val parts = raw.split(':')
        if (parts.size > 2) return null
        val host = parts[0].lowercase()
        if (host.isEmpty()) return null
        val port = if (parts.size == 2) {
            parts[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        } else {
            null
        }
        return ParsedAuthority(host, port)
    }

    internal data class ParsedAuthority(val host: String, val port: Int?)
}
