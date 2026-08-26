package com.droid.dolphy.plugin

import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import org.json.JSONArray
import org.json.JSONObject

data class PluginBluetoothAdvertiseDecision(
    val skipNative: Boolean,
    val advertiseData: AdvertiseData,
    val scanResponse: AdvertiseData?,
)

object PluginBluetoothHooks {
    fun action(action: String, payload: JSONObject = JSONObject()): PluginActionDecision {
        return PluginManager.invokeActionHooks(action, payload.toString())
    }

    fun interceptAdvertising(
        advertiseData: AdvertiseData,
        scanResponse: AdvertiseData?,
        metadata: JSONObject = JSONObject(),
    ): PluginBluetoothAdvertiseDecision {
        val payload = JSONObject()
            .put("metadata", metadata)
            .put("data", encodeAdvertiseData(advertiseData))
            .put("scanResponse", scanResponse?.let(::encodeAdvertiseData))
        val decision = action("bluetooth.advertising.transmit", payload)
        if (decision.cancelled || decision.handled) {
            return PluginBluetoothAdvertiseDecision(true, advertiseData, scanResponse)
        }
        val changed = runCatching { JSONObject(decision.payloadJson) }.getOrNull()
            ?: return PluginBluetoothAdvertiseDecision(false, advertiseData, scanResponse)
        return PluginBluetoothAdvertiseDecision(
            skipNative = false,
            advertiseData = changed.optJSONObject("data")?.let(::decodeAdvertiseData) ?: advertiseData,
            scanResponse = when {
                !changed.has("scanResponse") -> scanResponse
                changed.isNull("scanResponse") -> null
                else -> changed.optJSONObject("scanResponse")?.let(::decodeAdvertiseData) ?: scanResponse
            },
        )
    }

    private fun encodeAdvertiseData(data: AdvertiseData): JSONObject {
        val manufacturers = JSONArray()
        val manufacturerData = data.manufacturerSpecificData
        for (index in 0 until manufacturerData.size()) {
            manufacturers.put(
                JSONObject()
                    .put("id", manufacturerData.keyAt(index))
                    .put("data", manufacturerData.valueAt(index).toHex()),
            )
        }
        val services = JSONArray()
        data.serviceData.forEach { (uuid, bytes) ->
            services.put(JSONObject().put("uuid", uuid.toString()).put("data", bytes.toHex()))
        }
        return JSONObject()
            .put("includeDeviceName", data.includeDeviceName)
            .put("includeTxPowerLevel", data.includeTxPowerLevel)
            .put("manufacturerData", manufacturers)
            .put("serviceUuids", JSONArray(data.serviceUuids?.map { it.toString() }.orEmpty()))
            .put("serviceData", services)
    }

    private fun decodeAdvertiseData(value: JSONObject): AdvertiseData {
        val builder = AdvertiseData.Builder()
            .setIncludeDeviceName(value.optBoolean("includeDeviceName", false))
            .setIncludeTxPowerLevel(value.optBoolean("includeTxPowerLevel", false))
        val manufacturers = value.optJSONArray("manufacturerData") ?: JSONArray()
        for (index in 0 until manufacturers.length()) {
            val item = manufacturers.optJSONObject(index) ?: continue
            builder.addManufacturerData(item.optInt("id"), item.optString("data").hexToBytes())
        }
        val serviceUuids = value.optJSONArray("serviceUuids") ?: JSONArray()
        for (index in 0 until serviceUuids.length()) {
            runCatching { ParcelUuid.fromString(serviceUuids.optString(index)) }
                .getOrNull()
                ?.let(builder::addServiceUuid)
        }
        val serviceData = value.optJSONArray("serviceData") ?: JSONArray()
        for (index in 0 until serviceData.length()) {
            val item = serviceData.optJSONObject(index) ?: continue
            val uuid = runCatching { ParcelUuid.fromString(item.optString("uuid")) }.getOrNull() ?: continue
            builder.addServiceData(uuid, item.optString("data").hexToBytes())
        }
        return builder.build()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    private fun String.hexToBytes(): ByteArray {
        val clean = filterNot(Char::isWhitespace).removePrefix("0x").removePrefix("0X")
        if (clean.isBlank()) return byteArrayOf()
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { index -> clean.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }
}
