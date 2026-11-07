package com.droid.dolphy.hid

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import java.util.Timer
import java.util.TimerTask
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

class Connection(
    private val context: Context,
    private var hostDevice: BluetoothDevice? = null,
    private val onCapsLock: (Boolean) -> Unit
) : HidController {
    private var service: BluetoothHidDevice? = null

    fun injectProxy(proxy: BluetoothHidDevice?) {
        service = proxy
        Log.d("Connection", "injectProxy called: proxy=${proxy != null}, hostDevice=${hostDevice?.address}")
    }
    fun onAppRegistered(proxy: BluetoothHidDevice?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        service = proxy
        if (proxy == null || hostDevice == null) {
            Log.w("Connection", "Cannot connect: proxy or hostDevice is null")
            return
        }
        Log.d("Connection", "Attempting HID connection to ${hostDevice?.address}")
        try {
            try { proxy.connect(hostDevice) } catch (error: SecurityException) { Log.w("Connection", "Bluetooth permission revoked", error) }
        } catch (e: Exception) {
            Log.e("Connection", "Failed to connect", e)
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                Toast.makeText(context, "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
        if (device.address != hostDevice?.address) return

        val handler = Handler(Looper.getMainLooper())
        val stateName = when (state) {
            BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
            BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
            BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
            BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
            else -> "UNKNOWN($state)"
        }
        Log.d("Connection", "State changed to $stateName for ${device.address}")

        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                hostDevice = device
                handler.post {
                    Toast.makeText(context, "Connected: ${try { device.name } catch (_: SecurityException) { device.address }}", Toast.LENGTH_SHORT).show()
                }
            }

            BluetoothProfile.STATE_CONNECTING -> {
                handler.post {
                    Toast.makeText(context, "Connecting...", Toast.LENGTH_SHORT).show()
                }
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                val wasConnected = hostDevice != null
                hostDevice = null
                handler.post {
                    val msg = if (wasConnected) "Disconnected" else "Connection failed"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }

            BluetoothProfile.STATE_DISCONNECTING -> {
            }
        }
    }

    fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray) {
        Log.d("", "interrupt $reportId $data")
        if (reportId == 0x01.toByte()) onCapsLock((data[0] and 0x02) > 0)
    }
    fun close() {
        flushScheduled = false
        mouseHandler.removeCallbacksAndMessages(null)
        mouseHandlerThread.quitSafely()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                service?.disconnect(hostDevice)
            } catch (e: SecurityException) {
                Log.e("Connection", "SecurityException during disconnect", e)
            }
        }
    }

    private var modifierByte: Byte = 0x00
    private var keyBytes = mutableListOf<Byte>()
    private fun sendKeyboardReport(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        if (service == null) {
            Log.w("Connection", "sendKeyboardReport: service is NULL!")
            return false
        }
        val report = ByteArray(2 + 6)
        report[0] = modifierByte
        for (i in 0 until 6) {
            if (i < keyBytes.size)
                report[i + 2] = keyBytes[i]
        }
        Log.d("Connection", "Sending keyboard report: ${report.contentToString()}")
        return try {
            service?.sendReport(hostDevice, 0x01, report) ?: false
        } catch (e: SecurityException) {
            Log.e("Connection", "SecurityException in sendKeyboardReport", e)
            false
        }
    }
    override fun keyDown(key: String) {
        val code = keyCodes[key] ?: return
        Log.d("Connection", "keyDown $key ($code)")
        synchronized(this) {
            if (keyBytes.contains(code))
                return
            keyBytes.add(code)
            sendKeyboardReport()
        }
    }
    override fun keyUp(key: String) {
        val code = keyCodes[key] ?: return
        Log.d("Connection", "keyUp $key ($code)")
        synchronized(this) {
            if (!keyBytes.contains(code))
                return
            keyBytes.remove(code)
            sendKeyboardReport()
        }
    }
    override fun releaseAllKeyboard() {
        synchronized(this) {
            modifierByte = 0x00
            keyBytes.clear()
            sendKeyboardReport()
        }
    }
    override fun modifierDown(key: String) {
        val code = keyCodes[key] ?: return
        Log.d("Connection", "modDown $key ($code)")
        synchronized(this) {
            modifierByte = modifierByte or code
            sendKeyboardReport()
        }
    }
    override fun modifierUp(key: String) {
        val code = keyCodes[key] ?: return
        Log.d("Connection", "modUp $key ($code)")
        synchronized(this) {
            modifierByte = modifierByte and code.inv()
            sendKeyboardReport()
        }
    }

    private var mouseButtonByte: Byte = 0x00

    private var accumulatedDeltaX: Int = 0
    private var accumulatedDeltaY: Int = 0
    private var flushScheduled: Boolean = false
    private val mouseHandlerThread = HandlerThread("HidMouseHandler").apply { start() }
    private val mouseHandler = Handler(mouseHandlerThread.looper)
    private val mouseFlushRunnable = object : Runnable {
        override fun run() {
            val dx: Int
            val dy: Int
            synchronized(this@Connection) {
                val reportX = accumulatedDeltaX.coerceIn(-127, 127)
                val reportY = accumulatedDeltaY.coerceIn(-127, 127)

                accumulatedDeltaX -= reportX
                accumulatedDeltaY -= reportY

                dx = reportX
                dy = reportY

                if (accumulatedDeltaX != 0 || accumulatedDeltaY != 0) {
                    mouseHandler.postDelayed(this, mouseFlushDelayMs)
                } else {
                    flushScheduled = false
                }
            }
            if (dx != 0 || dy != 0) {
                sendMouseReport(deltaX = dx.toByte(), deltaY = dy.toByte())
            }
        }
    }
    private val mouseFlushDelayMs = 4L
    private fun sendMouseReport(
        buttonByte: Byte? = null,
        deltaX: Byte = 0,
        deltaY: Byte = 0,
        deltaWheel: Byte = 0,
        deltaPan: Byte = 0
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        if (service == null) {
            Log.w("Connection", "sendMouseReport: service is NULL!")
            return false
        }
        val buttons = buttonByte ?: mouseButtonByte
        Log.d("Connection", "Sending mouse report: buttons=$buttons, dx=$deltaX, dy=$deltaY, pan=$deltaPan")
        return try {
            service?.sendReport(
                hostDevice,
                0x02,
                byteArrayOf(
                    buttons,
                    deltaX,
                    deltaY,
                    deltaWheel,
                    deltaPan
                )
            ) ?: false
        } catch (e: SecurityException) {
            Log.e("Connection", "SecurityException in sendMouseReport", e)
            false
        }
    }
    override fun mouseMove(deltaX: Int, deltaY: Int) {
        synchronized(this) {
            accumulatedDeltaX += deltaX
            accumulatedDeltaY += deltaY
            if (!flushScheduled) {
                flushScheduled = true

                mouseHandler.post(mouseFlushRunnable)
            }
        }
    }
    override fun mouseDown(button: Int) {
        Log.d("", "mouseDown $button")
        synchronized(this) {
            mouseButtonByte = mouseButtonByte or ((1 shl button).toByte())
            sendMouseReport()
        }
    }
    override fun mouseUp(button: Int) {
        Log.d("", "mouseUp $button")
        synchronized(this) {
            mouseButtonByte = mouseButtonByte and ((1 shl button).toByte()).inv()
            sendMouseReport()
        }
    }
    override fun releaseAllMouseButtons() {
        synchronized(this) {
            mouseButtonByte = 0x00
            sendMouseReport(buttonByte = 0x00)
        }
    }
    override fun mouseWheel(delta: Int, horizontal: Boolean) {
        Log.d("", "mouseWheel $delta, horizontal=$horizontal")
        if (horizontal) {
            sendMouseReport(deltaPan = delta.coerceIn(-128, 127).toByte())
        } else {
            sendMouseReport(deltaWheel = delta.coerceIn(-128, 127).toByte())
        }
    }
    override fun mouseClick(button: Int) {
        Log.d("Connection", "mouseClick $button")
        synchronized(this) {
            mouseButtonByte = mouseButtonByte or (1 shl button).toByte()
            sendMouseReport()
        }
        mouseHandler.postDelayed({
            synchronized(this@Connection) {
                mouseButtonByte = mouseButtonByte and (1 shl button).toByte().inv()
                sendMouseReport()
            }
        }, 20)
    }

    private var consumerBits: Int = 0
    private var launcherUsage: Int = 0
    private var applicationControlUsage: Int = 0
    private fun sendMediaReport(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val report = byteArrayOf(
            (consumerBits and 0xFF).toByte(),
            ((consumerBits ushr 8) and 0xFF).toByte(),
            (launcherUsage and 0xFF).toByte(),
            ((launcherUsage ushr 8) and 0xFF).toByte(),
            (applicationControlUsage and 0xFF).toByte(),
            ((applicationControlUsage ushr 8) and 0xFF).toByte(),
        )
        return try { service?.sendReport(hostDevice, 0x03, report) ?: false } catch (_: SecurityException) { false }
    }
    override fun mediaDown(key: String) {
        val code = consumerControlCodes[key] ?: return
        Log.d("Connection", "mediaDown $key ($code)")
        synchronized(this) {
            consumerBits = consumerBits or code
            sendMediaReport()
        }
    }
    override fun mediaUp(key: String) {
        val code = consumerControlCodes[key] ?: return
        Log.d("Connection", "mediaUp $key ($code)")
        synchronized(this) {
            consumerBits = consumerBits and code.inv()
            sendMediaReport()
        }
    }
    override fun consumerDown(usageMask: Int) {
        synchronized(this) {
            consumerBits = consumerBits or usageMask
            sendMediaReport()
        }
    }
    override fun consumerUp(usageMask: Int) {
        synchronized(this) {
            consumerBits = consumerBits and usageMask.inv()
            sendMediaReport()
        }
    }
    override fun launchApplication(usage: Int) {
        synchronized(this) {
            launcherUsage = usage
            sendMediaReport()
            launcherUsage = 0
            sendMediaReport()
        }
    }
    override fun controlApplication(usage: Int) {
        synchronized(this) {
            applicationControlUsage = usage
            sendMediaReport()
            applicationControlUsage = 0
            sendMediaReport()
        }
    }
}
