package com.droid.dolphy

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.PriorityQueue
import kotlin.math.roundToInt

object UsbIrManager {
    private const val TAG = "UsbIrManager"
    private const val ACTION_USB_PERMISSION = "com.droid.dolphy.USB_PERMISSION"
    private const val PREFS_NAME = "dolphy_prefs"
    private const val PREF_DONT_SHOW_POPUP = "dont_show_usb_ir_popup"

    private data class DongleProfile(val name: String, val encoderType: String)

    private val SUPPORTED_DONGLES = mapOf(
        (0x10C4 to 0x8468) to DongleProfile("ZaZa Remote / Tiqiaa / Tview", "legacy_bulk_st"),
        (0x045E to 0x8468) to DongleProfile("ZaZa Remote / Tiqiaa / Tview", "legacy_bulk_st"),
        (0x045C to 0x0131) to DongleProfile("ElkSmart D552/D226", "elksmart_bulk"),
        (0x045C to 0x0132) to DongleProfile("ElkSmart D552/D226", "elksmart_bulk"),
        (0x045C to 0x0134) to DongleProfile("Ocrustar / ElkSmart", "elksmart_bulk"),
        (0x045C to 0x014A) to DongleProfile("ElkSmart D552/D226", "elksmart_bulk"),
        (0x045C to 0x0184) to DongleProfile("ElkSmart D552/D226", "elksmart_bulk"),
        (0x045C to 0x0195) to DongleProfile("ElkSmart D552/D226", "elksmart_bulk"),
        (0x045C to 0x02AA) to DongleProfile("ElkSmart Old Model", "elksmart_bulk")
    )

    private var appContext: Context? = null
    private var usbManager: UsbManager? = null
    private var prefs: SharedPreferences? = null

    private var activeDevice: UsbDevice? = null
    private var activeConnection: UsbDeviceConnection? = null
    private var activeInterface: UsbInterface? = null
    private var outEndpoint: UsbEndpoint? = null
    private var inEndpoint: UsbEndpoint? = null
    private var activeProfile: DongleProfile? = null

    private val legacyEncoder = LegacyBulkStEncoder()
    private val elkSmartEncoder = ElkSmartBulkEncoder()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName

    private val _showPopup = MutableStateFlow(false)
    val showPopup: StateFlow<Boolean> = _showPopup

