package com.droid.dolphy

object HexUtils {




    fun hexToByteArray(input: String): ByteArray {
        val clean = input
            .trim()
            .removePrefix("0x")
            .removePrefix("0X")
            .replace("\\s+".toRegex(), "")

        require(clean.isNotEmpty()) { "Hex payload is empty" }
        require(clean.length % 2 == 0) { "Hex payload must contain an even number of characters" }
        require(clean.matches(Regex("^[0-9a-fA-F]+$"))) { "Hex payload contains invalid characters" }

        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }




    fun parseCompanyCode(input: String): Int {
        val clean = input
            .trim()
            .removePrefix("0x")
            .removePrefix("0X")

        require(clean.isNotEmpty()) { "Brand code is required" }
        require(clean.matches(Regex("^[0-9a-fA-F]+$"))) { "Brand code must be hex" }

        return clean.toInt(16)
    }
}


