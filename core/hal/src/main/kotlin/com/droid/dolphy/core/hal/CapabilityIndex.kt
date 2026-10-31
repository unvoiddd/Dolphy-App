package com.droid.dolphy.core.hal

import com.droid.dolphy.core.model.HardwareDescriptor
import com.droid.dolphy.core.model.HwCapability
import com.droid.dolphy.core.model.SupportLevel
import com.droid.dolphy.core.model.Transport

/**
 * Answers "what is the best backend for capability X right now?" without
 * exposing device models to features.
 */
class CapabilityIndex(private val registry: DeviceRegistry) {

    fun available(capability: HwCapability): List<HardwareDescriptor> =
        registry.readyWith(capability)

    fun supports(capability: HwCapability): Boolean = available(capability).isNotEmpty()

    fun best(
        capability: HwCapability,
        preferredTransport: Transport? = null,
    ): HardwareDescriptor? {
        val candidates = available(capability)
        if (candidates.isEmpty()) return null
        return candidates.sortedWith(
            compareByDescending<HardwareDescriptor> {
                preferredTransport != null && it.transport == preferredTransport
            }.thenByDescending { supportRank(it.supportLevel) }
                .thenBy { it.name.lowercase() },
        ).first()
    }

    private fun supportRank(level: SupportLevel): Int = when (level) {
        SupportLevel.CERTIFIED -> 4
        SupportLevel.BETA -> 3
        SupportLevel.EXPERIMENTAL -> 2
        SupportLevel.UNSUPPORTED -> 1
    }
}
