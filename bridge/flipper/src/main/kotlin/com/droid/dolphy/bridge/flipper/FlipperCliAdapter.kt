package com.droid.dolphy.bridge.flipper

import com.droid.dolphy.core.model.HardwareDescriptor
import com.droid.dolphy.core.model.HwCapability
import com.droid.dolphy.core.model.SafetyProfile
import com.droid.dolphy.core.model.SupportLevel
import com.droid.dolphy.core.model.Transport

data class FlipperInfo(
    val firmware: String?,
    val hardware: String?,
    val serial: String?,
    val raw: String,
)

/**
 * Version-aware adapter around the Flipper CLI. It only emits validated,
 * printable commands and never forwards arbitrary network/plugin input.
 */
class FlipperCliAdapter(
    private val transport: FlipperTransport,
    private val readTimeoutMillis: Long = 2_000,
) {
    fun connect(): FlipperInfo {
        if (!transport.isOpen) transport.open()
        // Wake the CLI and discard any stale prompt.
        transport.writeLine("")
        Thread.sleep(120)
        drain()
        return info()
    }

    fun info(): FlipperInfo {
        transport.writeLine("info")
        val output = readUntilPrompt()
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        fun value(keys: List<String>): String? = lines.firstOrNull { line ->
            keys.any { key -> line.startsWith(key, ignoreCase = true) }
        }?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }

        return FlipperInfo(
            firmware = value(listOf("firmware", "firmware version")),
            hardware = value(listOf("hardware", "hardware version")),
            serial = value(listOf("serial", "serial number")),
            raw = output,
        )
    }

    /**
     * Sends a diagnostic command composed only of a conservative character
     * set. Multi-line, control characters and shell metacharacters are rejected.
     */
    fun sendCommand(command: String): String {
        require(COMMAND_PATTERN.matches(command)) { "unsafe Flipper CLI command" }
        transport.writeLine(command)
        return readUntilPrompt()
    }

    fun descriptor(info: FlipperInfo = info()): HardwareDescriptor = HardwareDescriptor(
        id = "flipper:${info.serial ?: info.hardware ?: "unknown"}",
        name = "Flipper Zero",
        transport = Transport.USB_SERIAL,
        capabilities = FLIPPER_CAPABILITIES,
        vendorId = FLIPPER_VENDOR_ID,
        productId = FLIPPER_PRODUCT_ID,
        firmware = info.firmware,
        driverId = "flipper-cli",
        supportLevel = SupportLevel.BETA,
        safety = SafetyProfile(requiresLabMode = true),
    )

    private fun readUntilPrompt(): String {
        val builder = StringBuilder()
        val deadline = System.currentTimeMillis() + readTimeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val line = transport.readLine(deadline - System.currentTimeMillis()) ?: break
            builder.append(line).append('\n')
            if (line.trimEnd().endsWith(">")) break
            if (builder.length > MAX_RESPONSE_CHARS) break
        }
        return builder.toString().take(MAX_RESPONSE_CHARS)
    }

    private fun drain() {
        val deadline = System.currentTimeMillis() + 250
        while (System.currentTimeMillis() < deadline) {
            transport.readLine(50) ?: break
        }
    }

    companion object {
        const val FLIPPER_VENDOR_ID = 0x0483
        const val FLIPPER_PRODUCT_ID = 0x5740
        private const val MAX_RESPONSE_CHARS = 64 * 1024
        private val COMMAND_PATTERN = Regex("[A-Za-z0-9 _./:=+\\-]{1,200}")

        val FLIPPER_CAPABILITIES: Set<HwCapability> = setOf(
            HwCapability.SUBGHZ_RX,
            HwCapability.SUBGHZ_TX,
            HwCapability.RFID125_READ,
            HwCapability.RFID125_WRITE,
            HwCapability.NFC_READ,
            HwCapability.NFC_WRITE,
            HwCapability.NFC_EMULATE,
            HwCapability.IR_TX,
            HwCapability.IR_RX,
            HwCapability.IBUTTON,
            HwCapability.GPIO,
            HwCapability.HID,
            HwCapability.UART,
        )
    }
}
