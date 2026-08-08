package com.droid.dolphy.bluetooth.whisperpair

data class FastPairDevice(
    val name: String?,
    val address: String,
    val isPairingMode: Boolean,
    val hasAccountKeyFilter: Boolean,
    val modelId: String? = null,
    val rssi: Int = -100,
    val lastSeen: Long = System.currentTimeMillis(),
    var status: DeviceStatus = DeviceStatus.NOT_TESTED,
    val isFastPair: Boolean = true
) {
    val displayName: String
        get() = name ?: KnownDevices.getDeviceName(modelId) ?: if (isFastPair) "Unknown Fast Pair Device" else "BLE Device"

    val manufacturer: String?
        get() = KnownDevices.getManufacturer(modelId)

    val isKnownVulnerable: Boolean
        get() = KnownDevices.isKnownVulnerable(modelId)

    val signalStrength: SignalStrength
        get() = when {
            rssi >= -50 -> SignalStrength.EXCELLENT
            rssi >= -60 -> SignalStrength.GOOD
            rssi >= -70 -> SignalStrength.FAIR
            rssi >= -80 -> SignalStrength.WEAK
            else -> SignalStrength.VERY_WEAK
        }
}

enum class DeviceStatus {
    NOT_TESTED,
    TESTING,
    VULNERABLE,
    PATCHED,
    ERROR
}

enum class SignalStrength {
    EXCELLENT,
    GOOD,
    FAIR,
    WEAK,
    VERY_WEAK
}

object KnownDevices {
    data class DeviceInfo(
        val name: String,
        val manufacturer: String,
        val knownVulnerable: Boolean = true
    )

    private val devices = mapOf(
        "30018E" to DeviceInfo("Pixel Buds Pro 2", "Google"),
        "CD8256" to DeviceInfo("WF-1000XM4", "Sony"),
        "0E30C3" to DeviceInfo("WH-1000XM5", "Sony"),
        "D5BC6B" to DeviceInfo("WH-1000XM6", "Sony"),
        "821F66" to DeviceInfo("LinkBuds S", "Sony"),
        "F52494" to DeviceInfo("Tune Buds", "JBL"),
        "718FA4" to DeviceInfo("Live Pro 2", "JBL"),
        "D446A7" to DeviceInfo("Tune Beam", "JBL"),
        "9D3F8A" to DeviceInfo("Soundcore Liberty 4", "Anker"),
        "F0B77F" to DeviceInfo("Soundcore Liberty 4 NC", "Anker"),
        "D0A72C" to DeviceInfo("Ear (a)", "Nothing"),
        "D97EBA" to DeviceInfo("Nord Buds 3 Pro", "OnePlus"),
        "AE3989" to DeviceInfo("Redmi Buds 5 Pro", "Xiaomi"),
        "D446F9" to DeviceInfo("Elite 8 Active", "Jabra"),
        "0082DA" to DeviceInfo("Galaxy Buds2 Pro", "Samsung", knownVulnerable = false),
        "00FA72" to DeviceInfo("Galaxy Buds FE", "Samsung", knownVulnerable = false),
        "F00002" to DeviceInfo("QuietComfort Earbuds II", "Bose"),
        "000006" to DeviceInfo("Beats Studio Buds +", "Beats"),
    )

    fun getDeviceInfo(modelId: String?): DeviceInfo? {
        if (modelId == null) return null
        return devices[modelId.uppercase()]
    }

    fun getDeviceName(modelId: String?): String? = getDeviceInfo(modelId)?.name
    fun getManufacturer(modelId: String?): String? = getDeviceInfo(modelId)?.manufacturer
    fun isKnownVulnerable(modelId: String?): Boolean = getDeviceInfo(modelId)?.knownVulnerable ?: false
}


