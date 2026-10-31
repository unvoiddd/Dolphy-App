package com.droid.dolphy.rootd

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only JSONL audit trail for privileged operations. Kept local, rotated
 * at 2 MB. Never logs command output (it may contain secrets).
 */
class RootAuditLog(context: Context) {

    private val directory = File(context.filesDir, "audit").apply { mkdirs() }
    private val file = File(directory, "rootd.jsonl")
    private val lock = Any()

    fun record(
        operation: RootOperation,
        args: Map<String, String>,
        result: RootdResult,
        caller: String,
    ) {
        val entry = buildString {
            append('{')
            append("\"ts\":\"").append(timestamp()).append("\",")
            append("\"caller\":\"").append(escape(caller)).append("\",")
            append("\"op\":\"").append(operation.name).append("\",")
            append("\"args\":{")
            args.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(',')
                append('"').append(escape(key)).append("\":\"")
                append(escape(value.take(64))).append("\",")
            }
            if (args.isNotEmpty()) setLength(length - 1)
            append("},")
            append("\"ok\":").append(result.ok).append(',')
            append("\"code\":").append(result.exitCode).append(',')
            append("\"ms\":").append(result.durationMs)
            append('}')
        }
        synchronized(lock) {
            runCatching {
                rotateIfNeeded()
                file.appendText(entry + "\n")
            }.onFailure { Log.w(TAG, "Unable to write audit entry", it) }
        }
    }

    fun readAll(): String = synchronized(lock) {
        if (!file.exists()) "" else file.readText()
    }

    private fun rotateIfNeeded() {
        if (file.exists() && file.length() > MAX_BYTES) {
            val backup = File(directory, "rootd.1.jsonl")
            if (backup.exists()) backup.delete()
            file.renameTo(backup)
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(ch)
            }
        }
    }

    companion object {
        private const val TAG = "RootAuditLog"
        private const val MAX_BYTES = 2L * 1024 * 1024
    }
}
