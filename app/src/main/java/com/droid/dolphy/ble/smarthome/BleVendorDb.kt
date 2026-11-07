package com.droid.dolphy.ble.smarthome

import android.content.Context
import java.util.Locale

object BleVendorDb {
    @Volatile
    private var cached: Map<String, String>? = null

    fun getVendorLabel(context: Context, macAddress: String?): String {
        val mac = macAddress?.uppercase(Locale.ROOT) ?: return "Unknown"
        val db = cached ?: load(context).also { cached = it }
        return VendorPrefixes.lookup(db, mac) ?: "Unknown"
    }

    private fun load(context: Context): Map<String, String> {
        return runCatching {
            val map = HashMap<String, String>(32_000)
            context.assets.open("ble/nmap-mac-prefixes.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    VendorPrefixes.parseLine(line)?.let { (oui, vendor) -> map[oui] = vendor }
                }
            }
            map
        }.getOrDefault(emptyMap())
    }
}
