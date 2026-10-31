package com.droid.dolphy.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey

/**
 * SharedPreferences wrapper that encrypts every value with AES-256-GCM.
 * Legacy plaintext values are migrated on first read.
 */
class SecurePreferences(context: Context, name: String) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    private val key: SecretKey by lazy { AndroidKeyStoreKeys.getOrCreate() }

    @Synchronized
    fun getString(keyName: String, defaultValue: String? = null): String? {
        val raw = prefs.getString(keyName, null) ?: return defaultValue
        if (!SecureEnvelope.isEncrypted(raw)) {
            return runCatching {
                putString(keyName, raw)
                raw
            }.getOrElse { error ->
                Log.e(TAG, "Unable to migrate legacy value '$keyName'", error)
                null
            }
        }
        return try {
            String(SecureEnvelope.decrypt(raw, key), StandardCharsets.UTF_8)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to decrypt value '$keyName'", error)
            null
        }
    }

    @Synchronized
    fun putString(keyName: String, value: String) {
        val encrypted = SecureEnvelope.encrypt(
            value.toByteArray(StandardCharsets.UTF_8),
            key,
        )
        check(prefs.edit().putString(keyName, encrypted).commit()) {
            "Unable to persist encrypted preference '$keyName'"
        }
    }

    @Synchronized
    fun contains(keyName: String): Boolean = prefs.contains(keyName)

    @Synchronized
    fun remove(keyName: String) {
        prefs.edit().remove(keyName).apply()
    }

    companion object {
        private const val TAG = "SecurePreferences"
    }
}
