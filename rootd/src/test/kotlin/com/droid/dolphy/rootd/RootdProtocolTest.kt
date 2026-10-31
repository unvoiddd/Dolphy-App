package com.droid.dolphy.rootd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootdProtocolTest {

    @Test
    fun buildsOnlyFixedOperations() {
        assertEquals("id", RootdProtocol.commandFor(RootOperation.ID))
        assertEquals(
            "ip link set dev wlan0 up",
            RootdProtocol.commandFor(
                RootOperation.IFACE_STATE,
                mapOf("iface" to "wlan0", "state" to "up"),
            ),
        )
        assertEquals(
            "settings get global airplane_mode_on",
            RootdProtocol.commandFor(
                RootOperation.SETTING_GET,
                mapOf("namespace" to "global", "key" to "airplane_mode_on"),
            ),
        )
    }

    @Test
    fun rejectsShellInjectionInArguments() {
        val invalid = listOf(
            "wlan0; id",
            "wlan0 && reboot",
            "wlan0`id`",
            "wlan0|sh",
            "wlan0\nid",
            "wlan0$(id)",
        )
        invalid.forEach { value ->
            val rejected = runCatching {
                RootdProtocol.commandFor(
                    RootOperation.IFACE_STATE,
                    mapOf("iface" to value, "state" to "up"),
                )
            }.isFailure
            assertTrue("should reject: $value", rejected)
        }
    }

    @Test
    fun rejectsUnknownSettingNamespaceAndMissingArgs() {
        assertFalse(
            runCatching {
                RootdProtocol.commandFor(
                    RootOperation.SETTING_GET,
                    mapOf("namespace" to "vendor", "key" to "x"),
                )
            }.isSuccess,
        )
        assertFalse(runCatching { RootdProtocol.commandFor(RootOperation.IFACE_STATE) }.isSuccess)
    }

    @Test
    fun everyOperationHasCapability() {
        RootOperation.entries.forEach { operation ->
            assertTrue(operation.requiredCapability in RootCapability.entries)
            assertTrue(RootdProtocol.commandFor(operation, defaultArgs(operation)).isNotBlank())
        }
    }

    private fun defaultArgs(operation: RootOperation): Map<String, String> = when (operation) {
        RootOperation.IFACE_STATE -> mapOf("iface" to "wlan0", "state" to "up")
        RootOperation.GETPROP -> mapOf("key" to "ro.build.version.sdk")
        RootOperation.SETTING_GET -> mapOf("namespace" to "global", "key" to "adb_enabled")
        else -> emptyMap()
    }
}
