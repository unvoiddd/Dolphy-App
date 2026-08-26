package com.droid.dolphy

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import com.droid.dolphy.plugin.PluginBluetoothHooks
import org.json.JSONObject

class BluetoothAdvertiser {

    private val bluetoothAdapter = BluetoothHelper.bluetoothAdapter
    private val advertiser get() = bluetoothAdapter?.bluetoothLeAdvertiser

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Helper.log("Advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Helper.log("Advertising failed: $errorCode")
        }
    }

    private var extCallback: AdvertisingSetCallback? = null
    private var legacyCallbackActive: Boolean = false
    private var extendedCallbackActive: Boolean = false

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun advertise(
        advertiseData: AdvertiseData,
        scanResponse: AdvertiseData? = null
    ) {
        val pluginDecision = PluginBluetoothHooks.interceptAdvertising(
            advertiseData,
            scanResponse,
            JSONObject().put("source", "ble_spam"),
        )
        if (pluginDecision.skipNative) return
        val effectiveData = pluginDecision.advertiseData
        val effectiveScanResponse = pluginDecision.scanResponse
        if (Helper.canUseExtendedAdvertising()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                advertiseExtended(effectiveData)
                return
            }
        }
        advertiseLegacy(effectiveData, effectiveScanResponse)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun advertiseLegacy(advertiseData: AdvertiseData, scanResponse: AdvertiseData?) {
        val adv = advertiser ?: run {
            Helper.log("BluetoothLeAdvertiser is null")
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        try {
            adv.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
            legacyCallbackActive = true
            BleSpamRuntime.trackSentPackets()
        } catch (e: Exception) {
            Helper.log("startAdvertising error: ${e.message}")
            legacyCallbackActive = false
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun advertiseExtended(advertiseData: AdvertiseData) {
        val adv = advertiser ?: run {
            Helper.log("BluetoothLeAdvertiser is null (extended)")
            return
        }

        val params = AdvertisingSetParameters.Builder()
            .setLegacyMode(false)
            .setInterval(AdvertisingSetParameters.INTERVAL_MIN)
            .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
            .setConnectable(false)
            .setScannable(false)
            .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
            .setSecondaryPhy(BluetoothDevice.PHY_LE_1M)
            .build()

        extCallback = object : AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(
                advertisingSet: android.bluetooth.le.AdvertisingSet?,
                txPower: Int,
                status: Int
            ) {
                if (status == ADVERTISE_SUCCESS) {
                    Helper.log("Extended advertising started (tx=$txPower dBm)")
                } else {
                    Helper.log("Extended advertising failed: $status")
                    if (status == 2) {
                        Helper.log("Data too large for extended advertising - falling back to legacy")
                        Helper.hardwareExtendedBroken = true
                    }
                    extendedCallbackActive = false
                    advertiseLegacy(advertiseData, null)
                }
            }
        }

        try {
            adv.startAdvertisingSet(params, advertiseData, null, null, null, extCallback)
            extendedCallbackActive = true
            BleSpamRuntime.trackSentPackets()
        } catch (e: Exception) {
            Helper.log("startAdvertisingSet error: ${e.message}")
            extendedCallbackActive = false
            advertiseLegacy(advertiseData, null)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        val pluginDecision = PluginBluetoothHooks.action("bluetooth.advertising.stop", JSONObject().put("source", "ble_spam"))
        if (pluginDecision.cancelled) return
        if (pluginDecision.handled) {
            extendedCallbackActive = false
            extCallback = null
            legacyCallbackActive = false
            return
        }
        if (extendedCallbackActive) {
            extCallback?.let {
                try {
                    val adv = advertiser
                    adv?.stopAdvertisingSet(it)


                    Helper.log("Extended advertising stopped")
                } catch (e: Exception) {
                    Log.e("BLESpam", "Error stopping extended advertising: ${e.message}")
                }
            }
            extendedCallbackActive = false
            extCallback = null
        }
        if (legacyCallbackActive) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
                Helper.log("Legacy advertising stopped")
            } catch (e: Exception) {
                Log.e("BLESpam", "Error stopping legacy advertising: ${e.message}")
            }
            legacyCallbackActive = false
        }
    }
}

