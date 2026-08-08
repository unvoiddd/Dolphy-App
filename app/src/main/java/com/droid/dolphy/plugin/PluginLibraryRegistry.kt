package com.droid.dolphy.plugin

import android.util.Log
import org.mozilla.javascript.Scriptable
import java.util.concurrent.ConcurrentHashMap


object PluginLibraryRegistry {
    private const val TAG = "PluginLibRegistry"

    enum class Kind {
        API,
        DESIGN,
    }

    data class Export(
        val pluginId: String,
        val name: String,
        val kind: Kind,
        val value: Scriptable,
        
        val androidName: String? = null,
        
        val dolphyName: String? = null,
    )

    private val byName = ConcurrentHashMap<String, Export>()
    private val byAndroid = ConcurrentHashMap<String, String>()
    private val byDolphy = ConcurrentHashMap<String, String>()

    fun export(
        pluginId: String,
        apiName: String,
        value: Scriptable,
        kind: Kind = Kind.API,
        androidName: String? = null,
        dolphyName: String? = null,
    ) {
        val key = apiName.trim()
        if (key.isEmpty()) return

        clearAliasesFor(key)

        val exp = Export(
            pluginId = pluginId,
            name = key,
            kind = kind,
            value = value,
            androidName = androidName?.trim()?.takeIf { it.isNotEmpty() },
            dolphyName = dolphyName?.trim()?.takeIf { it.isNotEmpty() },
        )
        val prev = byName.put(key, exp)
        if (prev != null && prev.pluginId != pluginId) {
            Log.w(TAG, "API '$key' re-exported by $pluginId (was ${prev.pluginId})")
        }

        exp.androidName?.let { alias ->
            byAndroid[alias] = key
            if (!byName.containsKey(alias) || byName[alias]?.name == key) {
                byName.putIfAbsent(alias, exp)
            }
        }
        exp.dolphyName?.let { alias ->
            byDolphy[alias] = key
            if (!byName.containsKey(alias) || byName[alias]?.name == key) {
                byName.putIfAbsent(alias, exp)
            }
        }

        Log.i(
            TAG,
            "export '$key' kind=$kind from $pluginId" +
                (exp.androidName?.let { " android=$it" } ?: "") +
                (exp.dolphyName?.let { " dolphy=$it" } ?: ""),
        )
    }

    fun import(apiName: String, kind: Kind? = null): Scriptable? {
        val key = apiName.trim()
        if (key.isEmpty()) return null
        val exp = resolve(key) ?: return null
        if (kind != null && exp.kind != kind) return null
        return exp.value
    }

    fun importExport(apiName: String, kind: Kind? = null): Export? {
        val key = apiName.trim()
        if (key.isEmpty()) return null
        val exp = resolve(key) ?: return null
        if (kind != null && exp.kind != kind) return null
        return exp
    }

    fun list(kind: Kind? = null): List<String> {
        return byName.values
            .asSequence()
            .filter { kind == null || it.kind == kind }
            .map { it.name }
            .distinct()
            .sorted()
            .toList()
    }

    fun listExports(kind: Kind? = null): List<Export> {
        return byName.values
            .asSequence()
            .filter { kind == null || it.kind == kind }
            .distinctBy { it.name to it.pluginId }
            .sortedBy { it.name }
            .toList()
    }

    fun clearPlugin(pluginId: String) {
        val toRemove = byName.filterValues { it.pluginId == pluginId }.keys
        toRemove.forEach { name ->
            byName.remove(name)
            clearAliasesFor(name)
        }
        byAndroid.entries.removeIf { (_, canonical) ->
            byName[canonical]?.pluginId == pluginId || !byName.containsKey(canonical)
        }
        byDolphy.entries.removeIf { (_, canonical) ->
            byName[canonical]?.pluginId == pluginId || !byName.containsKey(canonical)
        }
        if (toRemove.isNotEmpty()) {
            Log.i(TAG, "cleared ${toRemove.size} export(s) from $pluginId")
        }
    }

    fun clearAll() {
        byName.clear()
        byAndroid.clear()
        byDolphy.clear()
    }

    private fun resolve(key: String): Export? {
        byName[key]?.let { return it }
        byAndroid[key]?.let { can -> byName[can]?.let { return it } }
        byDolphy[key]?.let { can -> byName[can]?.let { return it } }
        return null
    }

    private fun clearAliasesFor(canonical: String) {
        byAndroid.entries.removeIf { it.value == canonical }
        byDolphy.entries.removeIf { it.value == canonical }
        val shadows = byName.filter { (k, v) -> k != canonical && v.name == canonical }.keys
        shadows.forEach { byName.remove(it) }
    }
}

