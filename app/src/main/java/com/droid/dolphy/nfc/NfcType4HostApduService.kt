package com.droid.dolphy.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.nio.charset.StandardCharsets

class NfcType4HostApduService : HostApduService() {
    private var selectedFile: ByteArray? = null

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return SW_UNKNOWN

        return when {
            commandApdu.startsWith(SELECT_NDEF_APP) -> {
                selectedFile = null
                SW_OK
            }
            commandApdu.startsWith(SELECT_CC_FILE) -> {
                selectedFile = CC_FILE
                SW_OK
            }
            commandApdu.startsWith(SELECT_NDEF_FILE) -> {
                val url = if (NfcAudioSpoofState.isActive.value) {
                    NfcAudioSpoofState.spoofUrl.value ?: "https://example.com"
                } else {
                    val tag = NfcTagEmulationStore.getActiveTag(this)
                    tag?.url?.takeIf { it.isNotBlank() } ?: "https://example.com"
                }
                selectedFile = buildNdefFile(url)
                SW_OK
            }
            isReadBinary(commandApdu) -> {
                readBinary(commandApdu)
            }
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = null
        Log.d(TAG, "NFC link deactivated: $reason")
    }

    private fun readBinary(apdu: ByteArray): ByteArray {
        val file = selectedFile ?: run {
            val url = if (NfcAudioSpoofState.isActive.value) {
                NfcAudioSpoofState.spoofUrl.value ?: "https://example.com"
            } else {
                val tag = NfcTagEmulationStore.getActiveTag(this)
                tag?.url?.takeIf { it.isNotBlank() } ?: "https://example.com"
            }
            buildNdefFile(url)
        }


        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)


        val le = if (apdu.size >= 5) apdu[4].toInt() and 0xFF else 0
        val requested = if (le == 0) 256 else le

        if (offset >= file.size) return SW_WRONG_P1P2

        val available = file.size - offset
        val count = minOf(requested, available)
        val payload = file.copyOfRange(offset, offset + count)

        return payload + SW_OK
    }

    private fun buildNdefFile(url: String): ByteArray {
        val ndefMessage = buildUriNdefMessage(url)
        val nlen = byteArrayOf(
            ((ndefMessage.size shr 8) and 0xFF).toByte(),
            (ndefMessage.size and 0xFF).toByte()
        )
        return nlen + ndefMessage
    }

    private fun buildUriNdefMessage(url: String): ByteArray {
        val (prefixCode, rest) = when {
            url.startsWith("http://www.") -> 0x01.toByte() to url.removePrefix("http://www.")
            url.startsWith("https://www.") -> 0x02.toByte() to url.removePrefix("https://www.")
            url.startsWith("http://") -> 0x03.toByte() to url.removePrefix("http://")
            url.startsWith("https://") -> 0x04.toByte() to url.removePrefix("https://")
            else -> 0x00.toByte() to url
        }
        val uriPayload = byteArrayOf(prefixCode) + rest.toByteArray(StandardCharsets.UTF_8)


        val isShort = uriPayload.size <= 255
        val header = if (isShort) {
            byteArrayOf(0xD1.toByte(), 0x01.toByte(), uriPayload.size.toByte(), 0x55.toByte())
        } else {
            byteArrayOf(
                0xC1.toByte(), 0x01.toByte(),
                ((uriPayload.size shr 24) and 0xFF).toByte(),
                ((uriPayload.size shr 16) and 0xFF).toByte(),
                ((uriPayload.size shr 8) and 0xFF).toByte(),
                (uriPayload.size and 0xFF).toByte(),
                0x55.toByte()
            )
        }
        return header + uriPayload
    }

    private fun isReadBinary(apdu: ByteArray): Boolean {
        return apdu.size >= 4 && apdu[0] == 0x00.toByte() && apdu[1] == 0xB0.toByte()
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }

    companion object {
        private const val TAG = "NfcType4Service"

        private val SELECT_NDEF_APP = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
            0x07.toByte(), 0xD2.toByte(), 0x76.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x85.toByte(), 0x01.toByte(), 0x01.toByte()
        )
        private val SELECT_CC_FILE = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x0C.toByte(), 0x02.toByte(), 0xE1.toByte(), 0x03.toByte()
        )
        private val SELECT_NDEF_FILE = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x0C.toByte(), 0x02.toByte(), 0xE1.toByte(), 0x04.toByte()
        )
        private val CC_FILE = byteArrayOf(
            0x00.toByte(), 0x0F.toByte(),
            0x20.toByte(),
            0x00.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0xFF.toByte(),
            0x04.toByte(), 0x06.toByte(),
            0xE1.toByte(), 0x04.toByte(),
            0x04.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte()
        )

        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val SW_UNKNOWN = byteArrayOf(0x6F.toByte(), 0x00.toByte())
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())
        private val SW_WRONG_P1P2 = byteArrayOf(0x6B.toByte(), 0x00.toByte())
    }
}

