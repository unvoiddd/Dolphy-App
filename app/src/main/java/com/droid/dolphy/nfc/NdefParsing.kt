package com.droid.dolphy.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.charset.Charset

data class NdefSummary(
    val recordType: String,
    val content: String,
    val rawHex: String,
)

fun summarizeNdef(message: NdefMessage): NdefSummary {
    val rawHex = message.toByteArray().toHexString(sep = " ")
    val records = message.records?.toList().orEmpty()
    if (records.isEmpty()) {
        return NdefSummary(recordType = "Empty", content = "Пустое NDEF-сообщение", rawHex = rawHex)
    }

    val parts = mutableListOf<Pair<String, String>>()
    val types = linkedSetOf<String>()

    for (record in records) {
        val (type, content) = decodeRecord(record)
        types += type
        parts += type to content
    }

    val recordType = when {
        types.size == 1 -> types.first()
        else -> "Mixed"
    }

    val content = parts.joinToString(separator = "\n\n") { (t, c) -> "$t:\n$c" }
    return NdefSummary(recordType = recordType, content = content, rawHex = rawHex)
}

private fun decodeRecord(record: NdefRecord): Pair<String, String> {
    return try {
        when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI) -> {
                "URL" to decodeUri(record.payload)
            }
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                "Text" to decodeText(record.payload)
            }
            record.tnf == NdefRecord.TNF_MIME_MEDIA -> {
                val mime = record.type.toString(Charsets.US_ASCII).lowercase()
                when {
                    mime == "application/vnd.wfa.wsc" || mime.contains("wfa.wsc") -> "Wi‑Fi" to decodeUtf8OrHex(record.payload)
                    mime.contains("vcard") -> "vCard" to decodeUtf8OrHex(record.payload)
                    mime == "text/plain" -> "Text" to decodeUtf8OrHex(record.payload)
                    else -> "MIME ($mime)" to decodeUtf8OrHex(record.payload)
                }
            }
            record.tnf == NdefRecord.TNF_ABSOLUTE_URI -> {
                "Absolute URI" to record.type.toString(Charsets.UTF_8)
            }
            else -> {
                "Unknown" to ("TNF=${record.tnf}, type=${record.type.toHexString(sep = " ")}\npayload=${record.payload.toHexString(sep = " ")}")
            }
        }
    } catch (e: Exception) {
        "Unknown" to ("Ошибка декодирования: ${e.message ?: e.javaClass.simpleName}\npayload=${record.payload.toHexString(sep = " ")}")
    }
}

private fun decodeText(payload: ByteArray): String {
    if (payload.isEmpty()) return ""
    val status = payload[0].toInt() and 0xFF
    val isUtf16 = (status and 0x80) != 0
    val langLen = status and 0x3F
    val textStart = 1 + langLen
    if (textStart > payload.size) return ""
    val textBytes = payload.copyOfRange(textStart, payload.size)
    val charset: Charset = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
    return textBytes.toString(charset)
}

private fun decodeUri(payload: ByteArray): String {
    if (payload.isEmpty()) return ""
    val prefix = when (payload[0].toInt() and 0xFF) {
        0x00 -> ""
        0x01 -> "http://www."
        0x02 -> "https://www."
        0x03 -> "http://"
        0x04 -> "https://"
        0x05 -> "tel:"
        0x06 -> "mailto:"
        0x07 -> "ftp://anonymous:anonymous@"
        0x08 -> "ftp://ftp."
        0x09 -> "ftps://"
        0x0A -> "sftp://"
        0x0B -> "smb://"
        0x0C -> "nfs://"
        0x0D -> "ftp://"
        0x0E -> "dav://"
        0x0F -> "news:"
        0x10 -> "telnet://"
        0x11 -> "imap:"
        0x12 -> "rtsp://"
        0x13 -> "urn:"
        0x14 -> "pop:"
        0x15 -> "sip:"
        0x16 -> "sips:"
        0x17 -> "tftp:"
        0x18 -> "btspp://"
        0x19 -> "btl2cap://"
        0x1A -> "btgoep://"
        0x1B -> "tcpobex://"
        0x1C -> "irdaobex://"
        0x1D -> "file://"
        0x1E -> "urn:epc:id:"
        0x1F -> "urn:epc:tag:"
        0x20 -> "urn:epc:pat:"
        0x21 -> "urn:epc:raw:"
        0x22 -> "urn:epc:"
        0x23 -> "urn:nfc:"
        else -> ""
    }
    val rest = payload.copyOfRange(1, payload.size).toString(Charsets.UTF_8)
    return prefix + rest
}

private fun decodeUtf8OrHex(payload: ByteArray): String {
    if (payload.isEmpty()) return ""
    return try {
        val s = payload.toString(Charsets.UTF_8)
        if (s.any { it == '\uFFFD' }) payload.toHexString(sep = " ") else s
    } catch (_: Exception) {
        payload.toHexString(sep = " ")
    }
}


