package com.droid.dolphy.plugin

import android.content.Context
import android.net.Uri
import android.util.Log
import com.droid.dolphy.plugin.js.JsPluginSession
import com.droid.dolphy.plugin.model.LoadedJsPlugin
import com.droid.dolphy.plugin.model.PluginManifest
import com.droid.dolphy.plugin.model.UiNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream







object PluginManager {
    private const val TAG = "PluginManager"
    const val SHIZUKU_REQUEST_CODE = 0xD01F

    private lateinit var appContext: Context
    private val pluginsDir by lazy { appContext.getDir("dolphy_plugins", Context.MODE_PRIVATE) }
    private val sourcesDir by lazy { File(pluginsDir, "sources").apply { mkdirs() } }
    private val metaDir by lazy { File(pluginsDir, "meta").apply { mkdirs() } }
    private val deletedFile by lazy { File(pluginsDir, "deleted_ids.json") }

    private val sessions = linkedMapOf<String, JsPluginSession>()
    private val loaded = linkedMapOf<String, LoadedJsPlugin>()
    private val permanentlyDeleted = linkedSetOf<String>()

    private val _plugins = MutableStateFlow<List<LoadedJsPlugin>>(emptyList())
    val plugins: StateFlow<List<LoadedJsPlugin>> = _plugins.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile private var initialized = false

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true

