package com.droid.dolphy

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log
import com.droid.dolphy.plugin.PluginManager
import org.json.JSONArray
import org.json.JSONObject

object IrBackend {
    private const val SERVICE_ID = "infrared.transmitter"

    fun isAvailable(context: Context): Boolean {
        val pluginAvailable = PluginManager.invokeServices(SERVICE_ID, "available")
            .any { truthy(it.valueJson) }
        if (pluginAvailable) return true
        val manager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        return manager?.hasIrEmitter() == true
    }

    fun transmit(context: Context, button: IrButton): Boolean {
        if (button.pattern.isEmpty() || button.pattern.any { it <= 0 } || button.frequency <= 0) return false
        var payload = JSONObject()
            .put("name", button.name ?: "")
            .put("frequency", button.frequency)
            .put("pattern", JSONArray(button.pattern.toList()))
            .put("protocol", button.protocol)
            .put("code", button.irCode)
            .put("timings", button.timings)
        val decision = PluginManager.invokeActionHooks("infrared.transmit", payload.toString())
        if (decision.cancelled) return false
        payload = runCatching { JSONObject(decision.payloadJson) }.getOrDefault(payload)
        if (decision.handled) {
            val success = decision.resultJson?.let(::truthy) ?: true
            if (success) trackIrSend(context)
            return success
        }
        PluginManager.invokeServices(SERVICE_ID, "transmit", payload.toString()).forEach { response ->
            val result = parseServiceResult(response.valueJson)
            if (result.first) {
                if (result.second) trackIrSend(context)
                return result.second
            }
        }
        val manager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager ?: return false
        if (!manager.hasIrEmitter()) return false
        return runCatching {
            val frequency = payload.optInt("frequency", button.frequency)
            val array = payload.optJSONArray("pattern")
            val pattern = if (array == null) button.pattern else IntArray(array.length()) { array.optInt(it) }
            if (frequency <= 0 || pattern.isEmpty() || pattern.any { it <= 0 }) return false
            manager.transmit(frequency, pattern)
            trackIrSend(context)
            true
        }.onFailure { Log.e("IrBackend", "IR transmission failed", it) }.getOrDefault(false)
    }

    private fun parseServiceResult(raw: String): Pair<Boolean, Boolean> {
        val objectResult = runCatching { JSONObject(raw) }.getOrNull()
        if (objectResult != null) {
            return objectResult.optBoolean("handled", true) to objectResult.optBoolean("ok", true)
        }
        return if (raw.equals("true", true)) true to true else false to false
    }

    private fun truthy(raw: String): Boolean {
        val objectResult = runCatching { JSONObject(raw) }.getOrNull()
        return objectResult?.optBoolean("available", objectResult.optBoolean("ok", false))
            ?: raw.equals("true", true)
    }
}
