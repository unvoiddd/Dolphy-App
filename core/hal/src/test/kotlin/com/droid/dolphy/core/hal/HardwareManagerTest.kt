package com.droid.dolphy.core.hal

import com.droid.dolphy.core.model.FrequencyRange
import com.droid.dolphy.core.model.HardwareDescriptor
import com.droid.dolphy.core.model.HwCapability
import com.droid.dolphy.core.model.LabSession
import com.droid.dolphy.core.model.SafetyProfile
import com.droid.dolphy.core.model.Transport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareManagerTest {

    private class FakeDriver : HardwareDriver {
        override val driverId = "fake"
        override val supportedTransports = setOf(Transport.USB)
        var lastCommand: HwCommand? = null

        override suspend fun probe(descriptor: HardwareDescriptor) = true
        override suspend fun open(descriptor: HardwareDescriptor) = Unit
        override suspend fun execute(command: HwCommand): HwResult {
            lastCommand = command
            return HwResult.Success()
        }

        override suspend fun close() = Unit
    }

    private fun descriptor(safety: SafetyProfile) = HardwareDescriptor(
        id = "fake-subghz",
        name = "Fake Sub-GHz",
        transport = Transport.USB,
        capabilities = setOf(HwCapability.SUBGHZ_TX),
        driverId = "fake",
        safety = safety,
    )

    private fun activeLabSession() = LabSession(
        id = "lab-1",
        title = "test",
        startedAtEpochMs = System.currentTimeMillis() - 1_000,
        expiresAtEpochMs = System.currentTimeMillis() + 60_000,
        authorizedByUser = true,
    )

    @Test
    fun transmitsInsideAllowedBandWithActiveLabSession() = runTest {
        val manager = HardwareManager()
        val driver = FakeDriver()
        manager.registerDriver(driver)
        val device = descriptor(
            SafetyProfile(
                allowedFrequencies = listOf(FrequencyRange(300_000_000, 928_000_000)),
                requiresLabMode = true,
            ),
        )
        assertTrue(manager.attach(device) is HwResult.Success)

        val result = manager.execute(
            deviceId = device.id,
            command = HwCommand.Transmit(
                capability = HwCapability.SUBGHZ_TX,
                frequencyHz = 433_920_000,
                payload = byteArrayOf(1, 2, 3),
            ),
            owner = "test",
            labSession = activeLabSession(),
        )

        assertTrue(result is HwResult.Success)
        assertTrue(driver.lastCommand is HwCommand.Transmit)
    }

    @Test
    fun blocksTransmissionOutsideAllowedBand() = runTest {
        val manager = HardwareManager()
        manager.registerDriver(FakeDriver())
        val device = descriptor(
            SafetyProfile(
                allowedFrequencies = listOf(FrequencyRange(300_000_000, 928_000_000)),
                requiresLabMode = false,
            ),
        )
        manager.attach(device)

        val result = manager.execute(
            deviceId = device.id,
            command = HwCommand.Transmit(
                capability = HwCapability.SUBGHZ_TX,
                frequencyHz = 2_400_000_000,
                payload = byteArrayOf(1),
            ),
            owner = "test",
        )

        assertEquals("frequency_not_allowed", (result as HwResult.Failure).reason)
    }

    @Test
    fun blocksTransmissionWithoutLabSession() = runTest {
        val manager = HardwareManager()
        manager.registerDriver(FakeDriver())
        val device = descriptor(SafetyProfile(requiresLabMode = true))
        manager.attach(device)

        val result = manager.execute(
            deviceId = device.id,
            command = HwCommand.Transmit(
                capability = HwCapability.SUBGHZ_TX,
                frequencyHz = 433_920_000,
                payload = byteArrayOf(1),
            ),
            owner = "test",
        )

        assertEquals("lab_mode_required", (result as HwResult.Failure).reason)
    }
}
