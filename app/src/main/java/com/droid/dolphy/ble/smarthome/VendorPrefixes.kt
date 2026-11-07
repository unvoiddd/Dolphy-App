package com.droid.dolphy.ble.smarthome
import java.util.Locale
internal object VendorPrefixes {
    private val prefix = Regex("(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{7}|[0-9a-fA-F]{9})")
    fun parseLine(line: String): Pair<String, String>? {
        val clean = line.trim()
        if (clean.isEmpty() || clean.startsWith("#")) return null
        val parts = clean.split(Regex("\\s+"), limit = 2)
        if (parts.size != 2 || !prefix.matches(parts[0]) || parts[1].isBlank()) return null
        return parts[0].uppercase(Locale.ROOT) to parts[1].trim()
    }

    fun lookup(database: Map<String, String>, address: String): String? {
        val mac = address.replace(":", "").replace("-", "").uppercase(Locale.ROOT)
        if (!Regex("[0-9A-F]{12}").matches(mac)) return null
        return sequenceOf(9, 7, 6).mapNotNull { database[mac.take(it)] }.firstOrNull()
    }
}
