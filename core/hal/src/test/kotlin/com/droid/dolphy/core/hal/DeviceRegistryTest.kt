package com.droid.dolphy.core.hal

import com.droid.dolphy.core.model.DeviceState
import com.droid.dolphy.core.model.HardwareDescriptor
import com.droid.dolphy.core.model.HwCapability
import com.droid.dolphy.core.model.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRegistryTest {

    private val ir = HardwareDescriptor(
        id = "phone-ir",
        name = "Phone IR",
        transport = Transport.INTERNAL,
        capabilities = setOf(HwCapability.IR_TX),
    )

    @Test
    fun registersAndFiltersReadyDevices() {
        val registry = DeviceRegistry()
        registry.upsert(ir, DeviceState.READY)

        assertEquals(1, registry.readyWith(HwCapability.IR_TX).size)
        assertTrue(registry.readyWith(HwCapability.IR_RX).isEmpty())
        assertNull(registry.get("missing"))
    }

    @Test
    fun stateTransitionsAreVisibleInSnapshots() {
        val registry = DeviceRegistry()
        registry.upsert(ir, DeviceState.DETECTED)
        assertEquals(DeviceState.DETECTED, registry.snapshots.value.single().state)

        assertTrue(registry.updateState("phone-ir", DeviceState.READY))
        assertEquals(DeviceState.READY, registry.snapshots.value.single().state)
        assertEquals(1, registry.readyWith(HwCapability.IR_TX).size)
    }

    @Test
    fun removedDeviceIsNoLongerAvailable() {
        val registry = DeviceRegistry()
        registry.upsert(ir)
        assertTrue(registry.remove("phone-ir"))
        assertFalse(registry.remove("phone-ir"))
        assertTrue(registry.all().isEmpty())
    }
}
