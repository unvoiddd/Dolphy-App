package com.droid.dolphy.core.hal

import com.droid.dolphy.core.model.DeviceSnapshot
import com.droid.dolphy.core.model.DeviceState
import com.droid.dolphy.core.model.HardwareDescriptor
import com.droid.dolphy.core.model.HwCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory inventory of every known device (internal, attached, network or
 * remembered). Thread-safe and observable by the UI.
 */
class DeviceRegistry {

    private val lock = Any()
    private val devices = LinkedHashMap<String, DeviceSnapshot>()
    private val _snapshots = MutableStateFlow<List<DeviceSnapshot>>(emptyList())
    val snapshots: StateFlow<List<DeviceSnapshot>> = _snapshots.asStateFlow()

    fun upsert(
        descriptor: HardwareDescriptor,
        state: DeviceState = DeviceState.READY,
        now: Long = System.currentTimeMillis(),
        error: String? = null,
    ) {
        synchronized(lock) {
            devices[descriptor.id] = DeviceSnapshot(descriptor, state, now, error)
            publishLocked()
        }
    }

    fun updateState(
        deviceId: String,
        state: DeviceState,
        error: String? = null,
        now: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        val current = devices[deviceId] ?: return false
        devices[deviceId] = current.copy(state = state, lastSeenEpochMs = now, error = error)
        publishLocked()
        true
    }

    fun remove(deviceId: String): Boolean = synchronized(lock) {
        val removed = devices.remove(deviceId) != null
        if (removed) publishLocked()
        removed
    }

    fun get(deviceId: String): DeviceSnapshot? = synchronized(lock) { devices[deviceId] }

    fun all(): List<DeviceSnapshot> = synchronized(lock) { devices.values.toList() }

    fun readyWith(capability: HwCapability): List<HardwareDescriptor> = synchronized(lock) {
        devices.values
            .filter { it.state == DeviceState.READY && it.descriptor.supports(capability) }
            .map { it.descriptor }
    }

    private fun publishLocked() {
        _snapshots.value = devices.values.toList()
    }
}
