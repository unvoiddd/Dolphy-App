package com.droid.dolphy.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException

object NfcNdefWriter {

    private fun emptyNdefMessage(): NdefMessage {
        val empty = NdefRecord(
            NdefRecord.TNF_EMPTY,
            byteArrayOf(),
            byteArrayOf(),
            byteArrayOf(),
        )
        return NdefMessage(arrayOf(empty))
    }

    fun eraseToEmpty(tag: Tag): Result<Unit> =
        writeNdefMessage(tag, emptyNdefMessage())

    fun writeNdefMessage(tag: Tag, message: NdefMessage): Result<Unit> = runCatching {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                if (!ndef.isWritable) throw IOException("Tag is not writable")
                val max = ndef.maxSize
                if (message.byteArrayLength > max) {
                    throw IOException("NDEF too large for tag (max $max bytes)")
                }
                ndef.writeNdefMessage(message)
            } finally {
                try {
                    ndef.close()
                } catch (_: IOException) {
                }
            }
            return@runCatching
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            formatable.connect()
            try {
                formatable.format(message)
            } finally {
                try {
                    formatable.close()
                } catch (_: IOException) {
                }
            }
            return@runCatching
        }
        throw IOException("Tag does not support NDEF")
    }
}

