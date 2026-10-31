package com.droid.dolphy.core.hal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Grants exclusive, time-boxed access to a device.
 *
 * A crashed caller must never leave a device stuck in BUSY: leases expire and
 * the next acquisition reclaims them.
 */
class DeviceLeaseManager(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Lease(
        val deviceId: String,
        val owner: String,
        val expiresAtEpochMs: Long,
    )

    private data class Held(val owner: String, val expiresAtEpochMs: Long)

    private val mutex = Mutex()
    private val held = mutableMapOf<String, Held>()

    suspend fun acquire(deviceId: String, owner: String, ttlMillis: Long): Lease {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(owner.isNotBlank()) { "owner must not be blank" }
        require(ttlMillis > 0) { "ttlMillis must be positive" }

        return mutex.withLock {
            val now = clock()
            val current = held[deviceId]
            if (current != null && current.expiresAtEpochMs > now && current.owner != owner) {
                throw DeviceBusyException(deviceId, current.owner)
            }
            val expiresAt = now + ttlMillis
            held[deviceId] = Held(owner, expiresAt)
            Lease(deviceId, owner, expiresAt)
        }
    }

    suspend fun release(deviceId: String, owner: String): Boolean = mutex.withLock {
        val current = held[deviceId] ?: return@withLock false
        if (current.owner != owner) return@withLock false
        held.remove(deviceId)
        true
    }

    suspend fun <T> withLease(
        deviceId: String,
        owner: String,
        ttlMillis: Long,
        block: suspend (Lease) -> T,
    ): T {
        val lease = acquire(deviceId, owner, ttlMillis)
        return try {
            block(lease)
        } finally {
            release(deviceId, owner)
        }
    }

    suspend fun activeLeases(): Map<String, Lease> = mutex.withLock {
        val now = clock()
        held.entries
            .filter { it.value.expiresAtEpochMs > now }
            .associate { (deviceId, value) ->
                deviceId to Lease(deviceId, value.owner, value.expiresAtEpochMs)
            }
    }
}

class DeviceBusyException(
    val deviceId: String,
    val currentOwner: String,
) : IllegalStateException("Device $deviceId is busy (owner=$currentOwner)")
