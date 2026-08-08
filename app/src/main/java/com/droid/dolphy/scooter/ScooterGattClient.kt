package com.droid.dolphy.scooter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean


@SuppressLint("MissingPermission")
class ScooterGattClient(
    private val context: Context,
    private val device: BluetoothDevice,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected()
        fun onDisconnected(reason: String?)
        fun onBattery(percent: Int)
        fun onCommandResult(ok: Boolean, label: String)
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private val ready = AtomicBoolean(false)
    private val rxBuffer = ArrayList<Byte>(64)

    fun connect() {
        close()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, callback)
        }
    }

    fun disconnect() {
        try {
            gatt?.disconnect()
        } catch (_: Exception) {
        }
        close()
        main.post { listener.onDisconnected(null) }
    }

    fun close() {
        ready.set(false)
        writeChar = null
        notifyChar = null
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        rxBuffer.clear()
    }

    fun sendTuningPayload(payload: ByteArray, label: String) = send(payload, label)

    private fun send(payload: ByteArray, label: String, silent: Boolean = false) {
        val g = gatt
        val ch = writeChar
        if (g == null || ch == null || !ready.get()) {
            if (!silent) main.post { listener.onCommandResult(false, label) }
            return
        }
        try {
            var offset = 0
            while (offset < payload.size) {
                val end = minOf(offset + 20, payload.size)
                val chunk = payload.copyOfRange(offset, end)
                val writeType =
                    if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    } else {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeCharacteristic(ch, chunk, writeType) ==
                        android.bluetooth.BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    ch.value = chunk
                    @Suppress("DEPRECATION")
                    ch.writeType = writeType
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(ch)
                }
                if (!ok) {
                    if (!silent) main.post { listener.onCommandResult(false, label) }
                    return
                }
                offset = end
                if (offset < payload.size) Thread.sleep(20)
            }
            if (!silent) main.post { listener.onCommandResult(true, label) }
        } catch (t: Throwable) {
            if (!silent) main.post { listener.onError(t.message ?: "write failed") }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        g.requestMtu(64)
                        g.discoverServices()
                    } else {
                        main.post { listener.onError("connect status=$status") }
                        close()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    ready.set(false)
                    main.post { listener.onDisconnected("disconnected") }
                    close()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                main.post { listener.onError("services status=$status") }
                g.disconnect()
                return
            }
            val service = g.getService(ScooterProtocol.SERVICE_UUID)
            if (service == null) {
                main.post { listener.onError("Nordic UART service missing") }
                g.disconnect()
                return
            }
            writeChar = service.getCharacteristic(ScooterProtocol.WRITE_UUID)
            notifyChar = service.getCharacteristic(ScooterProtocol.NOTIFY_UUID)
            if (writeChar == null) {
                main.post { listener.onError("write characteristic missing") }
                g.disconnect()
                return
            }
            val n = notifyChar
            if (n != null) {
                g.setCharacteristicNotification(n, true)
                val cccd = n.getDescriptor(CCCD)
                if (cccd != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(cccd)
                    }
                } else {
                    markReadyAndConnected()
                }
            } else {
                markReadyAndConnected()
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            markReadyAndConnected()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            onNotify(value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onNotify(value)
        }
    }

    private fun markReadyAndConnected() {
        if (ready.compareAndSet(false, true)) {
            main.post { listener.onConnected() }
        }
    }

    private fun onNotify(chunk: ByteArray) {
        rxBuffer.clear()
    }

    companion object {
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

