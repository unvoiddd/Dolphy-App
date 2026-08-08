package com.droid.dolphy

object HexUtils {




    fun hexToByteArray(input: String): ByteArray {
        val clean = input
            .trim()
            .removePrefix("0x")
            .removePrefix("0X")
            .replace("\\s+".toRegex(), "")

        require(clean.isNotEmpty()) { "Hex payload пустой" }
        require(clean.length % 2 == 0) { "Hex payload должен содержать чётное число символов" }
        require(clean.matches(Regex("^[0-9a-fA-F]+$"))) { "Hex payload содержит недопустимые символы" }

        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }




    fun parseCompanyCode(input: String): Int {
        val clean = input
            .trim()
            .removePrefix("0x")
            .removePrefix("0X")

        require(clean.isNotEmpty()) { "Код бренда обязателен" }
        require(clean.matches(Regex("^[0-9a-fA-F]+$"))) { "Код бренда должен быть hex" }

        return clean.toInt(16)
    }
}