        try {
            loadDeletedIds()
            purgeAnyLegacyBundledHints()
            loadAllFromDisk()
        } catch (t: Throwable) {
            Log.e(TAG, "initialize failed — app continues without plugins", t)
            try {
                sessions.clear()
                loaded.clear()
                publish()
            } catch (_: Throwable) {
            }
        }
    }

    fun getSession(pluginId: String): JsPluginSession? = sessions[pluginId]

    fun getManifest(pluginId: String): PluginManifest? = loaded[pluginId]?.manifest

    
    fun dispatchNfcTag(tag: android.nfc.Tag) {
        sessions.values.forEach { session ->
            try {
                session.onNfcTag(tag)
            } catch (t: Throwable) {
                Log.w(TAG, "dispatchNfcTag ${session.manifest.id}", t)
            }
        }
    }

    fun renderScreen(pluginId: String, screenId: String): UiNode {
        return sessions[pluginId]?.renderScreen(screenId)
            ?: UiNode.Column(
                listOf(
                    UiNode.Text("Плагин не установлен", "headlineSmall"),
                    UiNode.Spacer(8f),
                    UiNode.Text("id=$pluginId — импортируйте .js через Управление плагинами", "bodyMedium"),
                ),
                padding = 16f,
                spacing = 8f,
                fillMaxSize = true,
            )
    }

    fun installFromUri(uri: Uri): Result<PluginManifest> {
        return try {
            val name = queryDisplayName(uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "plugin_${System.currentTimeMillis()}.js"
            val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val tmp = File(pluginsDir, "import_$safeName")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            } ?: return Result.failure(IllegalStateException("Не удалось прочитать файл"))
            val result = installFromFile(tmp)
            tmp.delete()
            result
        } catch (t: Throwable) {
            Log.e(TAG, "installFromUri", t)
            Result.failure(Exception(t.message, t))
        }
    }

    fun installFromFile(sourceFile: File): Result<PluginManifest> {
        if (!sourceFile.exists()) return Result.failure(IllegalArgumentException("Файл не найден"))
        return try {
            val raw = sourceFile.readText(Charsets.UTF_8)
            if (raw.isBlank()) return Result.failure(IllegalArgumentException("Пустой файл"))
            val fallbackId = sourceFile.nameWithoutExtension
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                .lowercase()
                .ifBlank { "plugin_${System.currentTimeMillis() % 100000}" }
            val manifest = JsPluginSession.parseManifest(raw, fallbackId, sourceFile.nameWithoutExtension)
            installSource(manifest, raw)
        } catch (t: Throwable) {
            Log.e(TAG, "installFromFile", t)
            Result.failure(Exception(t.message, t))
        }
    }

    private fun installSource(manifest: PluginManifest, raw: String): Result<PluginManifest> {

        if (permanentlyDeleted.remove(manifest.id)) {
            saveDeletedIds()
        }

        sessions.remove(manifest.id)?.stop()
        PluginRegistry.clearPlugin(manifest.id)
        PluginLibraryRegistry.clearPlugin(manifest.id)

        sourcesDir.mkdirs()
        metaDir.mkdirs()

        val saved = File(sourcesDir, "${manifest.id}.js")
        saved.writeText(raw, Charsets.UTF_8)
        saveMeta(manifest)

        if (!saved.exists() || saved.length() == 0L) {
            return Result.failure(IllegalStateException("Не удалось сохранить плагин на диск"))
        }

        val session = try {
            JsPluginSession(appContext, manifest, raw).also { it.start() }
        } catch (t: Throwable) {
            Log.e(TAG, "Plugin ${manifest.id} fatal on install", t)

            try {
                updateMetaEnabled(manifest.id, false)
            } catch (_: Throwable) {
            }
            loaded[manifest.id] = LoadedJsPlugin(manifest, saved, raw, enabled = false)
            publish()
            return Result.failure(
                IllegalStateException(
                    "Плагин сохранён, но не запустился (защита/Rhino): ${t.javaClass.simpleName}: ${t.message}",
                    t,
                ),
            )
        }
        if (session.lastError != null) {
            Log.w(TAG, "Plugin started with error: ${session.lastError}")
        }
        sessions[manifest.id] = session
        loaded[manifest.id] = LoadedJsPlugin(manifest, saved, raw, true)
        publish()
        Log.i(
            TAG,
            "Installed plugin ${manifest.id} library=${manifest.isLibrary} → ${saved.absolutePath}",
        )
        return Result.success(manifest)
    }


    private fun quarantineBroken(src: File, t: Throwable) {
        try {
            val dest = File(src.parentFile, "${src.name}.broken")
            if (dest.exists()) dest.delete()
            if (!src.renameTo(dest)) {
                src.delete()
            }
            File(metaDir, "${src.nameWithoutExtension}.json").delete()
            Log.e(TAG, "Quarantined ${src.name}: ${t.javaClass.simpleName}: ${t.message}")
        } catch (e: Throwable) {
            Log.e(TAG, "quarantineBroken failed", e)
        }
    }

    fun deletePlugin(pluginId: String) {
        sessions.remove(pluginId)?.stop()
        loaded.remove(pluginId)


        File(sourcesDir, "$pluginId.js").delete()
        File(metaDir, "$pluginId.json").delete()

        try {
            appContext.deleteSharedPreferences("plugin_prefs_$pluginId")
        } catch (_: Exception) {
        }

        permanentlyDeleted.add(pluginId)
        saveDeletedIds()

        PluginRegistry.clearPlugin(pluginId)
        PluginLibraryRegistry.clearPlugin(pluginId)
        publish()
        Log.i(TAG, "Permanently deleted plugin $pluginId")
    }

    fun setEnabled(pluginId: String, enabled: Boolean) {
        val entry = loaded[pluginId] ?: return
        if (enabled) {
            if (!sessions.containsKey(pluginId)) {
                try {
                    val session = JsPluginSession(appContext, entry.manifest, entry.sourceCode)
                    session.start()
                    sessions[pluginId] = session
                } catch (t: Throwable) {
                    Log.e(TAG, "enable failed for $pluginId", t)
                    updateMetaEnabled(pluginId, false)
                    loaded[pluginId] = entry.copy(enabled = false)
                    publish()
                    return
                }
            }
            loaded[pluginId] = entry.copy(enabled = true)
            updateMetaEnabled(pluginId, true)
        } else {
            try {
                sessions.remove(pluginId)?.stop()
            } catch (t: Throwable) {
                Log.w(TAG, "stop on disable", t)
            }
            PluginLibraryRegistry.clearPlugin(pluginId)
            loaded[pluginId] = entry.copy(enabled = false)
            updateMetaEnabled(pluginId, false)
        }
        publish()
    }

    fun reloadAll() {
        sessions.values.toList().forEach { it.stop() }
        sessions.clear()
        loaded.clear()
        PluginRegistry.clearAll()
        PluginLibraryRegistry.clearAll()
        loadAllFromDisk()
    }

    private data class PendingPlugin(
        val src: File,
        val raw: String,
        val manifest: PluginManifest,
        val enabled: Boolean,
    )

    private fun loadAllFromDisk() {
        sourcesDir.mkdirs()
        metaDir.mkdirs()
        PluginLibraryRegistry.clearAll()

        val sourceFiles = sourcesDir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("js", true) || it.extension.equals("plugin", true)) }
            .orEmpty()

        val pending = mutableListOf<PendingPlugin>()

        for (src in sourceFiles) {
            try {
                val raw = src.readText(Charsets.UTF_8)
                if (raw.isBlank()) continue
                val fallbackId = src.nameWithoutExtension
                val fromSource = JsPluginSession.parseManifest(raw, fallbackId, fallbackId)
                val metaFile = File(metaDir, "${fromSource.id}.json")
                var enabled = true
                val manifest = if (metaFile.exists()) {
                    try {
                        val serial = json.decodeFromString(SerializableManifest.serializer(), metaFile.readText())
                        enabled = serial.enabled
                        serial.toManifest().copy(
                            isLibrary = fromSource.isLibrary || serial.isLibrary || fromSource.isDesignLibrary,
                            isDesignLibrary = fromSource.isDesignLibrary || serial.isDesignLibrary,
                        )
                    } catch (_: Exception) {
                        fromSource
                    }
                } else {
                    saveMeta(fromSource, enabled = true)
                    fromSource
                }

                if (manifest.id in permanentlyDeleted) {
                    src.delete()
                    metaFile.delete()
                    continue
                }

                pending += PendingPlugin(src, raw, manifest, enabled)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to parse ${src.name}", t)
                quarantineBroken(src, t)
            }
        }

        val ordered = pending.sortedByDescending {
            it.manifest.isLibrary || it.manifest.isDesignLibrary
        }

        for (item in ordered) {
            try {
                val (src, raw, manifest, enabled) = item
                if (enabled) {
                    val session = try {
                        JsPluginSession(appContext, manifest, raw).also { it.start() }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Plugin ${manifest.id} fatal start error — quarantine", t)
                        quarantineBroken(src, t)
                        continue
                    }
                    if (session.lastError != null) {
                        Log.w(TAG, "Plugin ${manifest.id} started with error: ${session.lastError}")
                    }
                    sessions[manifest.id] = session
                }
                loaded[manifest.id] = LoadedJsPlugin(manifest, src, raw, enabled)
                Log.i(
                    TAG,
                    "Restored plugin ${manifest.id} enabled=$enabled library=${manifest.isLibrary}",
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load ${item.src.name}", t)
                quarantineBroken(item.src, t)
            }
        }

        metaDir.listFiles()?.forEach { f ->
            if (f.extension == "json") {
                try {
                    val m = json.decodeFromString(SerializableManifest.serializer(), f.readText())
                    val src = File(sourcesDir, "${m.id}.js")
                    if (!src.exists() || m.id in permanentlyDeleted) {
                        f.delete()
                    }
                } catch (_: Exception) {
                    f.delete()
                }
            }
        }

        publish()
        Log.i(
            TAG,
            "Loaded ${loaded.size} plugin(s) from disk " +
                "(libraries=${loaded.values.count { it.manifest.isLibrary }})",
        )
    }

    private fun purgeAnyLegacyBundledHints() {


    }

    private fun saveMeta(manifest: PluginManifest, enabled: Boolean = true) {
        val serial = SerializableManifest(
            id = manifest.id,
            name = manifest.name,
            version = manifest.version,
            description = manifest.description,
            author = manifest.author,
            enabled = enabled,
            isLibrary = manifest.isLibrary || manifest.isDesignLibrary,
            isDesignLibrary = manifest.isDesignLibrary,
        )
        metaDir.mkdirs()
        File(metaDir, "${manifest.id}.json").writeText(
            json.encodeToString(SerializableManifest.serializer(), serial),
            Charsets.UTF_8,
        )
    }

    private fun updateMetaEnabled(pluginId: String, enabled: Boolean) {
        val f = File(metaDir, "$pluginId.json")
        if (!f.exists()) return
        try {
            val m = json.decodeFromString(SerializableManifest.serializer(), f.readText())
            f.writeText(
                json.encodeToString(
                    SerializableManifest.serializer(),
                    m.copy(enabled = enabled),
                ),
                Charsets.UTF_8,
            )
        } catch (_: Exception) {
        }
    }

    private fun loadDeletedIds() {
        permanentlyDeleted.clear()
        try {
            if (deletedFile.exists()) {
                val arr = org.json.JSONArray(deletedFile.readText())
                for (i in 0 until arr.length()) {
                    permanentlyDeleted.add(arr.getString(i))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadDeletedIds", e)
        }
    }

    private fun saveDeletedIds() {
        try {
            pluginsDir.mkdirs()
            val arr = org.json.JSONArray()
            permanentlyDeleted.forEach { arr.put(it) }
            deletedFile.writeText(arr.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "saveDeletedIds", e)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun publish() {
        _plugins.value = loaded.values.toList()
    }

    @Serializable
    private data class SerializableManifest(
        val id: String,
        val name: String,
        val version: String = "1.0",
        val description: String = "",
        val author: String = "",
        val enabled: Boolean = true,
        val isLibrary: Boolean = false,
        val isDesignLibrary: Boolean = false,
    ) {
        fun toManifest() = PluginManifest(
            id, name, version, description, author,
            isLibrary = isLibrary || isDesignLibrary,
            isDesignLibrary = isDesignLibrary,
        )
    }
}

