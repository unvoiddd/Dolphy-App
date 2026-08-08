package com.droid.dolphy.nfc

fun ByteArray.toHexString(sep: String = ""): String {
    if (isEmpty()) return ""
    val sb = StringBuilder(size * (2 + sep.length))
    forEachIndexed { idx, b ->
        val v = b.toInt() and 0xFF
        if (idx > 0) sb.append(sep)
        if (v < 0x10) sb.append('0')
        sb.append(v.toString(16).uppercase())
    }
    return sb.toString()
}


