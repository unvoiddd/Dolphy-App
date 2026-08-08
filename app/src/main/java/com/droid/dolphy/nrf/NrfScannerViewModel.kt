package com.droid.dolphy.nrf

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droid.dolphy.bluetooth.whisperpair.FastPairDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NrfScannerViewModel : ViewModel() {
    private val _devices = MutableStateFlow<List<NrfDevice>>(emptyList())
    val devices: StateFlow<List<NrfDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedFilter = MutableStateFlow<DeviceType?>(null)
    val selectedFilter: StateFlow<DeviceType?> = _selectedFilter.asStateFlow()

    private val _selectedDevice = MutableStateFlow<NrfDevice?>(null)
    val selectedDevice: StateFlow<NrfDevice?> = _selectedDevice.asStateFlow()

    private val deviceMap = mutableMapOf<String, NrfDevice>()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var context: Context? = null
    private var classicScanReceiver: BroadcastReceiver? = null
    private var currentGatt: BluetoothGatt? = null

    fun initialize(context: Context, bluetoothAdapter: BluetoothAdapter?) {
        this.context = context
        this.bluetoothAdapter = bluetoothAdapter
        this.bleScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true
        deviceMap.clear()
        _devices.value = emptyList()

        startBleScanning()
        startClassicScanning()
    }

    fun stopScanning() {
        _isScanning.value = false
        stopBleScanning()
        stopClassicScanning()
    }

    fun setFilter(type: DeviceType?) {
        _selectedFilter.value = type
        updateDevicesList()
    }

    fun profileDevice(device: NrfDevice) {
        viewModelScope.launch {
            try {
                val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address) ?: return@launch

                currentGatt?.close()
                currentGatt = bluetoothDevice.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            gatt.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            updateDeviceConnection(device.address, false)
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val services = gatt.services.map { service ->
                                val characteristics = service.characteristics.map { char ->
                                    GattCharacteristicInfo(
                                        uuid = char.uuid.toString(),
                                        name = getCharacteristicName(char.uuid.toString()),
                                        properties = getCharacteristicProperties(char)
                                    )
                                }
                                GattServiceInfo(
                                    uuid = service.uuid.toString(),
                                    name = getServiceName(service.uuid.toString()),
                                    characteristics = characteristics
                                )
                            }


                            val hasHID = services.any { it.uuid.contains("1812", ignoreCase = true) }
                            val hasAudio = services.any {
                                it.uuid.contains("110b", ignoreCase = true) ||
                                it.uuid.contains("110e", ignoreCase = true) ||
                                it.uuid.contains("110f", ignoreCase = true)
                            }
                            val hasBattery = services.any { it.uuid.contains("180f", ignoreCase = true) }


                            var batteryLevel: Int? = null
                            if (hasBattery) {
                                val batteryService = gatt.getService(java.util.UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"))
                                val batteryChar = batteryService?.getCharacteristic(java.util.UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"))
                                if (batteryChar != null && gatt.readCharacteristic(batteryChar)) {

                                }
                            }


                            var deviceInfo: DeviceInfo? = null
                            val deviceInfoService = gatt.getService(java.util.UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"))
                            if (deviceInfoService != null) {
                                deviceInfo = DeviceInfo()

                            }

                            updateDeviceProfile(
                                device.address,
                                services,
                                batteryLevel,
                                deviceInfo,
                                hasHID,
                                hasAudio,
                                hasBattery,
                                true
                            )
                        }
                    }

                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            when (characteristic.uuid.toString().lowercase()) {
                                "00002a19-0000-1000-8000-00805f9b34fb" -> {

                                    val batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                                    updateDeviceBattery(device.address, batteryLevel)
                                }
                            }
                        }
                    }
                })
            } catch (e: SecurityException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnectDevice(address: String) {
        currentGatt?.disconnect()
        currentGatt?.close()
        currentGatt = null
        updateDeviceConnection(address, false)
    }

    private fun updateDeviceConnection(address: String, connected: Boolean) {
        deviceMap[address]?.let { device ->
            deviceMap[address] = device.copy(isConnected = connected)
            updateDevicesList()
        }
    }

    private fun updateDeviceProfile(
        address: String,
        services: List<GattServiceInfo>,
        batteryLevel: Int?,
        deviceInfo: DeviceInfo?,
        hasHID: Boolean,
        hasAudio: Boolean,
        hasBattery: Boolean,
        connected: Boolean
    ) {
        deviceMap[address]?.let { device ->
            deviceMap[address] = device.copy(
                gattServices = services,
                batteryLevel = batteryLevel,
                deviceInfo = deviceInfo,
                hasHID = hasHID,
                hasAudio = hasAudio,
                hasBattery = hasBattery,
                isConnected = connected
            )
            updateDevicesList()
        }
    }

    private fun updateDeviceBattery(address: String, batteryLevel: Int) {
        deviceMap[address]?.let { device ->
            deviceMap[address] = device.copy(batteryLevel = batteryLevel)
            updateDevicesList()
        }
    }

    private fun startBleScanning() {
        viewModelScope.launch {
            try {
                bleScanner?.startScan(object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        try {
                            processBleResult(result)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        super.onScanFailed(errorCode)
                    }
                })
            } catch (e: SecurityException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopBleScanning() {
        try {
            bleScanner?.stopScan(object : ScanCallback() {})
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processBleResult(result: ScanResult) {
        val device = result.device
        val scanRecord = result.scanRecord
        val rssi = result.rssi

        val serviceUuids = scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList()
        val manufacturerData = scanRecord?.manufacturerSpecificData
        val manufacturerId = manufacturerData?.keyAt(0)

        val isFastPair = serviceUuids.contains("0000fe2c-0000-1000-8000-00805f9b34fb")
        val isWhisperPair = serviceUuids.any { it.contains("fe2c", ignoreCase = true) }
        val isAppleDevice = manufacturerId == 0x004C
        val isSamsungDevice = manufacturerId == 0x0075
        val isXiaomiDevice = manufacturerId == 0x0157

        val isVulnerable = isFastPair || isWhisperPair || isAppleDevice || isSamsungDevice || isXiaomiDevice
        val vulnerabilityType = when {
            isFastPair -> VulnerabilityType.FAST_PAIR_KBP
            isWhisperPair -> VulnerabilityType.WHISPER_PAIR
            isAppleDevice || isSamsungDevice || isXiaomiDevice -> VulnerabilityType.STANDARD_PAIRING
            else -> VulnerabilityType.NONE
        }

        val deviceType = classifyDeviceType(device, serviceUuids, manufacturerId, device.name)
        val manufacturerName = manufacturerId?.let { getManufacturerName(it) }


        val hasHID = serviceUuids.any { it.contains("1812", ignoreCase = true) }
        val hasAudio = serviceUuids.any {
            it.contains("110b", ignoreCase = true) ||
            it.contains("110e", ignoreCase = true) ||
            it.contains("110f", ignoreCase = true)
        }
        val hasBattery = serviceUuids.any { it.contains("180f", ignoreCase = true) }

        val existingDevice = deviceMap[device.address]
        if (existingDevice == null) {
            context?.let { ctx -> com.droid.dolphy.trackNrfDevices(ctx, 1) }
        }

        val nrfDevice = NrfDevice(
            address = device.address,
            name = device.name ?: "Unknown",
            type = deviceType,
            rssi = rssi,
            txPower = scanRecord?.txPowerLevel,
            manufacturerId = manufacturerId,
            manufacturerData = if (manufacturerId != null) manufacturerData?.get(manufacturerId)?.clone() else null,
            serviceUuids = serviceUuids,
            deviceClass = device.bluetoothClass?.deviceClass,
            isVulnerable = isVulnerable,
            vulnerabilityType = vulnerabilityType,
            lastSeen = System.currentTimeMillis(),
            scanCount = (existingDevice?.scanCount ?: 0) + 1,
            manufacturerName = manufacturerName,
            deviceTypeClass = getDeviceTypeLabel(deviceType),
            gattServices = existingDevice?.gattServices ?: emptyList(),
            batteryLevel = existingDevice?.batteryLevel,
            deviceInfo = existingDevice?.deviceInfo,
            isConnected = existingDevice?.isConnected ?: false,
            hasHID = hasHID,
            hasAudio = hasAudio,
            hasBattery = hasBattery
        )

        deviceMap[device.address] = nrfDevice
        updateDevicesList()
    }

    private fun startClassicScanning() {
        viewModelScope.launch {
            try {
                context?.let { ctx ->
                    classicScanReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            try {
                                if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                    }
                                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                                    device?.let {
                                        val nrfDevice = NrfDevice(
                                            address = it.address,
                                            name = it.name ?: "Unknown",
                                            type = DeviceType.CLASSIC_DEVICE,
                                            rssi = rssi,
                                            deviceClass = it.bluetoothClass?.deviceClass,
                                            isVulnerable = false,
                                            vulnerabilityType = VulnerabilityType.NONE,
                                            lastSeen = System.currentTimeMillis(),
                                            scanCount = (deviceMap[it.address]?.scanCount ?: 0) + 1,
                                            deviceTypeClass = "Classic"
                                        )

                                        if (!deviceMap.containsKey(it.address)) {
                                            deviceMap[it.address] = nrfDevice
                                            context?.let { ctx -> com.droid.dolphy.trackNrfDevices(ctx, 1) }
                                            updateDevicesList()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ctx.registerReceiver(classicScanReceiver, filter, Context.RECEIVER_EXPORTED)
                        } else {
                            @Suppress("DEPRECATION")
                            ctx.registerReceiver(classicScanReceiver, filter)
                        }

                        bluetoothAdapter?.startDiscovery()
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopClassicScanning() {
        try {
            bluetoothAdapter?.cancelDiscovery()
            context?.let { ctx ->
                classicScanReceiver?.let { receiver ->
                    try {
                        ctx.unregisterReceiver(receiver)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateDevicesList() {
        val filter = _selectedFilter.value
        val filtered = if (filter != null) {
            deviceMap.values.filter { it.type == filter }
        } else {
            deviceMap.values.toList()
        }

        _devices.value = filtered.sortedByDescending { it.rssi }
    }

    override fun onCleared() {
        stopScanning()
        currentGatt?.close()
        super.onCleared()
    }
}

