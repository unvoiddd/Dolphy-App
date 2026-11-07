package com.droid.dolphy.plugin.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.droid.dolphy.R
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.model.LoadedPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale


data class StoreSource(
    val id: String,
    val label: String,
    val indexUrl: String,
    val builtIn: Boolean = false,
)

data class StorePlugin(
    val key: String,
    val sourceId: String,
    val catalogId: Int,
    val name: String,
    val version: String,
    val author: String,
    val descriptionRu: String,
    val descriptionEn: String,
    val file: String,
    val downloadUrl: String,
    val postUrl: String?,
    val updatedAt: String,
    val translatedName: String? = null,
    val translatedDescription: String? = null,
) {
    val description: String
        get() = if (Locale.getDefault().language.equals("ru", true) && descriptionRu.isNotBlank()) {
            descriptionRu
        } else {
            descriptionEn.ifBlank { descriptionRu }
        }

    val shortDescription: String
        get() = description.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    val displayName: String get() = translatedName?.takeIf { it.isNotBlank() } ?: name

    val displayDescription: String get() = translatedDescription?.takeIf { it.isNotBlank() } ?: description

    val displayShortDescription: String
        get() = displayDescription.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}

enum class StoreLoadState { IDLE, LOADING, READY, ERROR }

object PluginStoreRepository {
    private const val TAG = "PluginStore"
    private const val PREFS = "plugin_store"
    private const val KEY_SOURCES = "sources"
    private const val KEY_ACTIVE = "active_source"
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val KEY_TRANSLATE = "translate"
    private const val MAX_INDEX_BYTES = 4 * 1024 * 1024
    private const val MAX_PLUGIN_BYTES = 48 * 1024 * 1024

    private val DEFAULT_SOURCE = StoreSource(
        id = "official",
        label = "Dolphy Plugins",
        indexUrl = "https://raw.githubusercontent.com/unvoiddd/Dolphy-app-Plugins/HEAD/index.json",
        builtIn = true,
    )

    private lateinit var appContext: Context

    private val _sources = MutableStateFlow(listOf(DEFAULT_SOURCE))
    val sources: StateFlow<List<StoreSource>> = _sources.asStateFlow()

    private val _activeSourceId = MutableStateFlow(DEFAULT_SOURCE.id)
    val activeSourceId: StateFlow<String> = _activeSourceId.asStateFlow()

    private val _plugins = MutableStateFlow<List<StorePlugin>>(emptyList())
    val plugins: StateFlow<List<StorePlugin>> = _plugins.asStateFlow()

    private val _loadState = MutableStateFlow(StoreLoadState.IDLE)
    val loadState: StateFlow<StoreLoadState> = _loadState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _installing = MutableStateFlow<Set<String>>(emptySet())
    val installing: StateFlow<Set<String>> = _installing.asStateFlow()

    private val _selected = MutableStateFlow<StorePlugin?>(null)
    val selected: StateFlow<StorePlugin?> = _selected.asStateFlow()

    private val _autoUpdate = MutableStateFlow(false)
    val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    private val _translateEnabled = MutableStateFlow(true)
    val translateEnabled: StateFlow<Boolean> = _translateEnabled.asStateFlow()

