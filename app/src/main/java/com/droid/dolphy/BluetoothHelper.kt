// Deprecated platform APIs are still the only option on minSdk 26; their
// replacements require API 33+ branches that this feature cannot take yet.
@file:Suppress("DEPRECATION")

package com.droid.dolphy

import android.bluetooth.BluetoothAdapter

object BluetoothHelper {
    val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
}

