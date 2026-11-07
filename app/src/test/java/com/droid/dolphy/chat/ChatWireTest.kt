package com.droid.dolphy.chat
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
class ChatWireTest {
    @Test fun unicodeSurvivesEveryPacketBoundary() {
        val text = "Привет 🐬, Bluetooth!".repeat(40)
        val encoded = ChatWire.encode(text)
        for (packetSize in 1..30) {
            val receiver = ChatWire.Receiver()
            val messages = encoded.toList().chunked(packetSize).mapNotNull { receiver.accept(it.toByteArray()) }
            assertEquals(listOf(text), messages)
        }
    }
    @Test fun receiverAcceptsConsecutiveMessages() {
        val receiver = ChatWire.Receiver()
        assertEquals("one", receiver.accept(ChatWire.encode("one")))
        assertEquals("два", receiver.accept(ChatWire.encode("два")))
    }
    @Test fun incompleteMessageIsNotDelivered() {
        val bytes = ChatWire.encode("message")
        assertNull(ChatWire.Receiver().accept(bytes.copyOf(bytes.size - 1)))
    }
    @Test fun maximumMessageFitsAndOversizedMessageIsRejected() {
        val max = "a".repeat(ChatWire.MAX_MESSAGE_BYTES)
        assertEquals(max, ChatWire.Receiver().accept(ChatWire.encode(max)))
        assertThrows(IllegalArgumentException::class.java) { ChatWire.encode(max + "a") }
    }
    @Test fun malformedHeaderAndTrailingBytesAreRejected() {
        val valid = ChatWire.encode("hello")
        val invalidMagic = valid.copyOf().also { it[0] = 0 }
        assertThrows(IllegalArgumentException::class.java) { ChatWire.Receiver().accept(invalidMagic) }
        val invalidLength = valid.copyOf().also { ByteBuffer.wrap(it, 4, 4).putInt(Int.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { ChatWire.Receiver().accept(invalidLength) }
        assertThrows(IllegalArgumentException::class.java) { ChatWire.Receiver().accept(valid + byteArrayOf(0)) }
    }
}
