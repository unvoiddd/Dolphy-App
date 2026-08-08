package com.droid.dolphy.bluetooth.whisperpair

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log

class Scanner(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val onDeviceFound: (FastPairDevice) -> Unit
) {
    companion object {
        private const val TAG = "WhisperPairScanner"
        val FAST_PAIR_SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("0000fe2c-0000-1000-8000-00805f9b34fb")
    }

    private var isScanning = false
    private var scanAllDevices = false
    private val scanner = bluetoothAdapter?.bluetoothLeScanner

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { processScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun processScanResult(result: ScanResult) {
        val device = result.device
        val scanRecord = result.scanRecord ?: return

        val serviceData = scanRecord.getServiceData(FAST_PAIR_SERVICE_UUID)

        if (serviceData != null) {
            val fastPairDevice = parseFastPairAdvertisement(
                name = device.name,
                address = device.address,
                data = serviceData,
                rssi = result.rssi,
                isFastPair = true
            )
            onDeviceFound(fastPairDevice)
        } else if (scanAllDevices) {
            val genericDevice = FastPairDevice(
                name = device.name,
                address = device.address,
                isPairingMode = false,
                hasAccountKeyFilter = false,
                modelId = null,
                rssi = result.rssi,
                lastSeen = System.currentTimeMillis(),
                isFastPair = false
            )
            onDeviceFound(genericDevice)
        }
    }

    private fun parseFastPairAdvertisement(
        name: String?,
        address: String,
        data: ByteArray,
        rssi: Int,
        isFastPair: Boolean = true
    ): FastPairDevice {
        var modelId: String? = null
        var isPairingMode = false
        var hasAccountKeyFilter = false

        if (data.isNotEmpty()) {
            val firstByte = data[0].toInt() and 0xFF




            if (data.size == 3 && (firstByte and 0x80) == 0) {
                modelId = data.joinToString("") { "%02X".format(it) }
                isPairingMode = true
                Log.d(TAG, "Device in PAIRING MODE: $address, Model ID: $modelId")
            }


            else if ((firstByte and 0x60) != 0) {
                hasAccountKeyFilter = true
                isPairingMode = false
                Log.d(TAG, "Device in IDLE mode (has account key filter): $address")
            }

            else if (data.size > 3) {


                if ((firstByte and 0x80) == 0) {
                    modelId = data.take(3).joinToString("") { "%02X".format(it) }
                }
                Log.d(TAG, "Device with extended data: $address, size=${data.size}")
            }
        }

        return FastPairDevice(
            name = name,
            address = address,
            isPairingMode = isPairingMode,
            hasAccountKeyFilter = hasAccountKeyFilter,
            modelId = modelId,
            rssi = rssi,
            lastSeen = System.currentTimeMillis(),
            isFastPair = isFastPair
        )
    }

    fun setScanAllDevices(scanAll: Boolean) {
        scanAllDevices = scanAll
    }

    @SuppressLint("MissingPermission")
    fun startScanning(scanAll: Boolean = false): Boolean {
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null - Bluetooth may be disabled")
            return false
        }

        if (isScanning) {
            if (scanAllDevices == scanAll) {
                Log.d(TAG, "Already scanning with same filter")
                return true
            }
            Log.d(TAG, "Restarting scan with new filter (scanAll=$scanAll)")
            stopScanning()
        }

        scanAllDevices = scanAll

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        return try {
            if (scanAll) {
                scanner.startScan(null, settings, scanCallback)
                Log.d(TAG, "Scanning ALL BLE devices")
            } else {
                val filter = ScanFilter.Builder()
                    .setServiceData(FAST_PAIR_SERVICE_UUID, byteArrayOf())
                    .build()
                scanner.startScan(listOf(filter), settings, scanCallback)
                Log.d(TAG, "Scanning Fast Pair devices only")
            }
            isScanning = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning || scanner == null) return

        try {
            scanner.stopScan(scanCallback)
            isScanning = false
            Log.d(TAG, "Scanning stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop scan", e)
        }
    }

    fun isCurrentlyScanning(): Boolean = isScanning
}

