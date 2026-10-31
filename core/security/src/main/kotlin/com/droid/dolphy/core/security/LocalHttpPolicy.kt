package com.droid.dolphy.core.security

import java.net.InetAddress
import java.net.URI
import java.net.URL

/**
 * Central egress policy for Dolphy's local-network features.
 *
 * All HTTP/TCP code that talks to LAN devices must pass through this policy so
 * that a future refactor cannot accidentally send credentials to the public
 * internet. `.local` mDNS names are accepted; everything else must resolve to
 * a private, link-local or loopback address.
 */
object LocalHttpPolicy {

    fun isLocalUrl(url: String): Boolean {
        val parsed = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }
        val scheme = parsed.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = parsed.host ?: return false
        return isLocalHost(host)
    }

    fun isLocalHost(host: String): Boolean {
        val normalized = host.trim().trim('[', ']').lowercase()
        if (normalized.isEmpty()) return false
        if (normalized == "localhost" || normalized.endsWith(".local")) return true
        if (normalized == "::1") return true
        return try {
            val addresses = InetAddress.getAllByName(normalized)
            addresses.isNotEmpty() && addresses.all { isPrivateAddress(it) }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns the URL when it is local, otherwise throws. Use this in code
     * paths that must never contact the internet.
     */
    fun requireLocal(url: String): URL {
        require(isLocalUrl(url)) { "Blocked non-local URL" }
        return URL(url)
    }

    fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) {
            return true
        }
        val bytes = address.address
        if (bytes.size == 16) {
            // Unique local addresses fc00::/7
            return (bytes[0].toInt() and 0xfe) == 0xfc
        }
        return false
    }
}
