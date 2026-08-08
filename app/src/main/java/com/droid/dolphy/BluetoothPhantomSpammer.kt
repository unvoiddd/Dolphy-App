package com.droid.dolphy

import android.bluetooth.BluetoothAdapter
import kotlinx.coroutines.*
import android.util.Log

class BluetoothPhantomSpammer : Spammer {
    private var isRunning = false
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var blinkRunnable: Runnable? = null

    private val names = listOf(
        "iPhone 15 Pro Max",
        "Tesla Model 3",
        "Sony WH-1000XM5",
        "Dolphy Phantom",
        "AirPods Pro",
        "Samsung S24 Ultra",
        "Google Pixel 8",
        "iPad Air",
        "Bose QC45",
        "MacBook Pro"
    )

    override fun isSpamming(): Boolean = isRunning

    override fun start() {
        if (isRunning) return
        isRunning = true
        job = scope.launch {
            while (isActive && isRunning) {
                try {
                    val newName = names.random()
                    setName(newName)
                } catch (e: Exception) {
                    Log.e("BluetoothPhantom", "Error rotating name", e)
                }
                delay(1000)
            }
        }
    }

    override fun stop() {
        isRunning = false
        job?.cancel()
        job = null
    }

    override fun setBlinkRunnable(runnable: Runnable?) {
        this.blinkRunnable = runnable
    }

    override fun getBlinkRunnable(): Runnable? = blinkRunnable

    private fun setName(name: String) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                adapter.name = name
                Log.d("BluetoothPhantom", "Name updated to: $name")
            }
        } catch (e: SecurityException) {
            Log.e("BluetoothPhantom", "Permission denied for name change", e)
        } catch (e: Exception) {
            Log.e("BluetoothPhantom", "Failed to set name", e)
        }
    }
}