    @Volatile private var initialized = false
    @Volatile private var autoUpdateRan = false

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true
        PluginTranslator.initialize(context)
        runCatching { loadSources() }
            .onFailure { Log.w(TAG, "loadSources failed", it) }
    }

    fun activeSource(): StoreSource =
        _sources.value.firstOrNull { it.id == _activeSourceId.value } ?: DEFAULT_SOURCE

    fun select(plugin: StorePlugin?) {
        _selected.value = plugin
    }

    fun pluginByKey(key: String?): StorePlugin? =
        key?.let { k -> _plugins.value.firstOrNull { it.key == k } }

    fun installedMatch(plugin: StorePlugin, installed: List<LoadedPlugin>): LoadedPlugin? =
        installed.firstOrNull { it.manifest.name.trim().equals(plugin.name.trim(), ignoreCase = true) }


    suspend fun refresh(force: Boolean = false) {
        if (_loadState.value == StoreLoadState.LOADING) return
        if (!force && _loadState.value == StoreLoadState.READY && _plugins.value.isNotEmpty()) return
        _loadState.value = StoreLoadState.LOADING
        _error.value = null
        val source = activeSource()
        val result = runCatching { withContext(Dispatchers.IO) { fetchIndex(source) } }
        result
            .onSuccess { list ->
                _plugins.value = list
                _loadState.value = StoreLoadState.READY
                _error.value = null
                if (_translateEnabled.value) {
                    runCatching { withContext(Dispatchers.IO) { translateCatalog(list) } }
                }
            }
            .onFailure { t ->
                Log.e(TAG, "refresh failed", t)
                _plugins.value = emptyList()
                _loadState.value = StoreLoadState.ERROR
                _error.value = t.message ?: appContext.getString(R.string.store_error_catalog)
            }
    }

    private fun fetchIndex(source: StoreSource): List<StorePlugin> {
        val raw = httpGetText(source.indexUrl, MAX_INDEX_BYTES)
        val root = JSONObject(raw)
        val array: JSONArray = root.optJSONArray("plugins") ?: JSONArray()
        val out = ArrayList<StorePlugin>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val file = obj.optString("file").trim()
            val download = obj.optString("download_url").trim()
                .ifBlank { rawUrlFor(source, file) }
            if (download.isBlank()) continue
            var descRu = ""
            var descEn = ""
            when (val d = obj.opt("description")) {
                is JSONObject -> {
                    descRu = d.optString("ru").trim()
                    descEn = d.optString("en").trim()
                }
                is String -> {
                    descRu = d.trim()
                    descEn = d.trim()
                }
            }
            out += StorePlugin(
                key = source.id + "|" + file.ifBlank { download },
                sourceId = source.id,
                catalogId = obj.optInt("id", i),
                name = obj.optString("name").ifBlank { appContext.getString(R.string.store_plugin) },
                version = obj.optString("version", "1.0"),
                author = obj.optString("author").trim(),
                descriptionRu = descRu,
                descriptionEn = descEn,
                file = file,
                downloadUrl = download,
                postUrl = obj.optString("post_url").trim().ifBlank { null },
                updatedAt = obj.optString("updated_at").trim(),
            )
        }
        return out.sortedByDescending { it.updatedAt }
    }


    private fun deviceLang(): String = PluginTranslator.deviceLang()

    private suspend fun translateCatalog(list: List<StorePlugin>) {
        val target = deviceLang()
        val translated = list.map { plugin ->
            val tn = PluginTranslator.translate(plugin.name, target)
            val td = plugin.description.takeIf { it.isNotBlank() }
                ?.let { PluginTranslator.translate(it, target) }
            plugin.copy(
                translatedName = tn?.takeIf { it.isNotBlank() && !it.equals(plugin.name, ignoreCase = true) },
                translatedDescription = td?.takeIf { it.isNotBlank() },
            )
        }
        if (_plugins.value.map { it.key } == list.map { it.key }) {
            _plugins.value = translated
        }
    }

    private fun rawUrlFor(source: StoreSource, file: String): String {
        if (file.isBlank()) return ""
        val base = source.indexUrl.substringBeforeLast('/')
        return "$base/$file"
    }

    suspend fun install(plugin: StorePlugin): Result<Unit> {
        if (!initialized) return Result.failure(IllegalStateException(appContext.getString(R.string.store_error_not_ready)))
        markInstalling(plugin.key, true)
        return try {
            withContext(Dispatchers.IO) {
                val bytes = httpGetBytes(plugin.downloadUrl, MAX_PLUGIN_BYTES)
                val baseName = plugin.file.substringAfterLast('/')
                    .ifBlank { "${sanitize(plugin.name)}.dolphyplugin" }
                val safeName = if (baseName.endsWith(".dolphyplugin", true) || baseName.endsWith(".js", true)) {
                    baseName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                } else {
                    "${sanitize(plugin.name)}.dolphyplugin"
                }
                val dir = File(appContext.cacheDir, "store_downloads").apply { mkdirs() }
                val tmp = File(dir, safeName)
                tmp.writeBytes(bytes)
                val installed = PluginManager.installFromFile(tmp)
                tmp.delete()
                installed.getOrThrow()
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "install ${plugin.name}", t)
            Result.failure(Exception(t.message ?: appContext.getString(R.string.store_error_install), t))
        } finally {
            markInstalling(plugin.key, false)
        }
    }

    fun openPost(context: Context, plugin: StorePlugin): Boolean {
        val url = plugin.postUrl ?: return false
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    private fun markInstalling(key: String, value: Boolean) {
        _installing.value = if (value) _installing.value + key else _installing.value - key
    }

    private fun httpGetText(url: String, limit: Int): String =
        httpGetBytes(url, limit).toString(Charsets.UTF_8)

    private fun httpGetBytes(url: String, limit: Int): ByteArray {
        var current = url
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Dolphy-PluginStore")
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IllegalStateException(appContext.getString(R.string.store_error_redirect_no_url, code))
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code")
                }
                val out = ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > limit) throw IllegalStateException(appContext.getString(R.string.store_error_file_too_large))
                        out.write(buffer, 0, read)
                    }
                }
                return out.toByteArray()
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException(appContext.getString(R.string.store_error_too_many_redirects))
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_-]"), "_").lowercase().ifBlank { "plugin" }


    fun addSource(input: String): Result<StoreSource> {
        val indexUrl = normalizeToIndexUrl(input.trim())
            ?: return Result.failure(IllegalArgumentException(appContext.getString(R.string.store_error_parse_url)))
        if (_sources.value.any { it.indexUrl.equals(indexUrl, true) }) {
            val existing = _sources.value.first { it.indexUrl.equals(indexUrl, true) }
            setActive(existing.id)
            return Result.success(existing)
        }
        val source = StoreSource(
            id = "src_" + System.currentTimeMillis().toString(36),
            label = labelFor(indexUrl),
            indexUrl = indexUrl,
        )
        _sources.value = _sources.value + source
        _activeSourceId.value = source.id
        saveSources()
        return Result.success(source)
    }

    fun removeSource(id: String) {
        val target = _sources.value.firstOrNull { it.id == id } ?: return
        if (target.builtIn) return
        _sources.value = _sources.value.filterNot { it.id == id }
        if (_activeSourceId.value == id) {
            _activeSourceId.value = _sources.value.firstOrNull()?.id ?: DEFAULT_SOURCE.id
        }
        saveSources()
    }

    fun setActive(id: String) {
        if (_sources.value.none { it.id == id }) return
        if (_activeSourceId.value == id) return
        _activeSourceId.value = id
        _plugins.value = emptyList()
        _loadState.value = StoreLoadState.IDLE
        saveSources()
    }

    private fun normalizeToIndexUrl(input: String): String? {
        if (input.isBlank()) return null
        val cleaned = input.removeSuffix("/")
        if (cleaned.endsWith(".json", true)) return cleaned
        val ghMatch = Regex("github\\.com[/:]([^/]+)/([^/]+)", RegexOption.IGNORE_CASE).find(cleaned)
        if (ghMatch != null) {
            val owner = ghMatch.groupValues[1]
            val repo = ghMatch.groupValues[2].removeSuffix(".git")
            return "https://raw.githubusercontent.com/$owner/$repo/HEAD/index.json"
        }
        val shortMatch = Regex("^([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)$").find(cleaned)
        if (shortMatch != null) {
            val owner = shortMatch.groupValues[1]
            val repo = shortMatch.groupValues[2].removeSuffix(".git")
            return "https://raw.githubusercontent.com/$owner/$repo/HEAD/index.json"
        }
        if (cleaned.startsWith("http", true)) {
            return if (cleaned.endsWith("index.json", true)) cleaned else "$cleaned/index.json"
        }
        return null
    }

    private fun labelFor(indexUrl: String): String {
        val gh = Regex("githubusercontent\\.com/([^/]+)/([^/]+)", RegexOption.IGNORE_CASE).find(indexUrl)
        if (gh != null) return "${gh.groupValues[1]}/${gh.groupValues[2]}"
        return runCatching { URL(indexUrl).host }.getOrNull() ?: appContext.getString(R.string.store_source)
    }

    private fun loadSources() {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_SOURCES, null)
        val list = linkedMapOf(DEFAULT_SOURCE.id to DEFAULT_SOURCE)
        if (!stored.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(stored)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank() || id == DEFAULT_SOURCE.id) continue
                    list[id] = StoreSource(
                        id = id,
                        label = o.optString("label").ifBlank { appContext.getString(R.string.store_source) },
                        indexUrl = o.optString("indexUrl"),
                        builtIn = false,
                    )
                }
            }
        }
        _sources.value = list.values.toList()
        val active = prefs.getString(KEY_ACTIVE, DEFAULT_SOURCE.id) ?: DEFAULT_SOURCE.id
        _activeSourceId.value = if (list.containsKey(active)) active else DEFAULT_SOURCE.id
        _autoUpdate.value = prefs.getBoolean(KEY_AUTO_UPDATE, false)
        _translateEnabled.value = prefs.getBoolean(KEY_TRANSLATE, true)
    }

    fun setAutoUpdate(enabled: Boolean) {
        _autoUpdate.value = enabled
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    fun setTranslateEnabled(enabled: Boolean) {
        _translateEnabled.value = enabled
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TRANSLATE, enabled).apply()
    }

    suspend fun runAutoUpdateOnce(installed: List<LoadedPlugin>) {
        if (!_autoUpdate.value || autoUpdateRan) return
        autoUpdateRan = true
        refresh(force = false)
        val catalog = _plugins.value
        for (plugin in catalog) {
            val match = installedMatch(plugin, installed) ?: continue
            if (match.manifest.version.trim() != plugin.version.trim()) {
                runCatching { install(plugin) }
            }
        }
    }

    private fun saveSources() {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray()
        _sources.value.filterNot { it.builtIn }.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("label", s.label)
                    .put("indexUrl", s.indexUrl),
            )
        }
        prefs.edit()
            .putString(KEY_SOURCES, arr.toString())
            .putString(KEY_ACTIVE, _activeSourceId.value)
            .apply()
    }
}

