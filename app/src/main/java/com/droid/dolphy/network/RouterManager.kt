package com.droid.dolphy.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.io.File

class RouterManager(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    val configs = mutableListOf<RouterConfig>()


    var authCookie: String? = null
    var authStok: String? = null
    var authenticatedUser: String? = null
    var authenticatedPass: String? = null

    init {
        loadConfigs()
    }

    private fun loadConfigs() {
        try {
            val routerAssets = context.assets.list("routers") ?: return
            routerAssets.forEach { fileName ->
                if (fileName.endsWith(".json")) {
                    context.assets.open("routers/$fileName").bufferedReader().use {
                        val content = it.readText()
                        configs.add(json.decodeFromString<RouterConfig>(content))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun detectRouter(hostname: String?, ip: String, openPorts: List<Int>): RouterConfig? {
        val h = hostname?.lowercase() ?: ""
        val ports = openPorts.toSet()
        configs.find { h.contains(it.detectionKeyword.lowercase()) }?.let { return it }
        if (ports.contains(8089)) {
            configs.find { it.vendor.contains("Beeline", ignoreCase = true) }?.let { return it }
        }
        return null
    }

    suspend fun authenticate(config: RouterConfig, ip: String, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        authCookie = null
        authStok = null
        authenticatedUser = null
        authenticatedPass = null

        for ((user, pass) in config.allLogins) {
            onLog("Trying $user / ${pass.ifEmpty { "<blank>" }}")
            try {
                val url = URL("http://$ip${config.authEndpoint}")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000

                when (config.authType) {
                    "basic" -> {
                        val credentials = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
                        conn.setRequestProperty("Authorization", "Basic $credentials")
                        conn.requestMethod = "GET"
                    }
                    "digest" -> {
                        val credentials = Base64.getEncoder().encodeToString("$user:$pass".toByteArray())
                        conn.setRequestProperty("Authorization", "Basic $credentials")
                        conn.requestMethod = "GET"
                    }
                    else -> {
                        conn.requestMethod = config.method
                        conn.doOutput = true
                        config.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                        val body = "${config.authUsernameField}=${user}&${config.authPasswordField}=${pass}"
                        val writer = OutputStreamWriter(conn.outputStream)
                        writer.write(body)
                        writer.flush()
                    }
                }

                val code = conn.responseCode
                val responseText = try { conn.inputStream.bufferedReader().readText() } catch (e: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
                val cookie = conn.getHeaderField("Set-Cookie")

                val success = when {
                    code == 200 || code == 302 -> true
                    config.authSuccessIndicator.isNotEmpty() -> responseText.contains(config.authSuccessIndicator, ignoreCase = true)
                    code in 200..399 -> true
                    else -> false
                }

                if (success) {
                    authenticatedUser = user
                    authenticatedPass = pass
                    if (cookie != null) authCookie = cookie

                    val stokMatch = Regex("""stok=([a-f0-9]+)""").find(responseText)
                    if (stokMatch != null) authStok = stokMatch.groupValues[1]
                    onLog("OK ($user / ${pass.ifEmpty { "<blank>" }})")
                    return@withContext true
                }
            } catch (e: Exception) {
                onLog("Connection error: ${e.message}")
            }
        }
        onLog("No valid credentials found")
        return@withContext false
    }

    suspend fun reboot(config: RouterConfig, ip: String, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (config.rebootEndpoint.isEmpty()) {
            onLog("No reboot endpoint configured for ${config.vendor}")
            return@withContext false
        }

        try {
            val endpoint = config.rebootEndpoint.replace("\$STOK", authStok ?: "")
            val url = URL("http://$ip$endpoint")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "POST"
            conn.doOutput = true
            config.headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (authCookie != null) conn.setRequestProperty("Cookie", authCookie!!)
            conn.outputStream.close()

            val code = conn.responseCode
            if (code < 400) {
                onLog("Reboot command accepted (HTTP $code)")
                return@withContext true
            } else {
                onLog("Reboot rejected (HTTP $code)")
                return@withContext false
            }
        } catch (e: Exception) {
            onLog("Reboot error: ${e.message}")
            return@withContext false
        }
    }

    suspend fun fetchInfo(config: RouterConfig, ip: String, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val endpointsToTry = listOfNotNull(
            if (config.infoEndpoint.isNotEmpty()) config.infoEndpoint else null,
            config.authEndpoint,
            "/",
            "/status",
            "/cgi-bin/status",
            "/router_info.html",
            "/about.html",
        ).distinct()

        for (endpoint in endpointsToTry) {
            try {
                val resolved = endpoint.replace("\$STOK", authStok ?: "")
                val url = URL("http://$ip$resolved")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                if (authCookie != null) conn.setRequestProperty("Cookie", authCookie!!)

                val code = conn.responseCode
                val body = try { conn.inputStream.bufferedReader().readText() } catch (e: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }

                if (code < 400) {
                    onLog("Fetched from $resolved (HTTP $code)")

                    extractInfo(body, onLog)
                    return@withContext true
                }
            } catch (e: Exception) {
                onLog("Endpoint $endpoint: ${e.message}")
            }
        }
        onLog("Could not fetch router info")
        return@withContext false
    }

    suspend fun checkWifi(config: RouterConfig, ip: String, onLog: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (config.wifiEndpoint.isEmpty()) {
            onLog("No Wi-Fi endpoint configured")
            return@withContext false
        }
        try {
            val endpoint = config.wifiEndpoint.replace("\$STOK", authStok ?: "")
            val url = URL("http://$ip$endpoint")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            if (authCookie != null) conn.setRequestProperty("Cookie", authCookie!!)

            val code = conn.responseCode
            val body = try { conn.inputStream.bufferedReader().readText() } catch (e: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }

            if (code < 400) {
                onLog("Wi-Fi status fetched")
                extractWifiInfo(body, onLog)
                return@withContext true
            } else {
                onLog("Wi-Fi endpoint rejected (HTTP $code)")
                return@withContext false
            }
        } catch (e: Exception) {
            onLog("Wi-Fi error: ${e.message}")
            return@withContext false
        }
    }

    private fun extractInfo(body: String, onLog: (String) -> Unit) {
        val patterns = mapOf(
            "Model" to listOf("(?i)(?:model|device|product|router)[^<]*?[>]?\\s*:\\s*([^<\\n]+)".toRegex(), "(?i)<(?:td|div|span)[^>]*>(?:model|device)[^<]*</(?:td|div|span)>\\s*<(?:td|div|span)[^>]*>([^<]+)".toRegex()),
            "Firmware" to listOf("(?i)(?:firmware|version|software|fw_ver)[^<]*?[>]?\\s*:\\s*([^<\\n]+)".toRegex(), "(?i)firmware[^>]*>([^<]+)".toRegex()),
            "Uptime" to listOf("(?i)(?:uptime|up time|run.?time)[^<]*?[>]?\\s*:\\s*([^<\\n]+)".toRegex(), "(?i)uptime[^>]*>([^<]+)".toRegex()),
            "MAC" to listOf("(?i)(?:mac|mac.?address|wlan.?mac)[^<]*?[>]?\\s*:\\s*([0-9A-Fa-f:]{17})".toRegex()),
            "Serial" to listOf("(?i)(?:serial|sn)[^<]*?[>]?\\s*:\\s*([^<\\n]{6,30})".toRegex()),
            "CPU" to listOf("(?i)(?:cpu|processor|load)[^<]*?[>]?\\s*:\\s*([^<\\n]+)".toRegex()),
            "Memory" to listOf("(?i)(?:memory|ram|mem)[^<]*?[>]?\\s*:\\s*([^<\\n]+)".toRegex()),
            "SSID" to listOf("(?i)(?:ssid|wifi.?name|wireless.?name)[^<]*?[>]?\\s*:\\s*([^<\\n]+)".toRegex()),
        )
        var found = false
        for ((key, regexes) in patterns) {
            for (regex in regexes) {
                val match = regex.find(body)
                if (match != null) {
                    onLog("$key: ${match.groupValues[1].trim()}")
                    found = true
                    break
                }
            }
        }
        if (!found) onLog("Router is online (no detailed info extracted)")
    }

    private fun extractWifiInfo(body: String, onLog: (String) -> Unit) {
        val patterns = mapOf(
            "2.4GHz" to listOf("(?i)(?:2.4|2g|bgn)[^<]*?(?:enabled|status|on|up)[^<]*?(?:yes|1|on|enabled|up)".toRegex()),
            "5GHz" to listOf("(?i)(?:5|5g|ac|an)[^<]*?(?:enabled|status|on|up)[^<]*?(?:yes|1|on|enabled|up)".toRegex()),
            "SSID (2.4G)" to listOf("(?i)(?:ssid|wifi.?name)[^:]*?:\\s*([^<\\n]+)".toRegex()),
        )
        var found = false
        for ((key, regexes) in patterns) {
            for (regex in regexes) {
                val match = regex.find(body)
                if (match != null) {
                    val value = if (match.groupValues.size > 1) match.groupValues[1].trim() else "ON"
                    onLog("$key: $value")
                    found = true
                    break
                }
            }
        }
        if (!found) onLog("Wi-Fi interfaces detected (no detailed status)")
    }
}

