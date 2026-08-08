package com.droid.dolphy.scooter

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
class ScooterHackViewModel(app: Application) : AndroidViewModel(app) {

    private val _devices = MutableStateFlow<List<ScooterDevice>>(emptyList())
    val devices: StateFlow<List<ScooterDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectedAddress = MutableStateFlow<String?>(null)
    val connectedAddress: StateFlow<String?> = _connectedAddress.asStateFlow()

    private val deviceMap = linkedMapOf<String, ScooterDevice>()
    private var adapter: BluetoothAdapter? = null
    private var gattClient: ScooterGattClient? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!isHardScooterMatch(result)) return
            val address = result.device.address ?: return
            val name = result.device.name
                ?: result.scanRecord?.deviceName
                ?: "Scooter"
            val rssi = result.rssi
            val existing = deviceMap[address]
            deviceMap[address] = (existing ?: ScooterDevice(address, name, rssi)).copy(
                name = if (name.isNotBlank()) name else existing?.name ?: "Scooter",
                rssi = rssi,
                isConnected = existing?.isConnected == true || _connectedAddress.value == address,
                isConnecting = existing?.isConnecting == true,
                batteryPercent = existing?.batteryPercent,
                statusMessage = existing?.statusMessage,
            )
            publish()
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    fun ensureAdapter(context: Context) {
        if (adapter == null) {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            adapter = bm?.adapter
        }
    }

    fun startScan(context: Context) {
        ensureAdapter(context)
        val a = adapter ?: return
        if (!a.isEnabled) return
        if (_isScanning.value) return
        val scanner = a.bluetoothLeScanner ?: return

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(ScooterProtocol.SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setManufacturerData(ScooterProtocol.NINEBOT_MANUFACTURER_ID, byteArrayOf())
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            _isScanning.value = true
        } catch (_: SecurityException) {
            try {
                scanner.startScan(filters, settings, scanCallback)
                _isScanning.value = true
            } catch (_: Exception) {
                _isScanning.value = false
            }
        } catch (_: Exception) {
            _isScanning.value = false
        }
    }

    fun stopScan() {
        val scanner = adapter?.bluetoothLeScanner
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        _isScanning.value = false
    }

    fun toggleConnection(context: Context, scooter: ScooterDevice) {
        if (_connectedAddress.value == scooter.address || scooter.isConnected) {
            disconnect()
            return
        }
        connect(context, scooter)
    }

    fun connect(context: Context, scooter: ScooterDevice) {
        ensureAdapter(context)
        val a = adapter ?: return
        disconnect(silent = true)
        updateDevice(scooter.address) {
            it.copy(isConnecting = true, statusMessage = "Connecting…", isConnected = false)
        }
        val remote = try {
            a.getRemoteDevice(scooter.address)
        } catch (_: Exception) {
            updateDevice(scooter.address) {
                it.copy(isConnecting = false, statusMessage = "Invalid address")
            }
            return
        }
        gattClient = ScooterGattClient(
            context = context.applicationContext,
            device = remote,
            listener = object : ScooterGattClient.Listener {
                override fun onConnected() {
                    _connectedAddress.value = scooter.address
                    updateDevice(scooter.address) {
                        it.copy(
                            isConnecting = false,
                            isConnected = true,
                            statusMessage = "Connected",
                        )
                    }
                }

                override fun onDisconnected(reason: String?) {
                    if (_connectedAddress.value == scooter.address) {
                        _connectedAddress.value = null
                    }
                    updateDevice(scooter.address) {
                        it.copy(
                            isConnecting = false,
                            isConnected = false,
                            statusMessage = reason ?: "Disconnected",
                        )
                    }
                    gattClient = null
                }

                override fun onBattery(percent: Int) {
                    updateDevice(scooter.address) { it.copy(batteryPercent = percent) }
                }

                override fun onCommandResult(ok: Boolean, label: String) {
                    updateDevice(scooter.address) {
                        it.copy(statusMessage = if (ok) "$label ✓" else "$label failed")
                    }
                }

                override fun onError(message: String) {
                    updateDevice(scooter.address) {
                        it.copy(isConnecting = false, statusMessage = message)
                    }
                }
            },
        )
        gattClient?.connect()
    }

    fun disconnect(silent: Boolean = false) {
        val addr = _connectedAddress.value
        gattClient?.disconnect()
        gattClient?.close()
        gattClient = null
        _connectedAddress.value = null
        if (addr != null && !silent) {
            updateDevice(addr) {
                it.copy(isConnected = false, isConnecting = false, statusMessage = "Disconnected")
            }
        }
    }

    fun sendTuningPayload(payload: ByteArray, label: String) {
        gattClient?.sendTuningPayload(payload, label)
    }

    private fun updateDevice(address: String, transform: (ScooterDevice) -> ScooterDevice) {
        val cur = deviceMap[address] ?: return
        deviceMap[address] = transform(cur)
        publish()
    }

    private fun publish() {
        _devices.value = deviceMap.values
            .sortedWith(compareByDescending<ScooterDevice> { it.isConnected }.thenByDescending { it.rssi })
            .toList()
    }

    override fun onCleared() {
        stopScan()
        disconnect(silent = true)
        super.onCleared()
    }

    companion object {
        private val NAME_HINTS = listOf(
            "miscooter",
            "mi scooter",
            "xiaomi",
            "ninebot",
            "segway",
            "m365",
            "mi electric",
            "mipro",
            "mijia",
            "nb-",
            "max g",
            "g30",
            "f20",
            "f25",
            "f30",
            "f40",
            "es1",
            "es2",
            "es4",
            "e22",
            "e25",
            "e45",
        )

        fun isHardScooterMatch(result: ScanResult): Boolean {
            val record = result.scanRecord
            val name = (
                result.device.name
                    ?: record?.deviceName
                    ?: ""
                ).lowercase()

            if (name.isNotBlank() && NAME_HINTS.any { name.contains(it) }) return true

            val uuids = record?.serviceUuids
            if (uuids != null && uuids.any { it.uuid == ScooterProtocol.SERVICE_UUID }) return true

            val mfg = record?.manufacturerSpecificData
            if (mfg != null) {
                if (mfg.indexOfKey(ScooterProtocol.NINEBOT_MANUFACTURER_ID) >= 0) return true
                if (mfg.indexOfKey(0x4E42) >= 0) return true
            }

            val raw = record?.bytes
            if (raw != null && raw.size > 6) {
                for (i in 0 until raw.size - 3) {
                    if ((raw[i].toInt() and 0xFF) == 0x4E &&
                        (raw[i + 1].toInt() and 0xFF) == 0x42 &&
                        (raw[i + 2].toInt() and 0xFF) == 0x20
                    ) {
                        return true
                    }
                }
            }
            return false
        }
    }
}

