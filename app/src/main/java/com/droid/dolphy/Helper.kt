package com.droid.dolphy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import java.util.Random

object Helper {
    private val hexDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    val random = Random()
    var delay = 20
    val delays = intArrayOf(10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 6000, 8000, 10000, 12000, 15000, 18000, 20000)
    const val MAX_LOOP = 50_000_000

    var concurrentMode = false
    var useExtendedAdvertising = true
    var hardwareExtendedBroken: Boolean = false

    private const val PREF_NAME = "AppSettings"
    private const val KEY_DELAY = "spam_delay"
    private const val KEY_CONCURRENT = "concurrent_mode"

    fun saveDelay(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DELAY, delay).apply()
    }

    fun loadDelay(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        delay = prefs.getInt(KEY_DELAY, 20)
        concurrentMode = prefs.getBoolean(KEY_CONCURRENT, false)
        useExtendedAdvertising = prefs.getBoolean("extended_advertising_enabled", true)
    }

    fun canUseExtendedAdvertising(): Boolean {
        if (hardwareExtendedBroken) {
            log("canUseExtendedAdvertising: false (hardwareExtendedBroken=true)")
            return false
        }
        val adapter = BluetoothHelper.bluetoothAdapter
        val isSupported = useExtendedAdvertising &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            adapter?.isLeExtendedAdvertisingSupported == true &&
            (adapter.getLeMaximumAdvertisingDataLength() >= 62)

        if (!useExtendedAdvertising) {
            log("canUseExtendedAdvertising: false (disabled by user)")
        } else if (isSupported) {
            log("canUseExtendedAdvertising: true")
        } else {
            log("canUseExtendedAdvertising: false (hardware/SDK limitation)")
        }

        return isSupported
    }

    fun saveConcurrentMode(context: Context, enabled: Boolean) {
        concurrentMode = enabled
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CONCURRENT, enabled).apply()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun isPermissionGranted(c: Context): Boolean {
        return (ActivityCompat.checkSelfPermission(c, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) &&
            (ActivityCompat.checkSelfPermission(c, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
    }

    fun convertHexToByteArray(hex: String): ByteArray {
        val cleanHex = hex.lowercase().replace("0x", "").replace(" ", "")
        if (cleanHex.isEmpty() || cleanHex.length % 2 != 0) {
            throw IllegalArgumentException("Invalid hex string: $cleanHex")
        }
        val data = ByteArray(cleanHex.length / 2)
        for (i in 0 until cleanHex.length step 2) {
            val high = Character.digit(cleanHex[i], 16)
            val low = Character.digit(cleanHex[i + 1], 16)
            if (high == -1 || low == -1) {
                throw IllegalArgumentException("Invalid hex character in: $cleanHex")
            }
            data[i / 2] = ((high shl 4) or low).toByte()
        }
        return data
    }

    fun randomHexFiller(size: Int): String {
        return buildString {
            repeat(size) { append(hexDigits[random.nextInt(hexDigits.size)]) }
        }
    }

    fun log(msg: String) {
        android.util.Log.d("BLESpam", msg)
    }
}

