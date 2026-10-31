package com.droid.dolphy.bridge.flipper

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.ByteArrayOutputStream

/**
 * CDC-ACM implementation for the Flipper Zero USB serial port.
 *
 * Permission must be granted by the activity before [open]. This class does not
 * request permissions itself so it stays UI-free and testable.
 */
class FlipperUsbCdcTransport(
    context: Context,
    private val device: UsbDevice,
) : FlipperTransport {

    private val usbManager = context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val lock = Any()
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var readEndpoint: UsbEndpoint? = null
    private var writeEndpoint: UsbEndpoint? = null
    private val pending = ByteArrayOutputStream()
    private val readBuffer = ByteArray(4096)

    override val isOpen: Boolean
        get() = synchronized(lock) { connection != null }

    fun hasPermission(): Boolean = usbManager.hasPermission(device)

    override fun open() {
        synchronized(lock) {
            check(connection == null) { "transport already open" }
            check(hasPermission()) { "USB permission not granted" }

            val endpoints = findCdcEndpoints(device)
                ?: error("no CDC bulk endpoints found")
            val opened = usbManager.openDevice(device)
                ?: error("UsbManager.openDevice returned null")

            if (!opened.claimInterface(endpoints.interfaceBlock, true)) {
                opened.close()
                error("Unable to claim CDC interface")
            }
            configureLine(opened, endpoints.interfaceBlock)
            connection = opened
            usbInterface = endpoints.interfaceBlock
            readEndpoint = endpoints.input
            writeEndpoint = endpoints.output
            pending.reset()
        }
    }

    override fun writeLine(line: String) {
        val bytes = (line + "\r\n").toByteArray(Charsets.UTF_8)
        val conn: UsbDeviceConnection
        val endpoint: UsbEndpoint
        synchronized(lock) {
            conn = connection ?: error("transport is not open")
            endpoint = writeEndpoint ?: error("missing write endpoint")
        }
        val written = conn.bulkTransfer(endpoint, bytes, bytes.size, WRITE_TIMEOUT_MS)
        if (written < 0) error("USB write failed")
    }

    override fun readLine(timeoutMillis: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMillis.coerceAtLeast(1)
        synchronized(lock) {
            val conn = connection ?: return null
            val endpoint = readEndpoint ?: return null
            while (System.currentTimeMillis() < deadline) {
                extractLine()?.let { return it }
                val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1).toInt()
                val read = conn.bulkTransfer(endpoint, readBuffer, readBuffer.size, remaining)
                if (read > 0) {
                    pending.write(readBuffer, 0, read)
                    extractLine()?.let { return it }
                } else if (read < 0) {
                    return null
                }
            }
            return extractLine()
        }
    }

    override fun close() {
        synchronized(lock) {
            val conn = connection
            val iface = usbInterface
            if (conn != null && iface != null) {
                runCatching { conn.releaseInterface(iface) }
            }
            conn?.close()
            connection = null
            usbInterface = null
            readEndpoint = null
            writeEndpoint = null
            pending.reset()
        }
    }

    private fun extractLine(): String? {
        val bytes = pending.toByteArray()
        val newline = bytes.indexOf('\n'.code.toByte())
        if (newline < 0) return null
        val line = bytes.copyOfRange(0, newline).toString(Charsets.UTF_8).trimEnd('\r')
        pending.reset()
        pending.write(bytes, newline + 1, bytes.size - newline - 1)
        return line
    }

    private fun configureLine(connection: UsbDeviceConnection, iface: UsbInterface) {
        val lineCoding = byteArrayOf(
            0x00, 0xC2.toByte(), 0x01, 0x00, // 115200 baud, little-endian
            0x00,                            // 1 stop bit
            0x00,                            // no parity
            0x08,                            // 8 data bits
        )
        connection.controlTransfer(
            CDC_SET_LINE_CODING_REQUEST_TYPE,
            CDC_SET_LINE_CODING,
            0,
            iface.id,
            lineCoding,
            lineCoding.size,
            CONTROL_TIMEOUT_MS,
        )
        connection.controlTransfer(
            CDC_SET_CONTROL_LINE_STATE_REQUEST_TYPE,
            CDC_SET_CONTROL_LINE_STATE,
            0x03, // DTR | RTS
            iface.id,
            null,
            0,
            CONTROL_TIMEOUT_MS,
        )
    }

    private data class CdcEndpoints(
        val interfaceBlock: UsbInterface,
        val input: UsbEndpoint,
        val output: UsbEndpoint,
    )

    companion object {
        private const val WRITE_TIMEOUT_MS = 1_000
        private const val CONTROL_TIMEOUT_MS = 1_000
        private const val CDC_SET_LINE_CODING = 0x20
        private const val CDC_SET_CONTROL_LINE_STATE = 0x22
        private const val CDC_SET_LINE_CODING_REQUEST_TYPE = 0x21
        private const val CDC_SET_CONTROL_LINE_STATE_REQUEST_TYPE = 0x21

        fun isFlipper(device: UsbDevice): Boolean =
            device.vendorId == FlipperCliAdapter.FLIPPER_VENDOR_ID &&
                device.productId == FlipperCliAdapter.FLIPPER_PRODUCT_ID

        fun findFlipper(context: Context): UsbDevice? {
            val manager = context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
            return manager.deviceList.values.firstOrNull { isFlipper(it) }
                ?: manager.deviceList.values.firstOrNull { hasCdcInterface(it) }
        }

        fun connectedDevices(context: Context): List<UsbDevice> {
            val manager = context.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
            return manager.deviceList.values.filter { isFlipper(it) || hasCdcInterface(it) }
        }

        private fun hasCdcInterface(device: UsbDevice): Boolean =
            findCdcEndpoints(device) != null

        private fun findCdcEndpoints(device: UsbDevice): CdcEndpoints? {
            for (index in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(index)
                var input: UsbEndpoint? = null
                var output: UsbEndpoint? = null
                for (endpointIndex in 0 until usbInterface.endpointCount) {
                    val endpoint = usbInterface.getEndpoint(endpointIndex)
                    if (endpoint.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                        input = endpoint
                    } else {
                        output = endpoint
                    }
                }
                if (input != null && output != null) {
                    return CdcEndpoints(usbInterface, input, output)
                }
            }
            return null
        }
    }
}
