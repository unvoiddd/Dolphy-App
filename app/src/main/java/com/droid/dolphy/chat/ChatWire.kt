package com.droid.dolphy.chat

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object ChatWire {
    const val MAX_MESSAGE_BYTES = 4096
    private val magic = byteArrayOf(0x44, 0x43, 0x48, 1)

    fun encode(text: String): ByteArray {
        val body = text.toByteArray(Charsets.UTF_8)
        require(body.size in 1..MAX_MESSAGE_BYTES)
        return ByteBuffer.allocate(8 + body.size).put(magic).putInt(body.size).put(body).array()
    }

    class Receiver {
        private val buffer = ByteArrayOutputStream()
        fun accept(chunk: ByteArray): String? {
            require(buffer.size() + chunk.size <= MAX_MESSAGE_BYTES + 8)
            buffer.write(chunk)
            val bytes = buffer.toByteArray()
            if (bytes.size < 8) return null
            require(bytes.copyOfRange(0, 4).contentEquals(magic))
            val length = ByteBuffer.wrap(bytes, 4, 4).int
            require(length in 1..MAX_MESSAGE_BYTES)
            require(bytes.size <= length + 8)
            if (bytes.size != length + 8) return null
            buffer.reset()
            return bytes.copyOfRange(8, bytes.size).toString(Charsets.UTF_8)
        }
    }
}