    private var isInitialized = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null && isSupported(device)) {
                        checkDevice(device)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null && device.deviceName == activeDevice?.deviceName) {
                        releaseUsb()
                    }
                }
                ACTION_USB_PERMISSION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null && granted) {
                        openDevice(device)
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        val app = context.applicationContext
        appContext = app
        usbManager = app.getSystemService(Context.USB_SERVICE) as? UsbManager
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        androidx.core.content.ContextCompat.registerReceiver(app, usbReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)

        isInitialized = true
        scanDevices()
    }

    fun scanDevices() {
        val manager = usbManager ?: return
        val deviceList = manager.deviceList ?: return
        for (device in deviceList.values) {
            if (isSupported(device)) {
                checkDevice(device)
                return
            }
        }
    }

    fun isSupported(device: UsbDevice): Boolean {
        return SUPPORTED_DONGLES.containsKey(device.vendorId to device.productId)
    }

    fun dismissPopup(dontShowAgain: Boolean = false) {
        if (dontShowAgain) {
            prefs?.edit()?.putBoolean(PREF_DONT_SHOW_POPUP, true)?.apply()
        }
        _showPopup.value = false
    }

    private fun checkDevice(device: UsbDevice) {
        val manager = usbManager ?: return
        if (manager.hasPermission(device)) {
            openDevice(device)
        } else {
            requestPermission(device)
        }
    }

    private fun requestPermission(device: UsbDevice) {
        val context = appContext ?: return
        val manager = usbManager ?: return
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pi = PendingIntent.getBroadcast(context, 0, intent, flags)
        manager.requestPermission(device, pi)
    }

    @Synchronized
    private fun openDevice(device: UsbDevice) {
        val manager = usbManager ?: return
        val profile = SUPPORTED_DONGLES[device.vendorId to device.productId] ?: return

        if (_isConnected.value && activeConnection != null) {
            if (activeDevice?.deviceName == device.deviceName) return
            releaseUsb()
        }

        var chosenInterface: UsbInterface? = null
        var chosenOut: UsbEndpoint? = null
        var chosenIn: UsbEndpoint? = null

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            val pair = findEndpointPair(intf)
            if (pair != null) {
                chosenInterface = intf
                chosenOut = pair.first
                chosenIn = pair.second
                break
            }
        }

        if (chosenInterface == null || chosenOut == null || chosenIn == null) {
            Log.e(TAG, "Supported dongle has no paired endpoints: ${device.deviceName}")
            return
        }

        val conn = manager.openDevice(device) ?: return
        if (!conn.claimInterface(chosenInterface, true)) {
            conn.close()
            return
        }

        val handshakeOk = if (profile.encoderType == "elksmart_bulk") {
            elkSmartEncoder.openHandshake(conn, chosenIn, chosenOut)
        } else {
            legacyEncoder.openHandshake(conn, chosenIn, chosenOut)
        }

        if (!handshakeOk && profile.encoderType == "elksmart_bulk") {
            try { conn.releaseInterface(chosenInterface) } catch (_: Exception) {}
            conn.close()
            return
        }

        activeDevice = device
        activeConnection = conn
        activeInterface = chosenInterface
        outEndpoint = chosenOut
        inEndpoint = chosenIn
        activeProfile = profile

        _isConnected.value = true
        _connectedDeviceName.value = profile.name

        val dontShow = prefs?.getBoolean(PREF_DONT_SHOW_POPUP, false) ?: false
        if (!dontShow) {
            _showPopup.value = true
        }
        Log.i(TAG, "USB IR module connected: ${profile.name}")
    }

    @Synchronized
    private fun releaseUsb() {
        try {
            activeInterface?.let { activeConnection?.releaseInterface(it) }
            activeConnection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing USB", e)
        } finally {
            activeDevice = null
            activeConnection = null
            activeInterface = null
            outEndpoint = null
            inEndpoint = null
            activeProfile = null
            _isConnected.value = false
            _connectedDeviceName.value = null
            _showPopup.value = false
        }
    }

    private fun findEndpointPair(intf: UsbInterface): Pair<UsbEndpoint, UsbEndpoint>? {
        val outEps = mutableMapOf<Int, UsbEndpoint>()
        val inEps = mutableMapOf<Int, UsbEndpoint>()
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            val num = ep.address and 0x0F
            if (ep.direction == UsbConstants.USB_DIR_OUT) {
                outEps[num] = ep
            } else if (ep.direction == UsbConstants.USB_DIR_IN) {
                inEps[num] = ep
            }
        }
        for (num in outEps.keys.intersect(inEps.keys).sorted()) {
            return Pair(outEps[num]!!, inEps[num]!!)
        }
        return null
    }

    @Synchronized
    fun transmit(frequency: Int, pattern: IntArray): Boolean {
        if (!_isConnected.value || activeConnection == null || outEndpoint == null) return false
        val profile = activeProfile ?: return false
        val conn = activeConnection ?: return false
        val outEp = outEndpoint ?: return false

        val inEp = inEndpoint

        try {
            if (sendOnce(profile, conn, inEp, outEp, frequency, pattern)) return true
            Log.w(TAG, "USB IR transmit failed, re-handshaking and retrying")
            val handshakeOk = if (inEp == null) false else if (profile.encoderType == "elksmart_bulk") {
                elkSmartEncoder.openHandshake(conn, inEp, outEp)
            } else {
                legacyEncoder.openHandshake(conn, inEp, outEp)
            }
            return handshakeOk && sendOnce(profile, conn, inEp, outEp, frequency, pattern)
        } catch (e: Exception) {
            Log.e(TAG, "Error transmitting over USB", e)
            releaseUsb()
            return false
        }
    }

    private fun sendOnce(
        profile: DongleProfile,
        conn: UsbDeviceConnection,
        inEp: UsbEndpoint?,
        outEp: UsbEndpoint,
        frequency: Int,
        pattern: IntArray
    ): Boolean {
        if (profile.encoderType == "elksmart_bulk") {
            drain(conn, inEp, 30)
            val frames = elkSmartEncoder.encode(frequency, pattern) ?: return false
            for (frame in frames) {
                if (conn.bulkTransfer(outEp, frame, frame.size, 400) <= 0) return false
            }
            Thread.sleep(elkSmartEncoder.postTransmitDelayMs(pattern).toLong())
            drain(conn, inEp, 30)
            return true
        }
        return legacyEncoder.transmit(conn, inEp, outEp, frequency, pattern)
    }

    private class LegacyBulkStEncoder {
        private companion object {
            const val CMD_SEND_MODE: Byte = 0x53

            const val CMD_DATA: Byte = 0x44

        }

        private var e = 1
        private var f = 0

        private fun nextE(): Int {
            e = if (e < 0x0F) e + 1 else 0x01
            return e
        }

        private fun nextF(): Int {
            f = if (f < 0x7F) f + 1 else 0x01
            return f
        }

        fun openHandshake(conn: UsbDeviceConnection, inEp: UsbEndpoint, outEp: UsbEndpoint): Boolean {
            drain(conn, inEp, 80)
            val sent = sendCommand(conn, inEp, outEp, CMD_SEND_MODE, 250)
            drain(conn, inEp, 50)
            return sent
        }

        fun transmit(
            conn: UsbDeviceConnection,
            inEp: UsbEndpoint?,
            outEp: UsbEndpoint,
            freqHz: Int,
            pattern: IntArray
        ): Boolean {
            drain(conn, inEp, 30)
            if (!sendCommand(conn, inEp, outEp, CMD_SEND_MODE, 250)) return false

            val cmdId = nextF()
            val frames = encode(freqHz, pattern, cmdId) ?: return false
            for (frame in frames) {
                if (conn.bulkTransfer(outEp, frame, frame.size, 400) <= 0) return false
            }

            val waitMs = postTransmitDelayMs(pattern) + 400
            if (inEp == null || !waitReply(conn, inEp, cmdId, CMD_DATA, waitMs)) {
                Thread.sleep(postTransmitDelayMs(pattern).toLong())
            }
            drain(conn, inEp, 20)
            return true
        }

        private fun sendCommand(
            conn: UsbDeviceConnection,
            inEp: UsbEndpoint?,
            outEp: UsbEndpoint,
            cmd: Byte,
            replyTimeoutMs: Int
        ): Boolean {
            val cmdId = nextF()
            val frame = byteArrayOf(
                0x02, 0x09, nextE().toByte(), 0x01, 0x01, 0x53, 0x54, cmdId.toByte(), cmd, 0x45, 0x4E
            )
            if (conn.bulkTransfer(outEp, frame, frame.size, 250) <= 0) return false
            if (inEp != null) waitReply(conn, inEp, cmdId, cmd, replyTimeoutMs)
            return true
        }

        private fun waitReply(conn: UsbDeviceConnection, inEp: UsbEndpoint, cmdId: Int, cmd: Byte, timeoutMs: Int): Boolean {
            val size = maxOf(64, inEp.maxPacketSize)
            val buf = ByteArray(size)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                val left = (deadline - System.currentTimeMillis()).toInt()
                if (left <= 0) return false
                val n = conn.bulkTransfer(inEp, buf, size, minOf(left, 100))
                if (n <= 0) continue
                for (i in 0..n - 4) {
                    if (buf[i] == 0x53.toByte() && buf[i + 1] == 0x54.toByte() &&
                        (buf[i + 2].toInt() and 0xFF) == cmdId && buf[i + 3] == cmd
                    ) return true
                }
            }
        }

        fun postTransmitDelayMs(pattern: IntArray): Int {
            var totalUs = 0
            for (v in pattern) totalUs += maxOf(0, v)
            return maxOf(25, (totalUs / 1000) + 2)
        }

        fun encode(freqHz: Int, pattern: IntArray, cmdId: Int): List<ByteArray>? {
            if (pattern.isEmpty()) return null
            val normalized = pattern.map { maxOf(1, it) }.toMutableList()
            if (normalized.size % 2 == 0) {
                val tail = normalized.last()
                normalized[normalized.size - 1] = if (tail > 3000) tail - 3000 else 10
            }

            val payload = mutableListOf<Byte>(0x53, 0x54, cmdId.toByte(), CMD_DATA, 0x00)
            for (i in normalized.indices) {
                var units = normalized[i] / 16
                if (units <= 0) units = 1
                val isMark = (i % 2 == 0)
                while (units > 0) {
                    val chunk = minOf(units, 0x7F)
                    units -= chunk
                    val byteVal = chunk or (if (isMark) 0x80 else 0x00)
                    payload.add(byteVal.toByte())
                }
            }
            payload.add(0x45.toByte())
            payload.add(0x4E.toByte())

            val maxPayloadPerFrame = 0x38
            val total = maxOf(1, (payload.size + maxPayloadPerFrame - 1) / maxPayloadPerFrame)
            val eVal = nextE().toByte()
            val frames = mutableListOf<ByteArray>()
            var offset = 0
            var index = 1
            while (offset < payload.size) {
                val take = minOf(maxPayloadPerFrame, payload.size - offset)
                val frame = ByteArray(take + 5)
                frame[0] = 0x02
                frame[1] = (take + 3).toByte()
                frame[2] = eVal
                frame[3] = total.toByte()
                frame[4] = index.toByte()
                for (j in 0 until take) {
                    frame[5 + j] = payload[offset + j]
                }
                frames.add(frame)
                offset += take
                index++
            }
            return frames
        }
    }

    private class ElkSmartBulkEncoder {
        private var subtype: String? = null

        fun openHandshake(conn: UsbDeviceConnection, inEp: UsbEndpoint, outEp: UsbEndpoint): Boolean {
            drain(conn, inEp, 80)
            val identify = byteArrayOf(0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte())
            if (conn.bulkTransfer(outEp, identify, identify.size, 200) <= 0) return false

            val size = maxOf(64, inEp.maxPacketSize)
            val resp = ByteArray(size)
            val n = conn.bulkTransfer(inEp, resp, size, 450)
            if (n >= 6) {
                val p0 = resp[0].toInt() and 0xFF
                val p1 = resp[1].toInt() and 0xFF
                val p2 = resp[2].toInt() and 0xFF
                val p3 = resp[3].toInt() and 0xFF
                if (p0 == 0xFC && p1 == 0xFC && p2 == 0xFC && p3 == 0xFC) {
                    val b4 = resp[4].toInt() and 0xFF
                    val b5 = resp[5].toInt() and 0xFF
                    subtype = if (b4 == 0x70 && b5 == 0x01) "D552" else if (b4 == 0x02 && b5 == 0xAA) "D226" else "D552"
                    return true
                }
            }
            subtype = "D552"
            return true
        }

        fun postTransmitDelayMs(pattern: IntArray): Int {
            var totalUs = 0
            for (v in pattern) totalUs += maxOf(0, v)
            return maxOf(80, ((totalUs + 500) / 1000) + 25)
        }

        fun encode(freqHz: Int, pattern: IntArray): List<ByteArray>? {
            if (pattern.isEmpty()) return null
            val pulses = mutableListOf<Pair<Int, Int>>()
            var idx = 0
            while (idx < pattern.size) {
                val onUs = maxOf(0, pattern[idx])
                val offUs = if (idx + 1 < pattern.size) maxOf(0, pattern[idx + 1]) else 10000
                pulses.add(Pair(onUs, offUs))
                idx += 2
            }

            val raw = compressPulses(pulses)
            val payload = if (subtype == "D226") encodeD226Payload(raw) else raw

            val f = freqHz + 0x7FFFF
            val msg = mutableListOf<Byte>()
            msg.add(0xFF.toByte()); msg.add(0xFF.toByte()); msg.add(0xFF.toByte()); msg.add(0xFF.toByte())
            msg.add(mangleByte(f shr 8))
            msg.add(mangleByte(f shr 16))
            msg.add(mangleByte(f))
            msg.add(mangleByte(payload.size shr 8))
            msg.add(mangleByte(payload.size))
            for (b in payload) msg.add(b)

            val frames = mutableListOf<ByteArray>()
            var offset = 0
            while (offset < msg.size) {
                val chunk = minOf(62, msg.size - offset)
                val frame = ByteArray(if (chunk == 62) 63 else chunk)
                for (j in 0 until chunk) {
                    frame[j] = msg[offset + j]
                }
                if (chunk == 62) {
                    frame[62] = checksum62(frame)
                }
                frames.add(frame)
                offset += chunk
            }
            return frames
        }

        private fun compressPulses(pulses: List<Pair<Int, Int>>): ByteArray {
            if (pulses.isEmpty()) return ByteArray(0)
            val freq = mutableMapOf<Pair<Int, Int>, Int>()
            for (p in pulses) freq[p] = (freq[p] ?: 0) + 1
            val sorted = freq.entries.sortedByDescending { it.value }
            val p1 = sorted[0].key
            val p2 = if (sorted.size > 1) sorted[1].key else p1

            val out = mutableListOf<Byte>()
            compressValueUs(p2.first, out)
            compressValueUs(p2.second, out)
            compressValueUs(p1.first, out)
            compressValueUs(p1.second, out)
            out.add(0xFF.toByte()); out.add(0xFF.toByte()); out.add(0xFF.toByte())

            for (p in pulses) {
                if (p == p1) {
                    out.add(0x00.toByte())
                } else if (p == p2) {
                    out.add(0x01.toByte())
                } else {
                    compressValueUs(p.first, out)
                    compressValueUs(p.second, out)
                }
            }
            return out.toByteArray()
        }

        private fun compressValueUs(valueUs: Int, out: MutableList<Byte>) {
            if (valueUs <= 2032) {
                val q = if (valueUs <= 1) valueUs else (valueUs / 16.0f).roundToInt()
                out.add((q and 0xFF).toByte())
                return
            }
            var v = valueUs
            while (true) {
                var b = v and 0x7F
                v = v ushr 7
                if (v != 0) b = b or 0x80
                if ((b and 0xFF) == 0xFF) b = 0xFE
                out.add((b and 0xFF).toByte())
                if (v == 0) break
            }
        }

        private fun encodeD226Payload(raw: ByteArray): ByteArray {
            if (raw.isEmpty()) return raw
            val freq = IntArray(256)
            for (b in raw) freq[b.toInt() and 0xFF]++

            class Node(val symbol: Int?, val weight: Int, val left: Node? = null, val right: Node? = null) : Comparable<Node> {
                override fun compareTo(other: Node): Int = this.weight.compareTo(other.weight)
            }

            val pq = PriorityQueue<Node>()
            for (i in 0 until 256) {
                if (freq[i] > 0) pq.add(Node(i, freq[i]))
            }
            if (pq.isEmpty()) return raw
            while (pq.size > 1) {
                val left = pq.poll()!!
                val right = pq.poll()!!
                pq.add(Node(null, left.weight + right.weight, left, right))
            }

            val codeMap = mutableMapOf<Int, String>()
            fun buildCodes(n: Node, prefix: String) {
                if (n.symbol != null) {
                    codeMap[n.symbol] = if (prefix.isEmpty()) "0" else prefix
                    return
                }
                n.left?.let { buildCodes(it, prefix + "0") }
                n.right?.let { buildCodes(it, prefix + "1") }
            }
            buildCodes(pq.peek()!!, "")

            val out = mutableListOf<Byte>()
            val numCodes = codeMap.size
            out.add(((numCodes shr 8) and 0xFF).toByte())
            out.add((numCodes and 0xFF).toByte())
            for ((sym, _) in codeMap.entries.sortedBy { it.key }) {
                val w = freq[sym]
                out.add((sym and 0xFF).toByte())
                out.add(((w shr 8) and 0xFF).toByte())
                out.add((w and 0xFF).toByte())
            }

            val bitStr = StringBuilder()
            for (b in raw) {
                bitStr.append(codeMap[b.toInt() and 0xFF] ?: "0")
            }
            val tailBits = bitStr.length % 8
            if (tailBits > 0) {
                for (k in 0 until (8 - tailBits)) bitStr.append("0")
            }
            out.add((tailBits and 0xFF).toByte())
            var idx = 0
            while (idx < bitStr.length) {
                val b = bitStr.substring(idx, idx + 8).toInt(2)
                out.add((b and 0xFF).toByte())
                idx += 8
            }
            return out.toByteArray()
        }

        private fun mangleByte(v: Int): Byte {
            var value = v and 0xFF
            var reversed = 0
            for (i in 0 until 8) {
                reversed = (reversed shl 1) or (value and 1)
                value = value shr 1
            }
            return (reversed.inv() and 0xFF).toByte()
        }

        private fun checksum62(buf: ByteArray): Byte {
            var total = 0
            for (i in 0 until 62) {
                total += buf[i].toInt() and 0xFF
            }
            val x = (total and 0xF0) or ((total shr 8) and 0x0F)
            return mangleByte(x)
        }
    }

    private fun drain(conn: UsbDeviceConnection?, inEp: UsbEndpoint?, timeoutMs: Int) {
        if (conn == null || inEp == null) return
        val size = maxOf(64, inEp.maxPacketSize)
        val buf = ByteArray(size)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val res = conn.bulkTransfer(inEp, buf, size, 15)
            if (res <= 0) break
        }
    }
}
