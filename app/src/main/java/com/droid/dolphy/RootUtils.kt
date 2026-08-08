package com.droid.dolphy

import android.util.Log
import java.io.File

object RootUtils {
    private const val TAG = "RootUtils"

    fun isRooted(): Boolean {

        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )

        for (path in paths) {
            if (File(path).exists()) {
                Log.d(TAG, "Found su at: $path")

                return checkRootAccess()
            }
        }


        Log.d(TAG, "Su files not found, trying direct execution")
        return checkRootAccess()
    }

    private fun checkRootAccess(): Boolean {
        return try {

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            Log.d(TAG, "Root check: exitCode=$exitCode, output=$output")


            exitCode == 0 && output.contains("uid=0")
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            false
        }
    }

    fun executeRootCommand(command: String): Pair<Int, String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            val result = if (error.isNotEmpty()) "$output\n$error" else output
            Pair(exitCode, result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute root command: $command", e)
            Pair(-1, e.message ?: "Unknown error")
        }
    }
}

