package com.droid.dolphy.driver.dsl

import com.droid.dolphy.core.hal.HardwareDriver
import com.droid.dolphy.core.model.HardwareDescriptor
import com.droid.dolphy.core.model.HwCapability
import com.droid.dolphy.core.model.Transport
import java.util.Properties

/**
 * Minimal DSL parser for driver definitions (`*.dsl`).
 *
 * Example `.dsl` file:
 * ```
 * descriptor {
 *     id = "flipper-cdc"
 *     name = "Flipper Zero (CDC)"
 *     transport = USB_SERIAL
 *     capabilities = [SUBGHZ_RX, SUBGHZ_TX, NFC_READ, NFC_WRITE, NFC_EMULATE, IR_TX, IR_RX, IBUTTON, GPIO, HID, UART]
 *     vendorId = 0x0483
 *     productId = 0x5740
 *     driverId = "flipper-cli"
 *     supportLevel = BETA
 *     requiresLabMode = true
 * }
 * ```
 */
object DriverDslParser {

    fun parse(source: String, descriptorOnly: Boolean = true): HardwareDescriptor {
        val props = Properties()
        source.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .filter { it.contains("=") }
            .forEach { line ->
                val (key, value) = line.split("=", limit = 2)
                val cleanKey = key.trim()
                val cleanValue = value.trim().removeSurrounding("\"", "\"")
                props.setProperty(cleanKey, cleanValue)
            }

        return HardwareDescriptor(
            id = props.getProperty("id") ?: error("DSL missing 'id'"),
            name = props.getProperty("name") ?: props.getProperty("id")!!,
            transport = Transport.valueOf(props.getProperty("transport") ?: "USB_SERIAL"),
            capabilities = parseCapabilities(props.getProperty("capabilities", "")),
            vendorId = props.getProperty("vendorId")?.toIntOrNull(16),
            productId = props.getProperty("productId")?.toIntOrNull(16),
            driverId = props.getProperty("driverId"),
            firmware = props.getProperty("firmware"),
            requiresRoot = props.getProperty("requiresRoot")?.toBoolean() ?: false,
            supportLevel = parseSupportLevel(props.getProperty("supportLevel")),
            safety = parseSafetyProfile(props),
        )
    }

    private fun parseCapabilities(raw: String): Set<HwCapability> {
        if (raw.isBlank()) return emptySet()
        return raw.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { capName ->
                try {
                    HwCapability.valueOf(capName)
                } catch (_: Exception) {
                    null
                }
            }
            .toSet()
    }

    private fun parseSupportLevel(raw: String?): com.droid.dolphy.core.model.SupportLevel =
        when (raw?.uppercase()) {
            "CERTIFIED" -> com.droid.dolphy.core.model.SupportLevel.CERTIFIED
            "BETA" -> com.droid.dolphy.core.model.SupportLevel.BETA
            "EXPERIMENTAL" -> com.droid.dolphy.core.model.SupportLevel.EXPERIMENTAL
            else -> com.droid.dolphy.core.model.SupportLevel.EXPERIMENTAL
        }

    private fun parseSafetyProfile(props: Properties): com.droid.dolphy.core.model.SafetyProfile =
        com.droid.dolphy.core.model.SafetyProfile(
            allowedFrequencies = emptyList(),
            maxTxPowerDbm = props.getProperty("maxTxPowerDbm")?.toIntOrNull(),
            maxDutyCycle = props.getProperty("maxDutyCycle")?.toDoubleOrNull() ?: 0.1,
            requiresLabMode = props.getProperty("requiresLabMode")?.toBoolean() ?: true,
            maxContinuousTxMillis = props.getProperty("maxContinuousTxMillis")?.toLongOrNull() ?: 10_000L,
        )
}
