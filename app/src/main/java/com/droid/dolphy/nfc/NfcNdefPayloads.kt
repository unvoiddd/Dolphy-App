package com.droid.dolphy.nfc

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.charset.Charset
import java.util.Locale

object NfcNdefPayloads {

    fun telephone(phoneDigits: String): NdefMessage {
        val cleaned = phoneDigits.filter { it.isDigit() || it == '+' }
        val uri = Uri.parse("tel:${Uri.encode(cleaned)}")
        return NdefMessage(NdefRecord.createUri(uri))
    }

    fun vCard(displayName: String, phone: String): NdefMessage {
        val name = displayName.ifBlank { phone }
        val escName = name.replace("\n", " ").trim()
        val escPhone = phone.replace("\n", " ").trim()
        val vcard = buildString {
            append("BEGIN:VCARD\r\n")
            append("VERSION:3.0\r\n")
            append("FN:").append(escName).append("\r\n")
            append("TEL:").append(escPhone).append("\r\n")
            append("END:VCARD\r\n")
        }
        val bytes = vcard.toByteArray(Charset.forName("UTF-8"))
        return NdefMessage(
            NdefRecord.createMime("text/vcard", bytes),
        )
    }

    fun wifiCredential(ssid: String, password: String, security: String = "WPA"): NdefMessage {
        val s = ssid.trim()
        val p = password.trim()
        val sec = security.uppercase(Locale.US)

        val payload = "WIFI:T:$sec;S:${escapeWifiField(s)};P:${escapeWifiField(p)};;"
        return NdefMessage(NdefRecord.createTextRecord("en", payload))
    }

    private fun escapeWifiField(value: String): String =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")

    fun url(raw: String): NdefMessage {
        var u = raw.trim()
        if (u.isEmpty()) throw IllegalArgumentException("empty url")
        if (!u.lowercase(Locale.US).startsWith("http://") && !u.lowercase(Locale.US).startsWith("https://")) {
            u = "https://$u"
        }
        return NdefMessage(NdefRecord.createUri(Uri.parse(u)))
    }

    fun email(address: String, subject: String = "", body: String = ""): NdefMessage {
        val a = address.trim()
        if (a.isEmpty()) throw IllegalArgumentException("empty email")
        val sub = Uri.encode(subject.trim())
        val b = Uri.encode(body.trim())
        val uri = Uri.parse("mailto:$a?subject=$sub&body=$b")
        return NdefMessage(NdefRecord.createUri(uri))
    }

    fun plainText(text: String): NdefMessage {
        val t = text.trim()
        if (t.isEmpty()) throw IllegalArgumentException("empty text")
        return NdefMessage(NdefRecord.createTextRecord("en", t))
    }
}

