package com.droid.dolphy.core.hal

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeviceLeaseManagerTest {

    @Test
    fun grantsExclusiveLeaseAndRejectsSecondOwner() = runTest {
        val manager = DeviceLeaseManager()
        manager.acquire("flipper", "owner-a", 10_000)

        try {
            manager.acquire("flipper", "owner-b", 10_000)
            fail("Expected DeviceBusyException")
        } catch (_: DeviceBusyException) {
        }
        assertEquals("owner-a", manager.activeLeases().getValue("flipper").owner)
    }

    @Test
    fun sameOwnerCanRenewAndRelease() = runTest {
        val manager = DeviceLeaseManager()
        manager.acquire("flipper", "owner-a", 10_000)
        manager.acquire("flipper", "owner-a", 20_000)

        assertTrue(manager.release("flipper", "owner-a"))
        assertFalse(manager.release("flipper", "owner-a"))
        assertTrue(manager.activeLeases().isEmpty())
    }

    @Test
    fun expiredLeaseIsReclaimable() = runTest {
        var now = 1_000L
        val manager = DeviceLeaseManager(clock = { now })
        manager.acquire("flipper", "owner-a", 500)

        now = 1_600L
        manager.acquire("flipper", "owner-b", 500)
        assertEquals("owner-b", manager.activeLeases().getValue("flipper").owner)
    }
}
