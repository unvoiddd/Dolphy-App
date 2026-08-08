package com.droid.dolphy.nrf

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService

enum class DeviceType {
    BLE_DEVICE,
    CLASSIC_DEVICE,
    BEACON,
    FAST_PAIR,
    DUAL_MODE
}

enum class VulnerabilityType {
    FAST_PAIR_KBP,
    WHISPER_PAIR,
    STANDARD_PAIRING,
    NONE
}

data class GattServiceInfo(
    val uuid: String,
    val name: String,
    val characteristics: List<GattCharacteristicInfo> = emptyList()
)

data class GattCharacteristicInfo(
    val uuid: String,
    val name: String,
    val properties: List<String> = emptyList(),
    val value: String? = null
)

data class NrfDevice(
    val address: String,
    val name: String?,
    val type: DeviceType,
    val rssi: Int,
    val txPower: Int? = null,
    val manufacturerId: Int? = null,
    val manufacturerData: ByteArray? = null,
    val serviceUuids: List<String> = emptyList(),
    val deviceClass: Int? = null,
    val isVulnerable: Boolean = false,
    val vulnerabilityType: VulnerabilityType? = null,
    val lastSeen: Long = System.currentTimeMillis(),
    val scanCount: Int = 1,
    val manufacturerName: String? = null,
    val deviceTypeClass: String? = null,
    val gattServices: List<GattServiceInfo> = emptyList(),
    val batteryLevel: Int? = null,
    val deviceInfo: DeviceInfo? = null,
    val isConnected: Boolean = false,
    val hasHID: Boolean = false,
    val hasAudio: Boolean = false,
    val hasBattery: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NrfDevice
        return address == other.address
    }

    override fun hashCode(): Int = address.hashCode()
}

data class DeviceInfo(
    val manufacturer: String? = null,
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val hardwareRevision: String? = null,
    val firmwareRevision: String? = null,
    val softwareRevision: String? = null
)

fun getManufacturerName(manufacturerId: Int): String? = when (manufacturerId) {
    0x004C -> "Apple"
    0x0075 -> "Samsung"
    0x0006 -> "Microsoft"
    0x038F -> "Xiaomi"
    0x0157 -> "Xiaomi"
    0xfe2c -> "Google"
    0x0059 -> "Sony"
    0x0105 -> "JBL"
    0x00E0 -> "Google"
    0x0087 -> "Garmin"
    0x0131 -> "Bose"
    0x0220 -> "Beats"
    else -> null
}

fun classifyDeviceType(
    bluetoothDevice: BluetoothDevice?,
    serviceUuids: List<String>,
    manufacturerId: Int?,
    name: String?
): DeviceType {
    val isFastPair = serviceUuids.contains("0000fe2c-0000-1000-8000-00805f9b34fb")
    val isBeacon = manufacturerId == 0x004C || manufacturerId == 0x0075 || manufacturerId == 0x0006

    return when {
        isFastPair -> DeviceType.FAST_PAIR
        isBeacon -> DeviceType.BEACON
        bluetoothDevice?.type == BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.DUAL_MODE
        bluetoothDevice?.type == BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.BLE_DEVICE
        bluetoothDevice?.type == BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.CLASSIC_DEVICE
        else -> DeviceType.BLE_DEVICE
    }
}

fun getDeviceTypeColor(type: DeviceType): String = when (type) {
    DeviceType.BLE_DEVICE -> "Blue"
    DeviceType.CLASSIC_DEVICE -> "Purple"
    DeviceType.BEACON -> "Green"
    DeviceType.FAST_PAIR -> "Orange"
    DeviceType.DUAL_MODE -> "Red"
}

fun getDeviceTypeLabel(type: DeviceType): String = when (type) {
    DeviceType.BLE_DEVICE -> "BLE"
    DeviceType.CLASSIC_DEVICE -> "Classic"
    DeviceType.BEACON -> "Beacon"
    DeviceType.FAST_PAIR -> "Fast Pair"
    DeviceType.DUAL_MODE -> "Dual Mode"
}

fun getSignalStrength(rssi: Int): String = when {
    rssi >= -50 -> "Excellent"
    rssi >= -60 -> "Good"
    rssi >= -70 -> "Fair"
    rssi >= -80 -> "Weak"
    else -> "Very Weak"
}

fun getSignalStrengthPercent(rssi: Int): Float = when {
    rssi >= -50 -> 1f
    rssi >= -60 -> 0.8f
    rssi >= -70 -> 0.6f
    rssi >= -80 -> 0.4f
    else -> 0.2f
}

fun getServiceName(uuid: String): String = when (uuid.lowercase()) {
    "0000180f-0000-1000-8000-00805f9b34fb" -> "Battery Service"
    "0000180a-0000-1000-8000-00805f9b34fb" -> "Device Information"
    "00001812-0000-1000-8000-00805f9b34fb" -> "HID Service"
    "0000110b-0000-1000-8000-00805f9b34fb" -> "Audio Sink"
    "0000110e-0000-1000-8000-00805f9b34fb" -> "A/V Remote Control"
    "0000110f-0000-1000-8000-00805f9b34fb" -> "A/V Remote Control Target"
    "0000111e-0000-1000-8000-00805f9b34fb" -> "Hands-Free"
    "0000111f-0000-1000-8000-00805f9b34fb" -> "Hands-Free Audio Gateway"
    "0000fe2c-0000-1000-8000-00805f9b34fb" -> "Google Fast Pair"
    "00001800-0000-1000-8000-00805f9b34fb" -> "Generic Access"
    "00001801-0000-1000-8000-00805f9b34fb" -> "Generic Attribute"
    "0000180d-0000-1000-8000-00805f9b34fb" -> "Heart Rate"
    "00001816-0000-1000-8000-00805f9b34fb" -> "Cycling Speed and Cadence"
    "00001818-0000-1000-8000-00805f9b34fb" -> "Cycling Power"
    else -> uuid
}

fun getCharacteristicName(uuid: String): String = when (uuid.lowercase()) {
    "00002a19-0000-1000-8000-00805f9b34fb" -> "Battery Level"
    "00002a29-0000-1000-8000-00805f9b34fb" -> "Manufacturer Name"
    "00002a24-0000-1000-8000-00805f9b34fb" -> "Model Number"
    "00002a25-0000-1000-8000-00805f9b34fb" -> "Serial Number"
    "00002a27-0000-1000-8000-00805f9b34fb" -> "Hardware Revision"
    "00002a26-0000-1000-8000-00805f9b34fb" -> "Firmware Revision"
    "00002a28-0000-1000-8000-00805f9b34fb" -> "Software Revision"
    "00002a00-0000-1000-8000-00805f9b34fb" -> "Device Name"
    "00002a01-0000-1000-8000-00805f9b34fb" -> "Appearance"
    "00002a37-0000-1000-8000-00805f9b34fb" -> "Heart Rate Measurement"
    else -> uuid
}

fun getCharacteristicProperties(characteristic: BluetoothGattCharacteristic): List<String> {
    val properties = mutableListOf<String>()
    val prop = characteristic.properties

    if (prop and BluetoothGattCharacteristic.PROPERTY_READ != 0) properties.add("Read")
    if (prop and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) properties.add("Write")
    if (prop and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) properties.add("Write No Response")
    if (prop and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) properties.add("Notify")
    if (prop and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) properties.add("Indicate")

    return properties
}

