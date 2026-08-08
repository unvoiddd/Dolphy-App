package com.droid.dolphy

import android.util.Log
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object ShizukuHelper {
    private const val TAG = "ShizukuHelper"

    @Volatile
    private var cachedNewProcess: Method? = null

    fun runShellCommand(command: String): Boolean {
        return try {
            JSONObject(runShellCommandWithOutput(command)).optInt("code", -1) == 0
        } catch (_: Exception) {
            false
        }
    }







    fun runShellCommandWithOutput(command: String): String {
        return try {
            if (!Shizuku.pingBinder()) {
                return resultJson(-1, "", "Shizuku not running")
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return resultJson(-1, "", "Shizuku permission not granted")
            }

            val process = createRemoteProcess(command)
                ?: return resultJson(-1, "", "newProcess returned null / method missing (R8?)")


            val pool = Executors.newFixedThreadPool(2)
            try {
                val outFuture = pool.submit(Callable { readFully(process.inputStream) })
                val errFuture = pool.submit(Callable { readFully(process.errorStream) })

                val code = try {
                    process.waitFor()
                } catch (e: Exception) {
                    Log.w(TAG, "waitFor failed", e)
                    try {
                        process.exitValue()
                    } catch (_: Exception) {
                        -1
                    }
                }

                val out = try {
                    outFuture.get(90, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.w(TAG, "stdout read", e)
                    ""
                }
                val err = try {
                    errFuture.get(10, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.w(TAG, "stderr read", e)
                    ""
                }

                resultJson(code, out, err)
            } finally {
                pool.shutdownNow()
                try {
                    process.destroy()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run command via Shizuku", e)

            val msg = buildString {
                append(e.javaClass.simpleName)
                if (!e.message.isNullOrBlank()) append(": ").append(e.message)
                e.cause?.let { c ->
                    append(" | cause=").append(c.javaClass.simpleName)
                    if (!c.message.isNullOrBlank()) append(": ").append(c.message)
                }
            }
            resultJson(-1, "", msg)
        }
    }

    private fun createRemoteProcess(command: String): Process? {
        val method = resolveNewProcessMethod()
            ?: throw NoSuchMethodException(
                "rikka.shizuku.Shizuku.newProcess not found " +
                    "(ProGuard stripped private API — rebuild with rikka.shizuku keep rules). " +
                    "methods=" + Shizuku::class.java.declaredMethods.joinToString { it.name },
            )

        method.isAccessible = true

        val candidates = listOf(
            arrayOf("sh", "-c", command),
            arrayOf("/system/bin/sh", "-c", command),

        )
        var lastError: Exception? = null
        for (cmd in candidates) {
            try {
                val p = method.invoke(null, cmd, null, null)
                when (p) {
                    is Process -> return p
                    null -> lastError = IllegalStateException("newProcess returned null")
                    else -> {

                        Log.w(TAG, "newProcess type=${p.javaClass.name}")
                        if (Process::class.java.isInstance(p)) {
                            return p as Process
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "newProcess invoke: ${e.message}")
            }
        }
        if (lastError != null) throw lastError
        return null
    }





    private fun resolveNewProcessMethod(): Method? {
        cachedNewProcess?.let { return it }

        val stringArray = Array<String>::class.java
        val stringArrayAlt = java.lang.reflect.Array.newInstance(String::class.java, 0).javaClass


        for (types in listOf(
            arrayOf(stringArray, stringArray, String::class.java),
            arrayOf(stringArrayAlt, stringArrayAlt, String::class.java),
            arrayOf(stringArray, stringArrayAlt, String::class.java),
        )) {
            try {
                val m = Shizuku::class.java.getDeclaredMethod("newProcess", *types)
                m.isAccessible = true
                cachedNewProcess = m
                Log.i(TAG, "newProcess resolved exact: ${m.toGenericString()}")
                return m
            } catch (_: NoSuchMethodException) {
            }
        }


        val found = Shizuku::class.java.declaredMethods.firstOrNull { m ->
            if (m.name != "newProcess") return@firstOrNull false
            if (!Modifier.isStatic(m.modifiers)) return@firstOrNull false
            val p = m.parameterTypes
            p.size == 3 &&
                p[0].isArray && p[0].componentType == String::class.java &&
                (p[1].isArray || p[1] == String::class.java) &&
                (p[2] == String::class.java || p[2].name == "java.lang.String")
        }
        if (found != null) {
            found.isAccessible = true
            cachedNewProcess = found
            Log.i(TAG, "newProcess resolved scan: ${found.toGenericString()}")
            return found
        }


        Log.e(
            TAG,
            "newProcess missing. Declared: " +
                Shizuku::class.java.declaredMethods.joinToString { m ->
                    "${m.name}(${m.parameterTypes.joinToString { it.simpleName }})"
                },
        )
        return null
    }

    private fun readFully(stream: InputStream?): String {
        if (stream == null) return ""
        return try {
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "readFully: ${e.message}")
            ""
        }
    }

    private fun resultJson(code: Int, out: String, err: String): String {
        return JSONObject()
            .put("code", code)
            .put("out", out)
            .put("err", err)
            .put("via", "shizuku")
            .toString()
    }
}

