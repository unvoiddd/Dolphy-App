package com.droid.dolphy.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import com.droid.dolphy.nfc.db.NfcScanEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object NfcTagAnalyzer {
    suspend fun analyzeIntent(intent: Intent): NfcScanEntity? = withContext(Dispatchers.IO) {
        val action = intent.action ?: return@withContext null
        if (
            action != NfcAdapter.ACTION_TAG_DISCOVERED &&
                action != NfcAdapter.ACTION_TECH_DISCOVERED &&
                action != NfcAdapter.ACTION_NDEF_DISCOVERED
        ) {
            return@withContext null
        }

        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return@withContext null
        val uidHex = tag.id?.toHexString(sep = " ")?.uppercase(Locale.US).orEmpty()
        val techSimple = tag.techList.orEmpty().map { it.substringAfterLast('.') }
        val techListCsv = techSimple.joinToString(", ")

        var writable: Boolean? = null
        var maxSizeBytes: Int? = null
        var ndefRecordType: String? = null
        var ndefContent: String? = null
        var ndefHex: String? = null

        val ndef = Ndef.get(tag)
        if(ndef != null) {
            try {
                ndef.connect()
                writable = runCatching { ndef.isWritable }.getOrNull()
                maxSizeBytes = runCatching { ndef.maxSize }.getOrNull()
                val msg = runCatching { ndef.ndefMessage ?: ndef.cachedNdefMessage }.getOrNull()
                if(msg != null) {
                    val summary = summarizeNdef(msg)
                    ndefRecordType = summary.recordType
                    ndefContent = summary.content
                    ndefHex = summary.rawHex
                }
            } catch (_: Exception) {

            } finally {
                runCatching { ndef.close() }
            }
        }

        val ntag = detectNtag21x(tag)
        val isReadOnly = writable?.not()
        val emv = detectEmv(tag)

        val (type, emvDetected, emvInfo) = if(emv != null) {
            Triple("EMV Contactless", true, emv)
        } else if(ndefRecordType != null) {
            Triple("NDEF", false, null)
        } else if(techSimple.contains("IsoDep")) {
            Triple("NFC Type 4 Tag", false, null)
        } else {
            Triple("Метка", false, null)
        }

        val (passwordSupported, passwordEnabled) = if(ntag != null) {
            val config = readNtagConfig(tag, ntag.configStartPage, ntag.lastUserPage)
            true to config?.passwordProtectionEnabled
        } else {
            null to null
        }

        val verdict = buildString {
            if(ntag != null) {
                append("NTAG: ${ntag.name}")
            } else {
                append("Тип метки: неизвестно")
            }

            if(isReadOnly == true) append(" • Read-only")
            if(passwordSupported == true) {
                when(passwordEnabled) {
                    true -> append(" • Password: включено")
                    false -> append(" • Password: выключено")
                    null -> append(" • Password: неизвестно")
                }
            }
            if(emvDetected) append(" • EMV")
        }

        NfcScanEntity(
            scannedAtMillis = System.currentTimeMillis(),
            uidHex = uidHex.ifBlank { "—" },
            type = type,
            techListCsv = techListCsv.ifBlank { "—" },
            writable = writable,
            maxSizeBytes = maxSizeBytes,
            ndefRecordType = ndefRecordType,
            ndefContent = ndefContent,
            ndefHex = ndefHex,
            ntagType = ntag?.name,
            isReadOnly = isReadOnly,
            passwordProtectionSupported = passwordSupported,
            passwordProtectionEnabled = passwordEnabled,
            analysisVerdict = verdict,
            emvDetected = emvDetected,
            emvInfo = emvInfo,
        )
    }

    private data class NtagInfo(
        val name: String,
        val configStartPage: Int,
        val lastUserPage: Int,
    )

    private data class NtagConfig(
        val passwordProtectionEnabled: Boolean?,
    )

    private fun detectNtag21x(tag: Tag): NtagInfo? {
        val nfcA = NfcA.get(tag) ?: return null
        return try {
            nfcA.connect()
            val resp = nfcA.transceive(byteArrayOf(0x60.toByte()))
            if(resp.size < 7) return null
            val storage = resp[6].toInt() and 0xFF
            when(storage) {
                0x0F -> NtagInfo(name = "NTAG213", configStartPage = 0x29, lastUserPage = 0x27)
                0x11 -> NtagInfo(name = "NTAG215", configStartPage = 0x83, lastUserPage = 0x81)
                0x13 -> NtagInfo(name = "NTAG216", configStartPage = 0xE3, lastUserPage = 0xE1)
                else -> null
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { nfcA.close() }
        }
    }

    private fun readNtagConfig(tag: Tag, configStartPage: Int, lastUserPage: Int): NtagConfig? {
        val nfcA = NfcA.get(tag) ?: return null
        return try {
            nfcA.connect()
            val resp = nfcA.transceive(byteArrayOf(0x30.toByte(), configStartPage.toByte()))
            if(resp.size < 4) return null
            val auth0 = resp[3].toInt() and 0xFF
            val enabled = when {
                auth0 == 0xFF -> false
                auth0 <= lastUserPage -> true
                else -> false
            }
            NtagConfig(passwordProtectionEnabled = enabled)
        } catch (_: Exception) {
            NtagConfig(passwordProtectionEnabled = null)
        } finally {
            runCatching { nfcA.close() }
        }
    }

    private fun detectEmv(tag: Tag): String? {
        val isoDep = IsoDep.get(tag) ?: return null
        return try {
            isoDep.connect()
            isoDep.timeout = if (RootNfcHelper.hasRoot()) RootNfcHelper.isoDepTimeoutMs() else 1200
            val ppse = byteArrayOf(
                0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
                0x0E.toByte(),
                0x32.toByte(), 0x50.toByte(), 0x41.toByte(), 0x59.toByte(), 0x2E.toByte(), 0x53.toByte(), 0x59.toByte(),
                0x53.toByte(), 0x2E.toByte(), 0x44.toByte(), 0x44.toByte(), 0x46.toByte(), 0x30.toByte(), 0x31.toByte(),
                0x00.toByte(),
            )
            val resp = isoDep.transceive(ppse)
            if(resp.size < 2) return null
            val sw1 = resp[resp.size - 2].toInt() and 0xFF
            val sw2 = resp[resp.size - 1].toInt() and 0xFF
            if(sw1 == 0x90 && sw2 == 0x00) {
                val body = resp.copyOf(resp.size - 2)
                val aids = extractTlvValues(body, 0x4F).map { it.toHexString(sep = "").uppercase(Locale.US) }
                val appLabels = extractTlvValues(body, 0x50)
                    .mapNotNull { decodePrintable(it) }
                    .distinct()
                val paymentSystem = detectPaymentSystem(aids, appLabels)

                return buildString {
                    appendLine("Тип: EMV Contactless")
                    appendLine("Стандарт: ISO 14443-4")
                    appendLine("Поддержка APDU: Да")
                    appendLine("Платежная система: ${paymentSystem ?: "Не определена"}")
                    appendLine("Приложений на карте: ${aids.size}")
                    appendLine("AID: ${if(aids.isNotEmpty()) aids.joinToString(", ") else "—"}")
                    append("PPSE: 2PAY.SYS.DDF01")
                    if(appLabels.isNotEmpty()) {
                        appendLine()
                        append("Метка приложения: ${appLabels.joinToString(", ")}")
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { isoDep.close() }
        }
    }

    private fun detectPaymentSystem(aids: List<String>, appLabels: List<String>): String? {
        val aidUpper = aids.map { it.uppercase(Locale.US) }
        if(aidUpper.any { it.startsWith("A000000003") }) return "Visa"
        if(aidUpper.any { it.startsWith("A000000004") }) return "Mastercard"
        if(aidUpper.any { it.startsWith("A000000025") }) return "American Express"
        if(aidUpper.any { it.startsWith("A000000065") }) return "JCB"
        if(aidUpper.any { it.startsWith("A000000333") }) return "UnionPay"
        if(aidUpper.any { it.startsWith("A000000658") }) return "Мир"
        if(aidUpper.any { it.startsWith("A000000524") }) return "RuPay"
        if(aidUpper.any { it.startsWith("A000000152") }) return "Discover"

        val labelText = appLabels.joinToString(" ").uppercase(Locale.US)
        return when {
            "VISA" in labelText -> "Visa"
            "MASTERCARD" in labelText || "MAESTRO" in labelText -> "Mastercard"
            "AMEX" in labelText || "AMERICAN EXPRESS" in labelText -> "American Express"
            "JCB" in labelText -> "JCB"
            "UNIONPAY" in labelText -> "UnionPay"
            "MIR" in labelText || "МИР" in labelText -> "Мир"
            "RUPAY" in labelText -> "RuPay"
            "DISCOVER" in labelText -> "Discover"
            else -> null
        }
    }

    private fun extractTlvValues(data: ByteArray, targetTag: Int): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        collectTlvValues(data, targetTag, out)
        return out
    }

    private fun collectTlvValues(data: ByteArray, targetTag: Int, out: MutableList<ByteArray>) {
        var i = 0
        while(i < data.size) {
            val tagStart = i
            if(i >= data.size) break
            var first = data[i].toInt() and 0xFF
            i++

            if((first and 0x1F) == 0x1F) {
                while(i < data.size) {
                    val next = data[i].toInt() and 0xFF
                    i++
                    if((next and 0x80) == 0) break
                }
            }
            val tagEnd = i
            val tagBytes = data.copyOfRange(tagStart, tagEnd)
            val tagInt = tagBytes.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
            val isConstructed = (first and 0x20) != 0

            if(i >= data.size) break
            val lenFirst = data[i].toInt() and 0xFF
            i++

            val length = when {
                lenFirst < 0x80 -> lenFirst
                lenFirst == 0x80 -> break
                else -> {
                    val count = lenFirst and 0x7F
                    if(count == 0 || i + count > data.size) break
                    var l = 0
                    repeat(count) {
                        l = (l shl 8) or (data[i].toInt() and 0xFF)
                        i++
                    }
                    l
                }
            }
            if(length < 0 || i + length > data.size) break
            val value = data.copyOfRange(i, i + length)
            i += length

            if(tagInt == targetTag) out += value
            if(isConstructed) collectTlvValues(value, targetTag, out)
        }
    }

    private fun decodePrintable(bytes: ByteArray): String? {
        if(bytes.isEmpty()) return null
        val raw = bytes.toString(Charsets.UTF_8).trim()
        if(raw.isBlank()) return null
        if(raw.any { it.code < 0x20 && it != '\n' && it != '\r' && it != '\t' }) return null
        return raw
    }
}

