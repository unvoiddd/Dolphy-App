package com.droid.dolphy.ble.smarthome

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.callback.profile.ProfileDataCallback
import java.util.UUID

class SmartLightManager(context: Context) : BleManager(context) {

    private var writeChar: BluetoothGattCharacteristic? = null

    override fun getGattCallback(): BleManagerGattCallback = object : BleManagerGattCallback() {
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {



            val candidates = listOf(
                UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb") to
                    UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb"),
                UUID.fromString("0000ffd0-0000-1000-8000-00805f9b34fb") to
                    UUID.fromString("0000ffd1-0000-1000-8000-00805f9b34fb"),
            )

            for ((svcUuid, chrUuid) in candidates) {
                val svc = gatt.getService(svcUuid) ?: continue
                val chr = svc.getCharacteristic(chrUuid) ?: continue
                val props = chr.properties
                val canWrite =
                    (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                        (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                if (canWrite) {
                    writeChar = chr
                    return true
                }
            }
            return false
        }

        override fun initialize() {

        }

        override fun onServicesInvalidated() {
            writeChar = null
        }
    }

    fun supportsElkBledom(): Boolean = writeChar != null

    fun sendElkCommand(bytes: ByteArray) {
        val chr = writeChar ?: return

        writeCharacteristic(chr, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            .split()
            .enqueue()
    }

    fun power(on: Boolean) = sendElkCommand(if (on) ElkBledomProtocol.powerOn() else ElkBledomProtocol.powerOff())

    fun setColor(r: Int, g: Int, b: Int) = sendElkCommand(ElkBledomProtocol.setColor(r, g, b))

    fun setBrightness(level: Int) = sendElkCommand(ElkBledomProtocol.setBrightness(level))

    @Suppress("EmptyMethod")
    private object NoopCallback : ProfileDataCallback {
        override fun onDataReceived(device: android.bluetooth.BluetoothDevice, data: no.nordicsemi.android.ble.data.Data) {}
    }
}