fun storeIconFor(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        "ir" in n || "инфра" in n || "transceiver" in n || "remote" in n || "tv" in n ->
            Icons.Default.SettingsRemote
        "usb" in n -> Icons.Default.Usb
        "ble" in n || "bluetooth" in n || "bt " in n || "radar" in n -> Icons.Default.Bluetooth
        "wifi" in n || "wi-fi" in n -> Icons.Default.Wifi
        "battery" in n || "батар" in n -> Icons.Default.BatteryChargingFull
        "gps" in n || "spoof" in n || "icloud" in n || "location" in n -> Icons.Default.LocationOn
        "terminal" in n || "shell" in n -> Icons.Default.Terminal
        "monitor" in n || "status" in n || "hardware" in n || "performance" in n || "booster" in n ->
            Icons.Default.Speed
        "metal" in n || "металл" in n -> Icons.Default.Sensors
        "level" in n || "уровень" in n -> Icons.Default.Straighten
        "morse" in n -> Icons.Default.Vibration
        "web" in n || "site" in n -> Icons.Default.Public
        "guard" in n || "anti" in n || "detector" in n -> Icons.Default.Shield
        "scanner" in n || "scan" in n -> Icons.Default.Radar
        "cortex" in n || "module" in n -> Icons.Default.Memory
        else -> Icons.Default.Extension
    }
}
