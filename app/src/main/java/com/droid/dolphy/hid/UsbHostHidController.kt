package com.droid.dolphy.hid

import android.content.Context
import android.hardware.usb.*
import android.util.Log
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or






class UsbHostHidController(private val context: Context) : HidController {
    private val TAG = "UsbHostHidController"

    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpoint: UsbEndpoint? = null
    private var usbConnection: UsbDeviceConnection? = null

    private var modifierByte: Byte = 0x00
    private var keyBytes = mutableListOf<Byte>()
    private var mouseButtonByte: Byte = 0x00

    init {
        usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    }

    override fun checkDevices(): Boolean {
        if (usbManager == null) {
            Log.e(TAG, "USB Manager not available")
            return false
        }

        val deviceList = usbManager?.deviceList
        if (deviceList.isNullOrEmpty()) {
            Log.e(TAG, "No USB devices found. Connect via OTG cable.")
            return false
        }


        for ((_, device) in deviceList) {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_HID) {
                    Log.d(TAG, "Found HID device: ${device.deviceName}")
                    return true
                }
            }
        }

        Log.e(TAG, "No HID devices found")
        return false
    }

    fun connectToDevice(device: UsbDevice): Boolean {
        try {
            usbDevice = device


            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_HID) {
                    usbInterface = intf
                    break
                }
            }

            if (usbInterface == null) {
                Log.e(TAG, "HID interface not found")
                return false
            }


            usbInterface?.let { intf ->
                for (i in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(i)
                    if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        usbEndpoint = ep
                        break
                    }
                }
            }

            if (usbEndpoint == null) {
                Log.e(TAG, "Output endpoint not found")
                return false
            }


            usbConnection = usbManager?.openDevice(device)
            if (usbConnection == null) {
                Log.e(TAG, "Failed to open USB connection")
                return false
            }


            val claimed = usbConnection?.claimInterface(usbInterface, true) ?: false
            if (!claimed) {
                Log.e(TAG, "Failed to claim interface")
                return false
            }

            Log.d(TAG, "Successfully connected to USB HID device")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device", e)
            return false
        }
    }

    fun disconnect() {
        try {
            usbConnection?.let { connection ->
                usbInterface?.let { intf ->
                    connection.releaseInterface(intf)
                }
                connection.close()
            }
            usbConnection = null
            usbInterface = null
            usbEndpoint = null
            usbDevice = null
            Log.d(TAG, "Disconnected from USB device")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
    }

    fun getAvailableDevices(): List<UsbDevice> {
        val devices = mutableListOf<UsbDevice>()
        usbManager?.deviceList?.values?.forEach { device ->
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_HID) {
                    devices.add(device)
                    break
                }
            }
        }
        return devices
    }

    private fun sendKeyboardReport() {
        if (usbConnection == null || usbEndpoint == null) {
            Log.e(TAG, "Not connected to USB device")
            return
        }

        val buffer = ByteBuffer.allocate(8)
        buffer.put(modifierByte)
        buffer.put(0x00.toByte())


        for (i in 0 until 6) {
            if (i < keyBytes.size) {
                buffer.put(keyBytes[i])
            } else {
                buffer.put(0x00.toByte())
            }
        }

        val result = usbConnection?.bulkTransfer(
            usbEndpoint,
            buffer.array(),
            buffer.capacity(),
            1000
        )

        if (result != null && result < 0) {
            Log.e(TAG, "Failed to send keyboard report: $result")
        }
    }

    private fun sendMouseReport(
        buttonByte: Byte? = null,
        deltaX: Byte = 0,
        deltaY: Byte = 0,
        deltaWheel: Byte = 0
    ) {
        if (usbConnection == null || usbEndpoint == null) {
            Log.e(TAG, "Not connected to USB device")
            return
        }

        val buffer = ByteBuffer.allocate(5)
        buffer.put(buttonByte ?: mouseButtonByte)
        buffer.put(deltaX)
        buffer.put(deltaY)
        buffer.put(deltaWheel)
        buffer.put(0x00.toByte())

        val result = usbConnection?.bulkTransfer(
            usbEndpoint,
            buffer.array(),
            buffer.capacity(),
            1000
        )

        if (result != null && result < 0) {
            Log.e(TAG, "Failed to send mouse report: $result")
        }
    }

    override fun keyDown(key: String) {
        val code = keyCodes[key] ?: return
        if (!keyBytes.contains(code)) {
            keyBytes.add(code)
            sendKeyboardReport()
        }
    }

    override fun keyUp(key: String) {
        val code = keyCodes[key] ?: return
        if (keyBytes.remove(code)) {
            sendKeyboardReport()
        }
    }

    override fun modifierDown(key: String) {
        val code = keyCodes[key] ?: return
        modifierByte = modifierByte or code
        sendKeyboardReport()
    }

    override fun modifierUp(key: String) {
        val code = keyCodes[key] ?: return
        modifierByte = modifierByte and code.inv()
        sendKeyboardReport()
    }

    override fun releaseAllKeyboard() {
        modifierByte = 0x00
        keyBytes.clear()
        sendKeyboardReport()
    }

    override fun mouseMove(deltaX: Int, deltaY: Int) {
        sendMouseReport(
            deltaX = deltaX.coerceIn(-128, 127).toByte(),
            deltaY = deltaY.coerceIn(-128, 127).toByte()
        )
    }

    override fun mouseDown(button: Int) {
        mouseButtonByte = mouseButtonByte or (1 shl button).toByte()
        sendMouseReport()
    }

    override fun mouseUp(button: Int) {
        mouseButtonByte = mouseButtonByte and (1 shl button).toByte().inv()
        sendMouseReport()
    }

    override fun mouseClick(button: Int) {
        mouseDown(button)
        Thread.sleep(20)
        mouseUp(button)
    }

    override fun releaseAllMouseButtons() {
        mouseButtonByte = 0x00
        sendMouseReport()
    }

    override fun mouseWheel(delta: Int, horizontal: Boolean) {
        sendMouseReport(deltaWheel = delta.coerceIn(-128, 127).toByte())
    }

    override fun mediaDown(key: String) {

        Log.w(TAG, "Media keys not implemented for USB Host mode")
    }

    override fun mediaUp(key: String) {
        Log.w(TAG, "Media keys not implemented for USB Host mode")
    }

    fun isConnected(): Boolean {
        return usbConnection != null && usbEndpoint != null
    }
}

