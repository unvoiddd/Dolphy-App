package com.droid.dolphy.nfc

import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.util.Log
import com.droid.dolphy.RootUtils


object RootNfcHelper {
    private const val TAG = "RootNfc"

    fun hasRoot(): Boolean = try {
        RootUtils.isRooted()
    } catch (_: Throwable) {
        false
    }

    
    fun prepareForRead(context: Context): String {
        if (!hasRoot()) return "standard"
        val results = mutableListOf<String>()
        results += runRoot("svc nfc enable")
        results += runRoot("cmd nfc enable")
        results += runRoot("settings put global nfc_on 1")
        results += runRoot("settings put secure nfc_payment_default_component null")
        Log.i(TAG, "prepareForRead: ${results.joinToString()}")
        runCatching {
            val adapter = NfcAdapter.getDefaultAdapter(context)
            if (adapter != null && !adapter.isEnabled) {
            }
        }
        return "root"
    }

    
    fun prepareForEmulation(context: Context): String {
        if (!hasRoot()) return "standard"
        val component = ComponentName(context, NfcType4HostApduService::class.java)
        val flat = "${component.packageName}/${component.className}"
        val results = mutableListOf<String>()
        results += runRoot("svc nfc enable")
        results += runRoot("cmd nfc enable")
        results += runRoot("settings put global nfc_on 1")
        results += runRoot("settings put secure nfc_payment_default_component $flat")
        results += runRoot("settings put secure nfc_payment_foreground 1")
        results += runRoot("settings put secure nfc_payment_component $flat")
        results += runRoot("cmd nfc set-controller-always-on enabled")
        Log.i(TAG, "prepareForEmulation component=$flat → ${results.joinToString()}")
        runCatching {
            val adapter = NfcAdapter.getDefaultAdapter(context) ?: return@runCatching
            val ce = CardEmulation.getInstance(adapter)
            ce.setPreferredService(null, component)
        }
        return "root"
    }

    
    fun isoDepTimeoutMs(): Int = if (hasRoot()) 5000 else 2500

    fun isNfcEnabled(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        if (adapter.isEnabled) return true
        if (!hasRoot()) return false
        prepareForRead(context)
        return adapter.isEnabled
    }

    private fun runRoot(cmd: String): String {
        return try {
            val (code, out) = RootUtils.executeRootCommand(cmd)
            "{$cmd → $code${if (out.isNotBlank()) " ${out.take(40)}" else ""}}"
        } catch (e: Exception) {
            "{$cmd → err:${e.message}}"
        }
    }
}

