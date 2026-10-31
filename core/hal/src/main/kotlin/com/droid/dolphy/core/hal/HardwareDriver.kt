package com.droid.dolphy.core.hal

import com.droid.dolphy.core.model.HardwareDescriptor
import com.droid.dolphy.core.model.HwCapability
import com.droid.dolphy.core.model.Transport

sealed interface HwCommand {
    val capability: HwCapability?

    data class Transmit(
        override val capability: HwCapability,
        val frequencyHz: Long?,
        val payload: ByteArray,
        val modulation: String? = null,
        val repeat: Int = 1,
    ) : HwCommand

    data class Receive(
        override val capability: HwCapability,
        val timeoutMillis: Long = 5_000,
    ) : HwCommand

    data class Configure(
        val values: Map<String, String>,
    ) : HwCommand {
        override val capability: HwCapability? = null
    }

    data class Raw(
        val opcode: String,
        val payload: ByteArray = ByteArray(0),
    ) : HwCommand {
        override val capability: HwCapability? = null
    }
}

sealed interface HwResult {
    data class Success(
        val payload: ByteArray = ByteArray(0),
        val message: String? = null,
    ) : HwResult

    data class Failure(
        val reason: String,
        val cause: Throwable? = null,
    ) : HwResult
}

/**
 * A backend for one family of transports. Drivers must be stateless with
 * respect to features: they only know how to probe, open, execute and close.
 */
interface HardwareDriver {
    val driverId: String
    val supportedTransports: Set<Transport>

    suspend fun probe(descriptor: HardwareDescriptor): Boolean

    suspend fun open(descriptor: HardwareDescriptor)

    suspend fun execute(command: HwCommand): HwResult

    suspend fun close()
}
