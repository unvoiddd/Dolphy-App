package com.droid.dolphy

import android.bluetooth.BluetoothAdapter

object BluetoothHelper {
    val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
}

