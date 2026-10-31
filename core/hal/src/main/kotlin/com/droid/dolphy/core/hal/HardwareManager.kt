package com.droid.dolphy.core.hal

import com.droid.dolphy.core.model.DeviceState
import com.droid.dolphy.core.model.HardwareDescriptor
import com.droid.dolphy.core.model.LabSession
import java.util.concurrent.ConcurrentHashMap

/**
 * Facade used by features. It owns the registry, driver lookup, device leases
 * and the safety gate.
 */
class HardwareManager(
    val registry: DeviceRegistry = DeviceRegistry(),
    val leases: DeviceLeaseManager = DeviceLeaseManager(),
) {
    val capabilities = CapabilityIndex(registry)

    private val drivers = ConcurrentHashMap<String, HardwareDriver>()

    fun registerDriver(driver: HardwareDriver) {
        drivers[driver.driverId] = driver
    }

    fun unregisterDriver(driverId: String) {
        drivers.remove(driverId)
    }

    suspend fun attach(descriptor: HardwareDescriptor): HwResult {
        val driver = driverFor(descriptor)
            ?: return HwResult.Failure("no_driver_for_transport:${descriptor.transport}")

        return try {
            registry.upsert(descriptor, DeviceState.PROBING)
            if (!driver.probe(descriptor)) {
                registry.updateState(descriptor.id, DeviceState.ERROR, error = "probe_failed")
                return HwResult.Failure("probe_failed")
            }
            driver.open(descriptor)
            registry.upsert(descriptor, DeviceState.READY)
            HwResult.Success(message = "attached")
        } catch (error: Throwable) {
            registry.updateState(
                descriptor.id,
                DeviceState.ERROR,
                error = error.message ?: error.javaClass.simpleName,
            )
            HwResult.Failure("attach_failed", error)
        }
    }

    suspend fun execute(
        deviceId: String,
        command: HwCommand,
        owner: String,
        labSession: LabSession? = null,
        ttlMillis: Long = DEFAULT_LEASE_MILLIS,
    ): HwResult {
        val snapshot = registry.get(deviceId)
            ?: return HwResult.Failure("device_not_found")
        if (snapshot.state != DeviceState.READY) {
            return HwResult.Failure("device_not_ready:${snapshot.state}")
        }
        val descriptor = snapshot.descriptor
        val driver = driverFor(descriptor)
            ?: return HwResult.Failure("no_driver_for_transport:${descriptor.transport}")

        checkSafety(descriptor, command, labSession)?.let { return HwResult.Failure(it) }

        return try {
            leases.withLease(deviceId, owner, ttlMillis) {
                registry.updateState(deviceId, DeviceState.BUSY)
                val result = driver.execute(command)
                registry.updateState(
                    deviceId,
                    if (result is HwResult.Failure) DeviceState.ERROR else DeviceState.READY,
                    error = (result as? HwResult.Failure)?.reason,
                )
                result
            }
        } catch (busy: DeviceBusyException) {
            HwResult.Failure(busy.message ?: "device_busy", busy)
        } catch (error: Throwable) {
            registry.updateState(
                deviceId,
                DeviceState.ERROR,
                error = error.message ?: error.javaClass.simpleName,
            )
            HwResult.Failure("execute_failed", error)
        }
    }

    suspend fun detach(deviceId: String): Boolean {
        val snapshot = registry.get(deviceId) ?: return false
        runCatching { driverFor(snapshot.descriptor)?.close() }
        registry.remove(deviceId)
        return true
    }

    suspend fun closeAll() {
        drivers.values.forEach { driver ->
            runCatching { driver.close() }
        }
        registry.all().forEach { registry.remove(it.descriptor.id) }
    }

    fun checkSafety(
        descriptor: HardwareDescriptor,
        command: HwCommand,
        labSession: LabSession?,
    ): String? {
        if (command is HwCommand.Transmit) {
            if (!descriptor.supports(command.capability)) {
                return "capability_not_supported:${command.capability}"
            }
            if (!descriptor.safety.allowsFrequency(command.frequencyHz)) {
                return "frequency_not_allowed"
            }
            if (command.repeat !in 1..MAX_REPEAT) return "invalid_repeat"
        }
        if (command is HwCommand.Receive && command.timeoutMillis !in 1..MAX_RECEIVE_MILLIS) {
            return "invalid_timeout"
        }
        if (descriptor.safety.requiresLabMode && labSession?.isActive() != true) {
            return "lab_mode_required"
        }
        return null
    }

    private fun driverFor(descriptor: HardwareDescriptor): HardwareDriver? {
        descriptor.driverId?.let { id -> drivers[id]?.let { return it } }
        return drivers.values.firstOrNull { descriptor.transport in it.supportedTransports }
    }

    companion object {
        const val DEFAULT_LEASE_MILLIS = 30_000L
        private const val MAX_REPEAT = 1_000
        private const val MAX_RECEIVE_MILLIS = 120_000L
    }
}
