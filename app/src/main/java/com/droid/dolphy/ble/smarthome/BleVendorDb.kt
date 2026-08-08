package com.droid.dolphy.ble.smarthome

import android.content.Context
import java.util.Locale

object BleVendorDb {
    @Volatile
    private var cached: Map<String, String>? = null

    fun getVendorLabel(context: Context, macAddress: String?): String {
        val mac = macAddress?.uppercase(Locale.ROOT) ?: return "Unknown"
        val oui = mac.split(":").take(3).joinToString(":")
        val db = cached ?: load(context).also { cached = it }
        return db[oui] ?: "Unknown"
    }

    private fun load(context: Context): Map<String, String> {
        return runCatching {
            val map = HashMap<String, String>(32_000)
            context.assets.open("ble/nmap-mac-prefixes.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank() || line.startsWith("#")) return@forEach

                    val parts = line.split("\t", limit = 2)
                    if (parts.size != 2) return@forEach
                    val hex = parts[0].trim()
                    if (hex.length != 6) return@forEach
                    val oui = hex.chunked(2).joinToString(":") { it.uppercase(Locale.ROOT) }
                    map[oui] = parts[1].trim()
                }
            }
            map
        }.getOrDefault(emptyMap())
    }
}


