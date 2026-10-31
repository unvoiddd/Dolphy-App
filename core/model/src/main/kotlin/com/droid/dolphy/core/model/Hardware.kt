package com.droid.dolphy.core.model

/**
 * Physical/logical transport used by a hardware backend.
 *
 * Features must never branch on a concrete device model. They request a
 * [HwCapability] and let the HAL pick an available transport.
 */
enum class Transport {
    INTERNAL,
    USB,
    USB_SERIAL,
    USB_GADGET,
    BLE,
    NETWORK,
    ROOT_NATIVE,
    SDR,
}

enum class HwCapability {
    IR_TX,
    IR_RX,
    NFC_READ,
    NFC_WRITE,
    NFC_EMULATE,
    RFID125_READ,
    RFID125_WRITE,
    IBUTTON,
    SUBGHZ_RX,
    SUBGHZ_TX,
    BLE_SCAN,
    BLE_ADVERTISE,
    BLE_GATT,
    HID,
    STORAGE,
    AUDIO,
    CAMERA,
    PRINTER,
    GPIO,
    I2C,
    SPI,
    UART,
    JTAG,
    SWD,
    PCAP,
    MONITOR,
    CAN,
    LOGIC_ANALYZE,
}

enum class SupportLevel {
    CERTIFIED,
    BETA,
    EXPERIMENTAL,
    UNSUPPORTED,
}

enum class DeviceState {
    DETECTED,
    PROBING,
    READY,
    BUSY,
    DISCONNECTED,
    ERROR,
    RECOVERING,
}

data class FrequencyRange(val minHz: Long, val maxHz: Long) {
    init {
        require(minHz > 0) { "minHz must be positive" }
        require(maxHz >= minHz) { "maxHz must be >= minHz" }
    }

    fun contains(hz: Long): Boolean = hz in minHz..maxHz
}

/**
 * Hard limits enforced before any transmission reaches a driver.
 */
data class SafetyProfile(
    val allowedFrequencies: List<FrequencyRange> = emptyList(),
    val maxTxPowerDbm: Int? = null,
    val maxDutyCycle: Double = 0.1,
    val requiresLabMode: Boolean = true,
    val maxContinuousTxMillis: Long = 10_000,
) {
    init {
        require(maxDutyCycle in 0.0..1.0) { "maxDutyCycle must be in 0..1" }
        require(maxContinuousTxMillis > 0) { "maxContinuousTxMillis must be positive" }
    }

    fun allowsFrequency(hz: Long?): Boolean {
        if (hz == null) return allowedFrequencies.isEmpty()
        return allowedFrequencies.isEmpty() || allowedFrequencies.any { it.contains(hz) }
    }
}

data class HardwareDescriptor(
    val id: String,
    val name: String,
    val transport: Transport,
    val capabilities: Set<HwCapability>,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val firmware: String? = null,
    val driverId: String? = null,
    val requiresRoot: Boolean = false,
    val supportLevel: SupportLevel = SupportLevel.EXPERIMENTAL,
    val safety: SafetyProfile = SafetyProfile(),
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
    }

    fun supports(capability: HwCapability): Boolean = capability in capabilities
}

data class DeviceSnapshot(
    val descriptor: HardwareDescriptor,
    val state: DeviceState,
    val lastSeenEpochMs: Long,
    val error: String? = null,
) {
    val isReady: Boolean get() = state == DeviceState.READY
}
