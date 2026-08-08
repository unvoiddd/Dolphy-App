package com.tutozz.blespam

import android.util.Log

object Helper {
    const val MAX_LOOP = 1000

    fun log(message: String) {
        Log.d("BLESpam", message)
    }

    fun getRandomDelay(): Long = (100..500).random().toLong()

    fun convertHexToByteArray(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
    }
}
