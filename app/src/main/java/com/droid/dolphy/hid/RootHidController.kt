package com.droid.dolphy.hid

import android.util.Log
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

class RootHidController : HidController {
    private var modifierByte: Byte = 0x00
    private var keyBytes = mutableListOf<Byte>()
    private var mouseButtonByte: Byte = 0x00

    private val KEYBOARD_DEV = "/dev/hidg0"
    private val MOUSE_DEV = "/dev/hidg1"

    enum class UsbLinkStatus {
        NO_ROOT,
        SETUP,
        DISCONNECTED,
        CONNECTED,
    }

    override fun checkDevices(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -e $KEYBOARD_DEV && echo exists || echo missing"))
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            val exists = result == "exists"

            if (!exists) {
                Log.w("RootHidController", "HID devices not found, attempting automatic setup...")
                return setupHidGadget()
            }

            exists
        } catch (e: Exception) {
            Log.e("RootHidController", "Failed to check HID devices", e)
            false
        }
    }

    fun hasHidNodes(): Boolean = suTestExists(KEYBOARD_DEV) && suTestExists(MOUSE_DEV)

    
    fun probeLinkStatus(hasRoot: Boolean): UsbLinkStatus {
        if (!hasRoot) return UsbLinkStatus.NO_ROOT
        return try {
            if (!hasHidNodes()) return UsbLinkStatus.DISCONNECTED
            val configured = suShell(
                "if [ -f /sys/class/android_usb/android0/state ]; then " +
                    "cat /sys/class/android_usb/android0/state; " +
                    "elif [ -f /sys/kernel/config/usb_gadget/dolphy_hid/UDC ]; then " +
                    "U=\$(cat /sys/kernel/config/usb_gadget/dolphy_hid/UDC 2>/dev/null); " +
                    "if [ -n \"\$U\" ]; then echo CONFIGURED; else echo DISCONNECTED; fi; " +
                    "else echo CONFIGURED; fi",
            ).trim().uppercase()
            if (configured.contains("CONFIGURED") || configured.contains("CONNECTED")) {
                UsbLinkStatus.CONNECTED
            } else {
                UsbLinkStatus.DISCONNECTED
            }
        } catch (e: Exception) {
            Log.w("RootHidController", "probeLinkStatus", e)
            UsbLinkStatus.DISCONNECTED
        }
    }

    
    fun wakeAsMouse() {
        try {
            mouseMove(2, 0)
            Thread.sleep(30)
            mouseMove(-2, 0)
        } catch (e: Exception) {
            Log.w("RootHidController", "wakeAsMouse", e)
        }
    }

    private fun suTestExists(path: String): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -e $path && echo exists || echo missing"))
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out == "exists"
        } catch (_: Exception) {
            false
        }
    }

    private fun suShell(script: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out
        } catch (e: Exception) {
            ""
        }
    }

    fun setupHidGadget(): Boolean {
        return try {
            Log.i("RootHidController", "Starting USB Gadget HID setup...")

            val commands = """
                # Mount configfs if not mounted
                mount -t configfs none /sys/kernel/config 2>/dev/null || true

                # Create gadget directory
                cd /sys/kernel/config/usb_gadget
                mkdir -p dolphy_hid
                cd dolphy_hid

                # Set USB IDs
                echo 0x1d6b > idVendor
                echo 0x0104 > idProduct
                echo 0x0100 > bcdDevice
                echo 0x0200 > bcdUSB

                # Create strings
                mkdir -p strings/0x409
                echo "Dolphy" > strings/0x409/manufacturer
                echo "Dolphy HID Device" > strings/0x409/product
                echo "123456" > strings/0x409/serialnumber

                # Create configuration
                mkdir -p configs/c.1
                mkdir -p configs/c.1/strings/0x409
                echo "HID Config" > configs/c.1/strings/0x409/configuration
                echo 120 > configs/c.1/MaxPower

                # Create keyboard function
                mkdir -p functions/hid.usb0
                echo 1 > functions/hid.usb0/protocol
                echo 1 > functions/hid.usb0/subclass
                echo 8 > functions/hid.usb0/report_length
                echo -ne '\x05\x01\x09\x06\xa1\x01\x05\x07\x19\xe0\x29\xe7\x15\x00\x25\x01\x75\x01\x95\x08\x81\x02\x95\x01\x75\x08\x81\x01\x95\x05\x75\x01\x05\x08\x19\x01\x29\x05\x91\x02\x95\x01\x75\x03\x91\x01\x95\x06\x75\x08\x15\x00\x25\x65\x05\x07\x19\x00\x29\x65\x81\x00\xc0' > functions/hid.usb0/report_desc
                ln -s functions/hid.usb0 configs/c.1/

                # Create mouse function
                mkdir -p functions/hid.usb1
                echo 2 > functions/hid.usb1/protocol
                echo 1 > functions/hid.usb1/subclass
                echo 5 > functions/hid.usb1/report_length
                echo -ne '\x05\x01\x09\x02\xa1\x01\x09\x01\xa1\x00\x05\x09\x19\x01\x29\x03\x15\x00\x25\x01\x95\x03\x75\x01\x81\x02\x95\x01\x75\x05\x81\x01\x05\x01\x09\x30\x09\x31\x09\x38\x15\x81\x25\x7f\x75\x08\x95\x03\x81\x06\xc0\xc0' > functions/hid.usb1/report_desc
                ln -s functions/hid.usb1 configs/c.1/

                # Create media keys function
                mkdir -p functions/hid.usb2
                echo 0 > functions/hid.usb2/protocol
                echo 0 > functions/hid.usb2/subclass
                echo 1 > functions/hid.usb2/report_length
                echo -ne '\x05\x0c\x09\x01\xa1\x01\x15\x00\x25\x01\x75\x01\x95\x08\x09\xe9\x09\xea\x09\xe2\x09\xcd\x09\xb5\x09\xb6\x09\xb7\x09\xb8\x81\x02\xc0' > functions/hid.usb2/report_desc
                ln -s functions/hid.usb2 configs/c.1/

                # Find and bind to UDC
                UDC=$(ls /sys/class/udc | head -n 1)
                if [ -n "${'$'}UDC" ]; then
                    echo "${'$'}UDC" > UDC
                    echo "USB Gadget bound to ${'$'}UDC"
                else
                    echo "No UDC found"
                    exit 1
                fi

                # Wait for devices to appear
                sleep 1

                # Set permissions
                chmod 666 /dev/hidg0 2>/dev/null || true
                chmod 666 /dev/hidg1 2>/dev/null || true
                chmod 666 /dev/hidg2 2>/dev/null || true

                echo "Setup complete"
            """.trimIndent()

            val process = Runtime.getRuntime().exec(arrayOf("su"))
            val os = DataOutputStream(process.outputStream)
            os.writeBytes(commands)
            os.writeBytes("\nexit\n")
            os.flush()

            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()

            Log.i("RootHidController", "Setup output: $output")
            if (error.isNotEmpty()) {
                Log.w("RootHidController", "Setup warnings: $error")
            }

            if (exitCode == 0) {

                Thread.sleep(500)
                val verifyProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -e $KEYBOARD_DEV && echo exists || echo missing"))
                val verifyResult = verifyProcess.inputStream.bufferedReader().readText().trim()
                verifyProcess.waitFor()

                val success = verifyResult == "exists"
                if (success) {
                    Log.i("RootHidController", "USB Gadget HID setup successful")
                } else {
                    Log.e("RootHidController", "USB Gadget HID setup failed - devices not created")
                }
                success
            } else {
                Log.e("RootHidController", "USB Gadget HID setup failed with exit code $exitCode")
                false
            }
        } catch (e: Exception) {
            Log.e("RootHidController", "Failed to setup USB Gadget HID", e)
            false
        }
    }

    private fun sendKeyboardReport() {
        val report = ByteArray(8)
        report[0] = modifierByte
        report[1] = 0x00
        for (i in 0 until 6) {
            if (i < keyBytes.size) {
                report[i + 2] = keyBytes[i]
            }
        }
        writeToDevice(KEYBOARD_DEV, report)
    }

    private fun sendMouseReport(
        buttonByte: Byte? = null,
        deltaX: Byte = 0,
        deltaY: Byte = 0,
        deltaWheel: Byte = 0,
        deltaPan: Byte = 0
    ) {
        val report = byteArrayOf(
            buttonByte ?: mouseButtonByte,
            deltaX,
            deltaY,
            deltaWheel,
            deltaPan
        )
        writeToDevice(MOUSE_DEV, report)
    }

    private fun writeToDevice(devicePath: String, report: ByteArray) {
        try {

            val checkProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -e $devicePath && echo exists || echo missing"))
            val checkResult = checkProcess.inputStream.bufferedReader().readText().trim()
            checkProcess.waitFor()

            if (checkResult != "exists") {
                Log.e("RootHidController", "Device $devicePath does not exist. HID gadget not configured.")
                return
            }


            val process = Runtime.getRuntime().exec(arrayOf("su"))
            val os = DataOutputStream(process.outputStream)


            val tempFile = "/data/local/tmp/hid_report_${System.currentTimeMillis()}"


            val base64Data = android.util.Base64.encodeToString(report, android.util.Base64.NO_WRAP)
            os.writeBytes("echo '$base64Data' | base64 -d > $tempFile\n")
            os.writeBytes("cat $tempFile > $devicePath\n")
            os.writeBytes("rm $tempFile\n")
            os.writeBytes("exit\n")
            os.flush()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val error = process.errorStream.bufferedReader().readText()
                Log.e("RootHidController", "Failed to write to $devicePath: $error")
            }
        } catch (e: Exception) {
            Log.e("RootHidController", "Failed to write to $devicePath", e)
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
        if (horizontal) {
            sendMouseReport(deltaPan = delta.coerceIn(-128, 127).toByte())
        } else {
            sendMouseReport(deltaWheel = delta.coerceIn(-128, 127).toByte())
        }
    }

    private var mediaByte: Byte = 0x00
    private val MEDIA_DEV = "/dev/hidg2"

    private fun sendMediaReport() {
        writeToDevice(MEDIA_DEV, byteArrayOf(mediaByte))
    }

    override fun mediaDown(key: String) {
        val code = keyCodes[key] ?: return
        mediaByte = mediaByte or code
        sendMediaReport()
    }

    override fun mediaUp(key: String) {
        val code = keyCodes[key] ?: return
        mediaByte = mediaByte and code.inv()
        sendMediaReport()
    }
}

