package com.droid.dolphy.bridge.flipper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlipperCliAdapterTest {

    private class FakeTransport : FlipperTransport {
        override var isOpen = false
        val written = mutableListOf<String>()
        private val incoming = ArrayDeque<String>()

        override fun open() {
            isOpen = true
        }

        override fun writeLine(line: String) {
            written += line
            if (line == "info") {
                incoming.addAll(
                    listOf(
                        "Firmware: 1.0.0",
                        "Hardware: Flipper Zero",
                        "Serial: FLP-123",
                        ">",
                    ),
                )
            }
            if (line == "power info") {
                incoming.addAll(listOf("Battery: 82%", ">"))
            }
        }

        override fun readLine(timeoutMillis: Long): String? = incoming.removeFirstOrNull()

        override fun close() {
            isOpen = false
        }
    }

    @Test
    fun parsesInfoAndBuildsDescriptor() {
        val transport = FakeTransport()
        val adapter = FlipperCliAdapter(transport)
        val info = adapter.connect()

        assertEquals("1.0.0", info.firmware)
        assertEquals("FLP-123", info.serial)

        val descriptor = adapter.descriptor(info)
        assertTrue(descriptor.supports(com.droid.dolphy.core.model.HwCapability.SUBGHZ_TX))
        assertTrue(descriptor.supports(com.droid.dolphy.core.model.HwCapability.IR_RX))
        assertEquals("flipper-cli", descriptor.driverId)
    }

    @Test
    fun rejectsUnsafeCommands() {
        val adapter = FlipperCliAdapter(FakeTransport())
        adapter.connect()

        listOf("info; reboot", "ir tx\nreboot", "subghz tx `id`", "gpio set \\x00").forEach { command ->
            val rejected = runCatching { adapter.sendCommand(command) }.isFailure
            assertTrue("should reject: $command", rejected)
        }
    }

    @Test
    fun acceptsReadOnlyDiagnosticCommand() {
        val adapter = FlipperCliAdapter(FakeTransport())
        adapter.connect()
        val output = adapter.sendCommand("power info")
        assertTrue(output.contains("82%"))
        assertFalse(output.contains("reboot"))
    }
}
