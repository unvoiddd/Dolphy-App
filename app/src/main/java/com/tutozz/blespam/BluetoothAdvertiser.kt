package com.tutozz.blespam

import android.Manifest
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import androidx.annotation.RequiresPermission

class BluetoothAdvertiser {
    private val bluetoothAdapter = BluetoothHelper.bluetoothAdapter
    private val bluetoothLeAdvertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter?.bluetoothLeAdvertiser

    fun advertise(
        advertiseData: AdvertiseData,
        scanResponse: AdvertiseData?,
        connectable: Boolean = false
    ) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(connectable)
            .build()

        val advertiser = bluetoothLeAdvertiser
        if (advertiser == null) {
            Helper.log("BluetoothLeAdvertiser is null")
            return
        }

        advertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Helper.log("Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Helper.log("Advertising failed: $errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
    }
}
