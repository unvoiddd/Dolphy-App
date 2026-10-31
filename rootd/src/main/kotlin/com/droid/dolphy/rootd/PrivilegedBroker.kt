package com.droid.dolphy.rootd

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * P1 privileged broker.
 *
 * This is intentionally *not* a general-purpose shell. It is a single choke
 * point that (1) accepts typed operations, (2) validates arguments, (3) runs
 * one fixed command via `su`, (4) enforces a timeout and (5) writes an audit
 * record. The P2 daemon can replace the process launcher while keeping this
 * API and its tests unchanged.
 */
class PrivilegedBroker(
    context: Context,
    private val auditLog: RootAuditLog = RootAuditLog(context),
    private val caller: String = "app",
) {
    private val executable: String = "su"

    var grantedCapabilities: Set<RootCapability> = RootCapability.entries.toSet()

    suspend fun execute(
        operation: RootOperation,
        args: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = 10,
    ): RootdResult = withContext(Dispatchers.IO) {
        if (operation.requiredCapability !in grantedCapabilities) {
            throw RootPermissionDeniedException(operation)
        }

        val command = try {
            RootdProtocol.commandFor(operation, args)
        } catch (error: IllegalArgumentException) {
            return@withContext RootdResult(
                ok = false,
                exitCode = -2,
                output = "",
                error = error.message,
            )
        }

        val started = System.currentTimeMillis()
        val result = try {
            runCommand(command, timeoutSeconds)
        } catch (error: Throwable) {
            RootdResult(
                ok = false,
                exitCode = -1,
                output = "",
                error = error.message ?: error.javaClass.simpleName,
                durationMs = System.currentTimeMillis() - started,
            )
        }
        auditLog.record(operation, args, result, caller)
        result
    }

    suspend fun isAvailable(): Boolean {
        val result = runCatching { execute(RootOperation.ID, timeoutSeconds = 5) }.getOrNull()
        return result?.ok == true && result.output.contains("uid=0")
    }

    private fun runCommand(command: String, timeoutSeconds: Long): RootdResult {
        val started = System.currentTimeMillis()
        val process = ProcessBuilder(executable, "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        try {
            process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(4096)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    if (output.length < MAX_OUTPUT_CHARS) {
                        output.append(buffer, 0, minOf(read, MAX_OUTPUT_CHARS - output.length))
                    }
                    if (output.length >= MAX_OUTPUT_CHARS) break
                }
            }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return RootdResult(
                    ok = false,
                    exitCode = -3,
                    output = output.toString().take(MAX_OUTPUT_CHARS),
                    error = "timeout",
                    durationMs = System.currentTimeMillis() - started,
                )
            }
            val code = process.exitValue()
            return RootdResult(
                ok = code == 0,
                exitCode = code,
                output = output.toString().take(MAX_OUTPUT_CHARS),
                durationMs = System.currentTimeMillis() - started,
            )
        } finally {
            runCatching { process.destroy() }
        }
    }

    companion object {
        private const val MAX_OUTPUT_CHARS = 256 * 1024

        fun isRootBinaryPresent(): Boolean {
            val paths = listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/su/bin/su",
                "/data/adb/ksu/bin/su",
                "/data/adb/magisk/su",
            )
            if (paths.any { File(it).exists() }) return true
            return try {
                ProcessBuilder("which", "su").start().waitFor(2, TimeUnit.SECONDS)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
