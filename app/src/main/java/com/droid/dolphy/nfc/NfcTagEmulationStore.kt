package com.droid.dolphy.nfc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class EmulatedNfcTag(
    val id: Long,
    val name: String,
    val url: String,
)

object NfcTagEmulationStore {
    private const val PREFS = "nfc_emulation_prefs"
    private const val KEY_TAGS = "tags"
    private const val KEY_ACTIVE = "active_tag"

    fun loadTags(context: Context): List<EmulatedNfcTag> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAGS, null)
            ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    val id = item.optLong("id", 0L)
                    val name = item.optString("name")
                    val url = item.optString("url")
                    if (id > 0L && name.isNotBlank() && url.isNotBlank()) {
                        add(EmulatedNfcTag(id = id, name = name, url = url))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveTags(context: Context, tags: List<EmulatedNfcTag>) {
        val json = JSONArray()
        tags.forEach { tag ->
            json.put(
                JSONObject().apply {
                    put("id", tag.id)
                    put("name", tag.name)
                    put("url", tag.url)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAGS, json.toString())
            .apply()
    }

    fun setActiveTag(context: Context, tag: EmulatedNfcTag?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (tag == null) {
            prefs.edit().remove(KEY_ACTIVE).apply()
            return
        }
        val json = JSONObject().apply {
            put("id", tag.id)
            put("name", tag.name)
            put("url", tag.url)
        }
        prefs.edit().putString(KEY_ACTIVE, json.toString()).apply()
    }

    fun getActiveTag(context: Context): EmulatedNfcTag? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE, null)
            ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val id = json.optLong("id", 0L)
            val name = json.optString("name")
            val url = json.optString("url")
            if (id > 0L && name.isNotBlank() && url.isNotBlank()) {
                EmulatedNfcTag(id = id, name = name, url = url)
            } else {
                null
            }
        }.getOrNull()
    }
}

