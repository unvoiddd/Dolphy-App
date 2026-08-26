package com.droid.dolphy.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.droid.dolphy.plugin.js.JsPluginSession
import com.droid.dolphy.plugin.model.LoadedPlugin
import com.droid.dolphy.plugin.model.PluginManifest
import com.droid.dolphy.plugin.model.PluginPreview
import com.droid.dolphy.plugin.model.UiNode
import com.droid.dolphy.plugin.python.PythonPluginSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import androidx.core.content.FileProvider







object PluginManager {
    private const val TAG = "PluginManager"
    const val SHIZUKU_REQUEST_CODE = 0xD01F

    private lateinit var appContext: Context
    private val pluginsDir by lazy { appContext.getDir("dolphy_plugins", Context.MODE_PRIVATE) }
    private val sourcesDir by lazy { File(pluginsDir, "sources").apply { mkdirs() } }
    private val metaDir by lazy { File(pluginsDir, "meta").apply { mkdirs() } }
    private val deletedFile by lazy { File(pluginsDir, "deleted_ids.json") }

    private val sessions = linkedMapOf<String, PluginSession>()
    private val loaded = linkedMapOf<String, LoadedPlugin>()
    private val permanentlyDeleted = linkedSetOf<String>()

    private val _plugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())
    val plugins: StateFlow<List<LoadedPlugin>> = _plugins.asStateFlow()

    private val _safeMode = MutableStateFlow(false)
    val safeMode: StateFlow<Boolean> = _safeMode.asStateFlow()

    private val _safeModeNotice = MutableStateFlow(false)
    val safeModeNotice: StateFlow<Boolean> = _safeModeNotice.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile private var initialized = false
    @Volatile private var pluginStartupActive = false

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true

        val safety = appContext.getSharedPreferences(SAFE_MODE_PREFS, Context.MODE_PRIVATE)
        _safeMode.value = safety.getBoolean(KEY_SAFE_MODE, false)
        _safeModeNotice.value = safety.getBoolean(KEY_SAFE_MODE_NOTICE, false)

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

    fun getSession(pluginId: String): PluginSession? = sessions[pluginId]

    fun getPluginSetting(pluginId: String, key: String, fallbackJson: String): String {
        return appContext.getSharedPreferences("plugin_prefs_$pluginId", Context.MODE_PRIVATE)
            .getString(key, null) ?: fallbackJson
    }

    fun setPluginSetting(pluginId: String, key: String, valueJson: String) {
        appContext.getSharedPreferences("plugin_prefs_$pluginId", Context.MODE_PRIVATE)
            .edit()
            .putString(key, valueJson)
            .apply()
        sessions[pluginId]?.onEvent(
            "setting_changed",
            org.json.JSONObject().put("key", key).put("value", runCatching { org.json.JSONTokener(valueJson).nextValue() }.getOrDefault(valueJson)),
        )
        PluginRegistry.touch()
    }

    fun getManifest(pluginId: String): PluginManifest? = loaded[pluginId]?.manifest

    fun hasRunningPlugins(): Boolean = initialized && (sessions.isNotEmpty() || pluginStartupActive)

    fun activateSafeModeFromCrash(context: Context, crashLog: String) {
        runCatching {
            context.applicationContext.getSharedPreferences(SAFE_MODE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SAFE_MODE, true)
                .putBoolean(KEY_SAFE_MODE_NOTICE, true)
                .commit()
            File(context.applicationContext.filesDir, SAFE_MODE_LOG_FILE).writeText(crashLog, Charsets.UTF_8)
            if (initialized) {
                _safeMode.value = true
                _safeModeNotice.value = true
            }
        }
    }

    fun safeModeCrashLog(): String {
        return runCatching { File(appContext.filesDir, SAFE_MODE_LOG_FILE).readText(Charsets.UTF_8) }
            .getOrDefault("")
    }

    fun dismissSafeModeNotice() {
        _safeModeNotice.value = false
        appContext.getSharedPreferences(SAFE_MODE_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SAFE_MODE_NOTICE, false).apply()
    }

    fun disableSafeMode() {
        _safeMode.value = false
        _safeModeNotice.value = false
        appContext.getSharedPreferences(SAFE_MODE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SAFE_MODE, false)
            .putBoolean(KEY_SAFE_MODE_NOTICE, false)
            .apply()
    }

    fun togglePinned(pluginId: String) {
        val entry = loaded[pluginId] ?: return
        val pinned = !entry.pinned
        loaded[pluginId] = entry.copy(pinned = pinned)
        updateMetaPinned(pluginId, pinned)
        publish()
    }

    fun sharePlugin(context: Context, pluginId: String): Result<Unit> {
        return runCatching {
            val entry = loaded[pluginId] ?: error("Плагин не найден")
            val shareDir = File(appContext.cacheDir, "shared_plugins").apply { mkdirs() }
            val file = File(shareDir, "${entry.manifest.id}.dolphyplugin")
            file.writeText(entry.sourceCode, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.provider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/x-dolphy-plugin"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, entry.manifest.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, entry.manifest.name))
        }
    }

    fun previewFromUri(uri: Uri): Result<PluginPreview> {
        return try {
            val fileName = queryDisplayName(uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "plugin.dolphyplugin"
            val mime = appContext.contentResolver.getType(uri).orEmpty()
            val accepted = fileName.endsWith(".dolphyplugin", true) ||
                uri.path?.endsWith(".dolphyplugin", true) == true ||
                mime.equals("application/x-dolphy-plugin", true)
            if (!accepted) return Result.failure(IllegalArgumentException("Поддерживаются только файлы .dolphyplugin"))
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_PLUGIN_SOURCE_BYTES) {
                        return Result.failure(IllegalArgumentException("Файл плагина слишком большой"))
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: return Result.failure(IllegalStateException("Не удалось прочитать файл"))
            val raw = bytes.toString(Charsets.UTF_8)
            if (raw.isBlank()) return Result.failure(IllegalArgumentException("Пустой файл"))
            val fallbackId = fileName.substringBeforeLast('.')
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                .lowercase()
                .ifBlank { "plugin" }
            val manifest = parseManifest("python", raw, fallbackId, fileName.substringBeforeLast('.'))
            Result.success(
                PluginPreview(
                    manifest = manifest,
                    fileName = fileName,
                    sizeBytes = bytes.size.toLong(),
                    runtime = "python",
                    installed = loaded.containsKey(manifest.id),
                    capabilities = inferCapabilities(raw),
                ),
            )
        } catch (t: Throwable) {
            Result.failure(Exception(t.message, t))
        }
    }

    private fun inferCapabilities(source: String): List<String> {
        val supported = setOf(
            "dex", "download", "network", "files", "ble_spam", "bluetooth", "wifi",
            "infrared", "usb", "root", "shizuku", "shell", "hooks",
        )
        val declaredBody = Regex("(?ms)^\\s*__permissions__\\s*=\\s*\\[(.*?)]\\s*$")
            .find(source)?.groupValues?.getOrNull(1).orEmpty()
        val declared = Regex("[\"']([^\"']+)[\"']")
            .findAll(declaredBody)
            .map { it.groupValues[1].trim().lowercase().replace('-', '_') }
            .filter { it in supported }
            .toList()
        val checks = linkedMapOf(
            "dex" to listOf("load_dex_", "loadDex", "export_app_class", "find_java_class"),
            "download" to listOf("download_assets", "httpDownload", "http_download"),
            "network" to listOf("httpRequest", "http_request", "tcpReachable", "portScan", "pingHost"),
            "files" to listOf("filesWrite", "filesRead", "asset_path", "apply_asset"),
            "ble_spam" to listOf("register_ble_mode", "registerBleMode", "bleAdvertise", "advertis"),
            "bluetooth" to listOf("bleStartScan", "bleConnect", "bluetooth", "gatt"),
            "wifi" to listOf("wifi", "WifiManager", "networkSuggest"),
            "infrared" to listOf("infrared", "irTransmit", "ir_remote"),
            "usb" to listOf("UsbManager", "usb", "bad_hid", "bad_usb"),
            "root" to listOf("rootExec", "root_exec"),
            "shizuku" to listOf("shizukuExec", "shizuku_exec"),
            "shell" to listOf("shellExec", "shell_exec", "smartExec"),
            "hooks" to listOf("hook_screen", "hook_action", "hook_surface", "registerScreenHook"),
        )
        return (declared + checks.filterValues { tokens -> tokens.any { source.contains(it, ignoreCase = true) } }.keys)
            .distinct()
    }

    
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
                    UiNode.Text("id=$pluginId — импортируйте .dolphyplugin через Управление плагинами", "bodyMedium"),
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
                ?: "plugin_${System.currentTimeMillis()}.dolphyplugin"
            val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val tmp = File(pluginsDir, "import_$safeName")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_PLUGIN_SOURCE_BYTES) throw IllegalArgumentException("Файл плагина слишком большой")
                        output.write(buffer, 0, read)
                    }
                }
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
            val runtime = if (sourceFile.extension.equals("dolphyplugin", true) || looksLikePython(raw)) "python" else "javascript"
            val manifest = parseManifest(runtime, raw, fallbackId, sourceFile.nameWithoutExtension)
            installSource(manifest, raw, runtime)
        } catch (t: Throwable) {
            Log.e(TAG, "installFromFile", t)
            Result.failure(Exception(t.message, t))
        }
    }

    private fun installSource(manifest: PluginManifest, raw: String, runtime: String): Result<PluginManifest> {

        if (permanentlyDeleted.remove(manifest.id)) {
            saveDeletedIds()
        }

        disableDependents(manifest.id)
        sessions.remove(manifest.id)?.stop()
        PluginRegistry.clearPlugin(manifest.id)
        PluginLibraryRegistry.clearPlugin(manifest.id)
        PluginDexRegistry.clearPlugin(manifest.id)

        sourcesDir.mkdirs()
        metaDir.mkdirs()

        val wasPinned = loaded[manifest.id]?.pinned ?: false
        val extension = if (runtime == "python") "dolphyplugin" else "js"
        val saved = File(sourcesDir, "${manifest.id}.$extension")
        saved.writeText(raw, Charsets.UTF_8)
        val shouldEnable = !_safeMode.value
        saveMeta(manifest, enabled = shouldEnable, runtime = runtime, pinned = wasPinned)

        if (!saved.exists() || saved.length() == 0L) {
            return Result.failure(IllegalStateException("Не удалось сохранить плагин на диск"))
        }

        if (!shouldEnable) {
            loaded[manifest.id] = LoadedPlugin(manifest, saved, raw, false, runtime, wasPinned)
            publish()
            return Result.success(manifest)
        }

        val missingDependencies = manifest.dependencies.filterNot { sessions.containsKey(it) }
        if (missingDependencies.isNotEmpty()) {
            updateMetaEnabled(manifest.id, false)
            loaded[manifest.id] = LoadedPlugin(manifest, saved, raw, enabled = false, runtime = runtime, pinned = wasPinned)
            publish()
            return Result.failure(IllegalStateException("Не установлены или отключены библиотеки: ${missingDependencies.joinToString()}"))
        }

        val session = try {
            createStartedSession(runtime, manifest, raw)
        } catch (t: Throwable) {
            Log.e(TAG, "Plugin ${manifest.id} fatal on install", t)

            try {
                updateMetaEnabled(manifest.id, false)
            } catch (_: Throwable) {
            }
            loaded[manifest.id] = LoadedPlugin(manifest, saved, raw, enabled = false, runtime = runtime, pinned = wasPinned)
            publish()
            return Result.failure(
                IllegalStateException(
                    "Плагин сохранён, но не запустился: ${t.javaClass.simpleName}: ${t.message}",
                    t,
                ),
            )
        }
        if (session.lastError != null) {
            Log.w(TAG, "Plugin started with error: ${session.lastError}")
        }
        sessions[manifest.id] = session
        loaded[manifest.id] = LoadedPlugin(manifest, saved, raw, true, runtime, wasPinned)
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
        disableDependents(pluginId)
        sessions.remove(pluginId)?.stop()
        loaded.remove(pluginId)


        File(sourcesDir, "$pluginId.js").delete()
        File(sourcesDir, "$pluginId.dolphyplugin").delete()
        File(sourcesDir, "$pluginId.plugin").delete()
        File(metaDir, "$pluginId.json").delete()

        try {
            appContext.deleteSharedPreferences("plugin_prefs_$pluginId")
        } catch (_: Exception) {
        }

        permanentlyDeleted.add(pluginId)
        saveDeletedIds()

        PluginRegistry.clearPlugin(pluginId)
        PluginLibraryRegistry.clearPlugin(pluginId)
        PluginDexRegistry.clearPlugin(pluginId)
        publish()
        Log.i(TAG, "Permanently deleted plugin $pluginId")
    }

    fun setEnabled(pluginId: String, enabled: Boolean) {
        val entry = loaded[pluginId] ?: return
        if (enabled && _safeMode.value) return
        if (enabled) {
            if (entry.manifest.dependencies.any { !sessions.containsKey(it) }) return
            if (!sessions.containsKey(pluginId)) {
                try {
                    val session = createStartedSession(entry.runtime, entry.manifest, entry.sourceCode)
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
            disableDependents(pluginId)
            try {
                sessions.remove(pluginId)?.stop()
            } catch (t: Throwable) {
                Log.w(TAG, "stop on disable", t)
            }
            PluginLibraryRegistry.clearPlugin(pluginId)
            PluginDexRegistry.clearPlugin(pluginId)
            loaded[pluginId] = entry.copy(enabled = false)
            updateMetaEnabled(pluginId, false)
        }
        publish()
    }

    fun dispatchEventToPlugin(pluginId: String, name: String, payload: Any?) {
        sessions[pluginId]?.onEvent(name, payload)
    }

    private fun disableDependents(pluginId: String) {
        loaded.values
            .filter { it.enabled && pluginId in it.manifest.dependencies }
            .map { it.manifest.id }
            .forEach { setEnabled(it, false) }
    }

    fun reloadAll() {
        sessions.values.toList().forEach { it.stop() }
        sessions.clear()
        loaded.clear()
        PluginRegistry.clearAll()
        PluginLibraryRegistry.clearAll()
        PluginDexRegistry.clearAll()
        loadAllFromDisk()
    }

    private data class PendingPlugin(
        val src: File,
        val raw: String,
        val manifest: PluginManifest,
        val enabled: Boolean,
        val runtime: String,
        val pinned: Boolean,
    )

    private fun loadAllFromDisk() {
        sourcesDir.mkdirs()
        metaDir.mkdirs()
        PluginLibraryRegistry.clearAll()
        PluginDexRegistry.clearAll()

        val sourceFiles = sourcesDir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("js", true) || it.extension.equals("plugin", true) || it.extension.equals("dolphyplugin", true)) }
            .orEmpty()

        val pending = mutableListOf<PendingPlugin>()

        for (src in sourceFiles) {
            try {
                val raw = src.readText(Charsets.UTF_8)
                if (raw.isBlank()) continue
                val fallbackId = src.nameWithoutExtension
                val detectedRuntime = if (src.extension.equals("dolphyplugin", true) || looksLikePython(raw)) "python" else "javascript"
                val fromSource = parseManifest(detectedRuntime, raw, fallbackId, fallbackId)
                val metaFile = File(metaDir, "${fromSource.id}.json")
                var enabled = true
                var pinned = false
                val manifest = if (metaFile.exists()) {
                    try {
                        val serial = json.decodeFromString(SerializableManifest.serializer(), metaFile.readText())
                        enabled = serial.enabled
                        pinned = serial.pinned
                        serial.toManifest().copy(
                            isLibrary = fromSource.isLibrary || serial.isLibrary || fromSource.isDesignLibrary,
                            isDesignLibrary = fromSource.isDesignLibrary || serial.isDesignLibrary,
                            icon = fromSource.icon,
                            dependencies = fromSource.dependencies.ifEmpty { serial.dependencies },
                        )
                    } catch (_: Exception) {
                        fromSource
                    }
                } else {
                    saveMeta(fromSource, enabled = !_safeMode.value, runtime = detectedRuntime)
                    enabled = !_safeMode.value
                    fromSource
                }

                if (_safeMode.value && enabled) {
                    enabled = false
                    updateMetaEnabled(fromSource.id, false)
                }

                if (manifest.id in permanentlyDeleted) {
                    src.delete()
                    metaFile.delete()
                    continue
                }

                pending += PendingPlugin(src, raw, manifest, enabled, detectedRuntime, pinned)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to parse ${src.name}", t)
                quarantineBroken(src, t)
            }
        }

        val ordered = orderByDependencies(pending)

        for (item in ordered) {
            try {
                val (src, raw, manifest, enabled, runtime, pinned) = item
                if (enabled) {
                    val missingDependencies = manifest.dependencies.filterNot { sessions.containsKey(it) }
                    if (missingDependencies.isNotEmpty()) {
                        Log.w(TAG, "Plugin ${manifest.id} disabled, missing dependencies: ${missingDependencies.joinToString()}")
                        loaded[manifest.id] = LoadedPlugin(manifest, src, raw, false, runtime, pinned)
                        updateMetaEnabled(manifest.id, false)
                        continue
                    }
                    val session = try {
                        createStartedSession(runtime, manifest, raw)
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
                loaded[manifest.id] = LoadedPlugin(manifest, src, raw, enabled, runtime, pinned)
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
                    val sources = listOf(File(sourcesDir, "${m.id}.js"), File(sourcesDir, "${m.id}.dolphyplugin"))
                    if (sources.none { it.exists() } || m.id in permanentlyDeleted) {
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

    private fun saveMeta(manifest: PluginManifest, enabled: Boolean = true, runtime: String = "python", pinned: Boolean = false) {
        val serial = SerializableManifest(
            id = manifest.id,
            name = manifest.name,
            version = manifest.version,
            description = manifest.description,
            author = manifest.author,
            icon = manifest.icon,
            enabled = enabled,
            isLibrary = manifest.isLibrary || manifest.isDesignLibrary,
            isDesignLibrary = manifest.isDesignLibrary,
            runtime = runtime,
            pinned = pinned,
            dependencies = manifest.dependencies,
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

    private fun updateMetaPinned(pluginId: String, pinned: Boolean) {
        val file = File(metaDir, "$pluginId.json")
        if (!file.exists()) return
        runCatching {
            val meta = json.decodeFromString(SerializableManifest.serializer(), file.readText())
            file.writeText(
                json.encodeToString(SerializableManifest.serializer(), meta.copy(pinned = pinned)),
                Charsets.UTF_8,
            )
        }
    }

    fun dispatchEvent(name: String, payload: Any? = null) {
        sessions.values.toList().forEach { session ->
            runCatching { session.onEvent(name, payload) }
                .onFailure { Log.w(TAG, "dispatchEvent $name ${session.manifest.id}", it) }
        }
    }

    fun invokeServices(serviceId: String, operation: String, payloadJson: String = "{}"): List<PluginServiceResult> {
        val normalized = serviceId.trim().lowercase()
        return PluginRegistry.services.value
            .filter { it.serviceId == normalized }
            .sortedByDescending { it.priority }
            .mapNotNull { provider ->
                val value = sessions[provider.pluginId]
                    ?.invokeService(normalized, operation, payloadJson)
                    ?.takeUnless { it == "None" || it == "null" }
                    ?: return@mapNotNull null
                PluginServiceResult(provider.pluginId, value)
            }
    }

    fun invokeActionHooks(action: String, payloadJson: String = "{}"): PluginActionDecision {
        val normalized = action.trim().lowercase()
        var currentPayload = payloadJson
        var handled = false
        var result: String? = null
        var handlingPlugin: String? = null
        val hooks = PluginRegistry.actionHooks.value
            .filter { patternMatches(it.actionPattern, normalized) }
            .sortedByDescending { it.priority }
        for (hook in hooks) {
            val raw = sessions[hook.pluginId]
                ?.invokeActionHook(normalized, currentPayload)
                ?.takeUnless { it == "None" || it == "null" }
                ?: continue
            val objectResult = runCatching { org.json.JSONObject(raw) }.getOrNull()
            if (objectResult != null) {
                if (objectResult.has("payload")) {
                    currentPayload = objectResult.opt("payload")?.toString() ?: currentPayload
                }
                if (objectResult.optBoolean("handled", false)) {
                    handled = true
                    handlingPlugin = hook.pluginId
                }
                if (objectResult.has("result")) result = objectResult.opt("result")?.toString()
                if (objectResult.optBoolean("cancel", false)) {
                    return PluginActionDecision(true, handled, currentPayload, result, hook.pluginId)
                }
            } else if (raw.equals("false", true)) {
                return PluginActionDecision(true, handled, currentPayload, result, hook.pluginId)
            } else if (raw.equals("true", true)) {
                handled = true
                handlingPlugin = hook.pluginId
            }
        }
        return PluginActionDecision(false, handled, currentPayload, result, handlingPlugin)
    }

    fun routeMatches(pattern: String, route: String): Boolean = patternMatches(pattern, route)

    private fun patternMatches(pattern: String, value: String): Boolean {
        val regex = buildString {
            append('^')
            pattern.lowercase().split('*').forEachIndexed { index, part ->
                if (index > 0) append(".*")
                append(Regex.escape(part))
            }
            append('$')
        }
        return Regex(regex).matches(value.lowercase())
    }

    private fun createSession(runtime: String, manifest: PluginManifest, raw: String): PluginSession {
        return if (runtime == "python") {
            PythonPluginSession(appContext, manifest, raw)
        } else {
            JsPluginSession(appContext, manifest, raw)
        }
    }

    private fun createStartedSession(runtime: String, manifest: PluginManifest, raw: String): PluginSession {
        pluginStartupActive = true
        return try {
            createSession(runtime, manifest, raw).also { it.start() }
        } finally {
            pluginStartupActive = false
        }
    }

    private fun parseManifest(runtime: String, raw: String, fallbackId: String, fallbackName: String): PluginManifest {
        return if (runtime == "python") {
            PythonPluginSession.parseManifest(raw, fallbackId, fallbackName)
        } else {
            JsPluginSession.parseManifest(raw, fallbackId, fallbackName)
        }
    }

    private fun looksLikePython(raw: String): Boolean {
        return Regex("(?m)^\\s*(__id__|class\\s+\\w+\\s*\\(BasePlugin\\)|from\\s+java\\s+import|def\\s+screen_)").containsMatchIn(raw)
    }

    private fun publish() {
        _plugins.value = loaded.values.sortedWith(compareByDescending<LoadedPlugin> { it.pinned }.thenBy { it.manifest.name.lowercase() })
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
        val runtime: String = "javascript",
        val icon: String = "extension",
        val pinned: Boolean = false,
        val dependencies: List<String> = emptyList(),
    ) {
        fun toManifest() = PluginManifest(
            id, name, version, description, author,
            isLibrary = isLibrary || isDesignLibrary,
            isDesignLibrary = isDesignLibrary,
            icon = icon,
            dependencies = dependencies,
        )
    }

    private fun orderByDependencies(items: List<PendingPlugin>): List<PendingPlugin> {
        val remaining = items.associateBy { it.manifest.id }.toMutableMap()
        val result = mutableListOf<PendingPlugin>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.values
                .filter { item -> item.manifest.dependencies.none { it in remaining } }
                .sortedWith(compareByDescending<PendingPlugin> { it.manifest.isLibrary || it.manifest.isDesignLibrary }.thenBy { it.manifest.id })
            if (ready.isEmpty()) {
                result += remaining.values.sortedBy { it.manifest.id }
                break
            }
            ready.forEach {
                result += it
                remaining.remove(it.manifest.id)
            }
        }
        return result
    }

    private const val MAX_PLUGIN_SOURCE_BYTES = 48 * 1024 * 1024
    private const val SAFE_MODE_PREFS = "plugin_safe_mode"
    private const val KEY_SAFE_MODE = "active"
    private const val KEY_SAFE_MODE_NOTICE = "notice"
    private const val SAFE_MODE_LOG_FILE = "plugin_safe_mode_crash.log"
}

