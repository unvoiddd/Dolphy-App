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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val VENDOR_OUI_ASSET = "ble/nmap-mac-prefixes.txt"

enum class NrfSortMode { RSSI, NAME }

@android.annotation.SuppressLint("MissingPermission")
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
    private var scanCallback: ScanCallback? = null
    private val _scanError = MutableStateFlow<Int?>(null)
    val scanError = _scanError.asStateFlow()

    private val _namedOnly = MutableStateFlow(false)
    val isNamedOnly: StateFlow<Boolean> = _namedOnly.asStateFlow()

    private val _knownVendorsOnly = MutableStateFlow(false)
    val isKnownVendorsOnly: StateFlow<Boolean> = _knownVendorsOnly.asStateFlow()

    private val _sortMode = MutableStateFlow(NrfSortMode.RSSI)
    val sortMode: StateFlow<NrfSortMode> = _sortMode.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow<Boolean?>(null)
    val isBluetoothEnabled: StateFlow<Boolean?> = _isBluetoothEnabled.asStateFlow()

    @Volatile
    private var vendorTable: Map<String, String>? = null
    private var vendorTableRequested = false
    private var bluetoothStateReceiver: BroadcastReceiver? = null

    fun initialize(context: Context, bluetoothAdapter: BluetoothAdapter?) {
        this.context = context.applicationContext
        this.bluetoothAdapter = bluetoothAdapter
        this.bleScanner = bluetoothAdapter?.bluetoothLeScanner
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled
        registerBluetoothStateReceiver()
        loadVendorTable()
    }

    fun setNamedOnly(enabled: Boolean) {
        _namedOnly.value = enabled
        updateDevicesList()
    }

    fun setKnownVendorsOnly(enabled: Boolean) {
        _knownVendorsOnly.value = enabled
        updateDevicesList()
    }

    fun setSortMode(mode: NrfSortMode) {
        _sortMode.value = mode
        updateDevicesList()
    }

    private fun loadVendorTable() {
        if (vendorTableRequested) return
        vendorTableRequested = true
        val appContext = context ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val table = HashMap<String, String>(65536)
            runCatching {
                appContext.assets.open(VENDOR_OUI_ASSET).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (line.length < 7 || line[0] == '#') continue
                        val space = line.indexOf(' ')
                        if (space < 6 || space > 9) continue
                        var valid = true
                        for (i in 0 until space) {
                            if (!isHexDigit(line[i])) { valid = false; break }
                        }
                        if (!valid) continue
                        val vendor = line.substring(space + 1).trim()
                        if (vendor.isNotEmpty()) table[line.substring(0, space)] = vendor
                    }
                }
            }
            vendorTable = table
            withContext(Dispatchers.Main) {
                for (address in deviceMap.keys.toList()) {
                    deviceMap[address]?.let { device ->
                        if (device.vendor == null) {
                            deviceMap[address] = device.copy(vendor = lookupVendor(device.address))
                        }
                    }
                }
                updateDevicesList()
            }
        }
    }

    private fun isHexDigit(ch: Char): Boolean = ch in '0'..'9' || ch in 'A'..'F'

    private fun lookupVendor(address: String): String? {
        val table = vendorTable ?: return null
        val compact = address.replace(":", "").replace("-", "").uppercase(Locale.US)
        if (compact.length < 6 || compact.any { !isHexDigit(it) }) return null
        for (len in minOf(9, compact.length) downTo 6) {
            table[compact.substring(0, len)]?.let { return it }
        }
        return null
    }

    private fun registerBluetoothStateReceiver() {
        val appContext = context ?: return
        if (bluetoothStateReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = state == BluetoothAdapter.STATE_ON
                }
            }
        }
        runCatching {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(receiver, filter)
            }
            bluetoothStateReceiver = receiver
        }
    }

    fun startScanning() {
        if (_isScanning.value) return
        _scanError.value = null
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

    private var profileAddress: String? = null
    private var profileTimeout: kotlinx.coroutines.Job? = null

    fun profileDevice(device: NrfDevice) {
        viewModelScope.launch {
            disconnectDevice(profileAddress ?: "")
            profileAddress = device.address
            deviceMap[device.address] = device.copy(isProfiling = true)
            updateDevicesList()
            try {
                val target = bluetoothAdapter?.getRemoteDevice(device.address) ?: error("Bluetooth unavailable")
                currentGatt = target.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        viewModelScope.launch {
                            if (currentGatt !== gatt) return@launch
                            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                                if (!gatt.discoverServices()) disconnectDevice(device.address)
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                                disconnectDevice(device.address)
                            }
                        }
                    }
                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        viewModelScope.launch {
                            if (currentGatt !== gatt) return@launch
                            if (status != BluetoothGatt.GATT_SUCCESS) { disconnectDevice(device.address); return@launch }
                            profileTimeout?.cancel()
                            val services = gatt.services.map { service ->
                                GattServiceInfo(service.uuid.toString(), getServiceName(service.uuid.toString()),
                                    service.characteristics.map { characteristic ->
                                        GattCharacteristicInfo(characteristic.uuid.toString(), getCharacteristicName(characteristic.uuid.toString()), getCharacteristicProperties(characteristic))
                                    })
                            }
                            updateDeviceProfile(device.address, services, null, null,
                                services.any { it.uuid.contains("1812", true) },
                                services.any { it.uuid.contains("110b", true) || it.uuid.contains("110e", true) || it.uuid.contains("110f", true) },
                                services.any { it.uuid.contains("180f", true) }, true)
                            val battery = gatt.getService(java.util.UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"))
                                ?.getCharacteristic(java.util.UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"))
                            battery?.let { runCatching { gatt.readCharacteristic(it) } }
                        }
                    }
                    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                        viewModelScope.launch {
                            if (currentGatt === gatt && status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid.toString().equals("00002a19-0000-1000-8000-00805f9b34fb", true)) {
                                characteristic.value?.firstOrNull()?.let { updateDeviceBattery(device.address, it.toInt() and 0xff) }
                            }
                        }
                    }
                })
                if (currentGatt == null) disconnectDevice(device.address)
                else profileTimeout = viewModelScope.launch { kotlinx.coroutines.delay(15_000); disconnectDevice(device.address) }
            } catch (_: Exception) {
                disconnectDevice(device.address)
            }
        }
    }

    fun stopProfiling() = disconnectDevice(profileAddress ?: "")

    fun disconnectDevice(address: String) {
        if (address != profileAddress) return
        profileTimeout?.cancel()
        runCatching { currentGatt?.disconnect() }
        runCatching { currentGatt?.close() }
        currentGatt = null
        profileAddress = null
        deviceMap[address]?.let { deviceMap[address] = it.copy(isConnected = false, isProfiling = false) }
        updateDevicesList()
    }

    private fun updateDeviceConnection(address: String, connected: Boolean) {
        deviceMap[address]?.let { device ->
            deviceMap[address] = device.copy(isConnected = connected, isProfiling = false)
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
                isConnected = connected,
                isProfiling = false
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
            if (!_isScanning.value) return@launch
            try {
                val scanner = bluetoothAdapter?.bluetoothLeScanner ?: error("Bluetooth is unavailable")
                bleScanner = scanner
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        try {
                            processBleResult(result)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        _scanError.value = errorCode
                        _isScanning.value = false
                        stopClassicScanning()
                    }
                }
                scanCallback = callback
                scanner.startScan(callback)
            } catch (e: Exception) {
                _scanError.value = -1
                _isScanning.value = false
                stopClassicScanning()
            }
        }
    }

    private fun stopBleScanning() {
        try {
            scanCallback?.let { bleScanner?.stopScan(it) }
            scanCallback = null
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
        val manufacturerId = manufacturerData?.takeIf { it.size() > 0 }?.keyAt(0)

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

        val vendor = existingDevice?.vendor ?: lookupVendor(device.address)
        val isConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            result.isConnectable
        } else {
            null
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
            isProfiling = existingDevice?.isProfiling ?: false,
            lastSeen = System.currentTimeMillis(),
            scanCount = (existingDevice?.scanCount ?: 0) + 1,
            manufacturerName = manufacturerName,
            vendor = vendor,
            isConnectable = isConnectable,
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
                                            lastSeen = System.currentTimeMillis(),
                                            scanCount = (deviceMap[it.address]?.scanCount ?: 0) + 1,
                                            vendor = deviceMap[it.address]?.vendor ?: lookupVendor(it.address),
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
        var list = deviceMap.values.toList()
        if (filter != null) list = list.filter { it.type == filter }
        if (_namedOnly.value) {
            list = list.filter { !it.name.isNullOrBlank() && !it.name.equals("Unknown", ignoreCase = true) }
        }
        if (_knownVendorsOnly.value) {
            list = list.filter { it.vendor != null || it.manufacturerName != null }
        }
        _devices.value = when (_sortMode.value) {
            NrfSortMode.NAME -> list.sortedWith(compareBy { (it.name ?: "~").lowercase() })
            NrfSortMode.RSSI -> list.sortedByDescending { it.rssi }
        }
    }

    override fun onCleared() {
        stopScanning()
        bluetoothStateReceiver?.let { receiver ->
            runCatching { context?.unregisterReceiver(receiver) }
        }
        bluetoothStateReceiver = null
        runCatching { currentGatt?.close() }
        super.onCleared()
    }
}
