package com.droid.dolphy.tvcast

data class HttpByteRange(val start: Long, val end: Long) {
    val length: Long get() = end - start + 1
    companion object {
        fun parse(header: String, size: Long): HttpByteRange? {
            if (size <= 0 || !header.startsWith("bytes=") || header.contains(',')) return null
            val parts = header.removePrefix("bytes=").split('-', limit = 2)
            if (parts.size != 2) return null
            if (parts[0].isEmpty()) {
                val suffix = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
                return HttpByteRange((size - suffix).coerceAtLeast(0), size - 1)
            }
            val start = parts[0].toLongOrNull()?.takeIf { it in 0 until size } ?: return null
            val end = if (parts[1].isEmpty()) size - 1 else parts[1].toLongOrNull() ?: return null
            if (end < start) return null
            return HttpByteRange(start, minOf(end, size - 1))
        }
    }
}
