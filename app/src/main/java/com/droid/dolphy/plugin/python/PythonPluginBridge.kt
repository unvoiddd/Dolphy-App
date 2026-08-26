package com.droid.dolphy.plugin.python

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.chaquo.python.PyObject
import com.droid.dolphy.RootUtils
import com.droid.dolphy.ShizukuHelper
import com.droid.dolphy.plugin.PluginRegistry
import com.droid.dolphy.plugin.PluginReflection
import com.droid.dolphy.plugin.PluginBleModeRegistry
import com.droid.dolphy.plugin.PluginRuntimeAccess
import com.droid.dolphy.plugin.PluginDexRegistry
import com.droid.dolphy.plugin.PluginDownloadPolicy
import com.droid.dolphy.IrRepository
import com.droid.dolphy.UserIrRemoteStore
import com.droid.dolphy.plugin.bridge.PluginAndroidApis
import com.droid.dolphy.plugin.bridge.PluginDeviceApis
import com.droid.dolphy.plugin.model.OtherCardContribution
import com.droid.dolphy.plugin.model.OtherSections
import com.droid.dolphy.plugin.model.PluginActionHookContribution
import com.droid.dolphy.plugin.model.PluginScreenContribution
import com.droid.dolphy.plugin.model.PluginServiceContribution
import com.droid.dolphy.plugin.model.SettingsItemContribution
import com.droid.dolphy.plugin.model.SettingsSectionContribution
import com.droid.dolphy.plugin.model.PluginBleModeContribution
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import java.security.MessageDigest

class PythonPluginBridge(
    private val context: Context,
    private val pluginId: String,
    private val dependencies: List<String>,
    private val onNavigate: (String) -> Unit,
    private val onRefresh: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("plugin_prefs_$pluginId", Context.MODE_PRIVATE)
    private val androidApis = PluginAndroidApis(context, pluginId)
    private val deviceApis = PluginDeviceApis(context, pluginId)

    fun getContext(): Context = context
    fun getApplicationContext(): Context = context.applicationContext
    fun getActivity(): android.app.Activity? = PluginRuntimeAccess.activity()
    fun getClassLoader(): ClassLoader = context.classLoader
    fun getAndroid(): PluginAndroidApis = androidApis
    fun getDevice(): PluginDeviceApis = deviceApis
    fun getPluginId(): String = pluginId
    fun getDependencies(): Array<String> = dependencies.toTypedArray()
    fun getDependencyClassLoader(): ClassLoader = PluginDexRegistry.dependencyParent(context.classLoader, dependencies)

    fun loadDexBase64(moduleName: String, encoded: String, sha256: String): ClassLoader {
        return PluginDexRegistry.loadBase64(context, pluginId, moduleName, encoded, sha256, dependencies)
    }

    fun loadDexBase64(moduleName: String, encoded: String): ClassLoader {
        return loadDexBase64(moduleName, encoded, "")
    }

    fun loadDexFile(moduleName: String, path: String, sha256: String): ClassLoader {
        return PluginDexRegistry.loadFile(context, pluginId, moduleName, File(path), sha256, dependencies)
    }

    fun loadDexFile(moduleName: String, path: String): ClassLoader {
        return loadDexFile(moduleName, path, "")
    }

    fun getDexClassLoader(moduleName: String): ClassLoader? = PluginDexRegistry.loader(pluginId, moduleName)

    fun loadDexClass(moduleName: String, className: String): Class<*> {
        return PluginDexRegistry.loadClass(pluginId, moduleName, className)
    }

    fun newDexInstance(moduleName: String, className: String): Any {
        val type = loadDexClass(moduleName, className)
        val constructor = type.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
    }

    fun exportDexClass(exportName: String, moduleName: String, className: String): Boolean {
        val type = loadDexClass(moduleName, className)
        return PluginDexRegistry.exportClass(pluginId, exportName, type, moduleName)
    }

    fun exportAppClass(exportName: String, className: String): Boolean {
        val type = context.classLoader.loadClass(className)
        return PluginDexRegistry.exportClass(pluginId, exportName, type)
    }

    fun exportJavaClass(exportName: String, type: Class<*>): Boolean {
        return PluginDexRegistry.exportClass(pluginId, exportName, type)
    }

    fun importJavaClass(exportName: String): Class<*>? = PluginDexRegistry.importClass(exportName)

    fun exportJavaObject(exportName: String, value: Any): Boolean {
        return PluginDexRegistry.exportObject(pluginId, exportName, value)
    }

    fun importJavaObject(exportName: String): Any? = PluginDexRegistry.importObject(exportName)

    fun listJavaExports(): String = PluginDexRegistry.listExportsJson()

    fun findJavaClass(className: String): Class<*>? {
        return PluginReflection.findClass(getDependencyClassLoader(), className)
    }

    fun getJavaField(target: Any, fieldName: String): Any? = PluginReflection.getField(target, fieldName)

    fun setJavaField(target: Any, fieldName: String, value: Any?): Boolean {
        return PluginReflection.setField(target, fieldName, value)
    }

    fun invokeJava(target: Any, methodName: String, args: Array<Any?>): Any? {
        return PluginReflection.invoke(target, methodName, args)
    }

    fun newJavaInstance(type: Class<*>, args: Array<Any?>): Any {
        return PluginReflection.newInstance(type, args)
    }

    fun inspectJavaClass(type: Class<*>): String = PluginReflection.membersJson(type)

    fun registerModule(
        section: String,
        title: String,
        description: String,
        icon: String,
        screenId: String,
        order: Int,
    ): Boolean {
        PluginRegistry.addOtherCard(
            OtherCardContribution(
                pluginId = pluginId,
                section = OtherSections.normalize(section),
                title = title,
                description = description,
                icon = icon,
                screenId = screenId,
                order = order,
            ),
        )
        return true
    }

    fun registerSettings(title: String, itemsJson: String, order: Int = 0): Boolean {
        return try {
            val array = JSONArray(itemsJson)
            val items = mutableListOf<SettingsItemContribution>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                when (item.optString("type").lowercase()) {
                    "header" -> items += SettingsItemContribution.Header(pluginId, item.optString("title"))
                    "switch" -> items += SettingsItemContribution.SwitchItem(pluginId, item.optString("key"), item.optString("title"), item.optString("subtitle"), item.optBoolean("default"))
                    "slider" -> items += SettingsItemContribution.SliderItem(pluginId, item.optString("key"), item.optString("title"), item.optString("subtitle"), item.optDouble("min", 0.0).toFloat(), item.optDouble("max", 100.0).toFloat(), item.optDouble("default", 50.0).toFloat(), item.optInt("steps"))
                    "nav" -> items += SettingsItemContribution.NavItem(pluginId, item.optString("title"), item.optString("subtitle"), item.optString("icon", "extension"), item.optString("screen", "main"))
                    "card" -> items += SettingsItemContribution.CardItem(pluginId, item.optString("title"), item.optString("subtitle"), item.optString("icon", "extension"), item.optString("screen").ifBlank { null })
                }
            }
            PluginRegistry.addSettingsSection(SettingsSectionContribution(pluginId, title, items, order))
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun registerScreenHook(routePattern: String, screenId: String, mode: String = "overlay", priority: Int = 0): Boolean {
        val normalizedMode = mode.lowercase().takeIf { it in setOf("replace", "overlay", "top", "bottom", "fab") }
            ?: return false
        PluginRegistry.addScreenContribution(
            PluginScreenContribution(pluginId, routePattern.trim(), screenId.trim(), normalizedMode, priority),
        )
        return true
    }

    fun registerSurfaceHook(surface: String, screenId: String, mode: String = "overlay", priority: Int = 0): Boolean {
        val normalized = surface.trim().lowercase().replace(Regex("[^a-z0-9_.-]"), "_")
        if (normalized.isEmpty()) return false
        return registerScreenHook("surface/$normalized", screenId, mode, priority)
    }

    fun registerService(serviceId: String, priority: Int = 0): Boolean {
        val normalized = serviceId.trim().lowercase()
        if (normalized.isBlank()) return false
        PluginRegistry.addService(PluginServiceContribution(pluginId, normalized, priority))
        return true
    }

    fun registerActionHook(actionPattern: String, priority: Int = 0): Boolean {
        val normalized = actionPattern.trim().lowercase()
        if (normalized.isBlank()) return false
        PluginRegistry.addActionHook(PluginActionHookContribution(pluginId, normalized, priority))
        return true
    }

    fun registerBleMode(
        modeId: String,
        title: String,
        description: String = "",
        icon: String = "bluetooth_searching",
        order: Int = 0,
    ): Boolean {
        val normalized = modeId.trim().lowercase().replace(Regex("[^a-z0-9_.-]"), "_").take(64)
        return PluginBleModeRegistry.register(
            PluginBleModeContribution(pluginId, normalized, title.trim(), description.trim(), icon.trim(), order),
        )
    }

    fun assetPath(path: String): String? = resolvePluginAsset(path)?.absolutePath

    fun downloadAssets(itemsJson: String, callback: PyObject): Boolean {
        val items = runCatching { JSONArray(itemsJson) }.getOrNull() ?: return false
        if (items.length() !in 1..64) return false
        val totalBytes = (0 until items.length()).sumOf { index ->
            items.optJSONObject(index)?.optLong("sizeBytes", 0L)?.coerceAtLeast(0L) ?: 0L
        }
        val maximumBatchBytes = (0 until items.length()).sumOf { index ->
            val item = items.optJSONObject(index)
            item?.optLong("maxBytes", item.optLong("sizeBytes", 50_000_000L))
                ?.coerceIn(1L, 100_000_000L) ?: 50_000_000L
        }
        if (maximumBatchBytes > 256_000_000L) return false
        PluginDownloadPolicy.request(pluginId, items.length(), totalBytes) { allowed ->
            if (!allowed) {
                runCatching { callback.call(JSONObject().put("ok", false).put("error", "download_denied").toString()) }
                return@request
            }
            downloadAssetAt(items, 0, JSONArray(), callback)
        }
        return true
    }

    private fun downloadAssetAt(items: JSONArray, index: Int, results: JSONArray, callback: PyObject) {
        if (index >= items.length()) {
            val failed = (0 until results.length()).any { !results.optJSONObject(it).optBoolean("ok") }
            runCatching { callback.call(JSONObject().put("ok", !failed).put("files", results).toString()) }
            return
        }
        val item = items.optJSONObject(index)
        val url = item?.optString("url").orEmpty()
        val path = item?.optString("path").orEmpty()
        val destination = resolvePluginAsset(path)
        if (url.isBlank() || destination == null) {
            results.put(JSONObject().put("ok", false).put("path", path).put("error", "invalid_item"))
            downloadAssetAt(items, index + 1, results, callback)
            return
        }
        val maxBytes = item?.optLong("maxBytes", item.optLong("sizeBytes", 50_000_000L))
            ?.coerceIn(1L, 100_000_000L) ?: 50_000_000L
        val headers = item?.optJSONObject("headers")?.toString()
        deviceApis.httpDownloadApproved(url, destination, headers, maxBytes) { resultJson ->
            val result = runCatching { JSONObject(resultJson) }.getOrElse {
                JSONObject().put("ok", false).put("error", "invalid_result")
            }
            val expected = item?.optString("sha256").orEmpty().lowercase()
            if (result.optBoolean("ok") && expected.isNotBlank()) {
                val actual = sha256(destination)
                if (actual != expected) {
                    destination.delete()
                    result.put("ok", false).put("error", "sha256_mismatch").put("actualSha256", actual)
                }
            }
            result.put("relativePath", path)
            results.put(result)
            downloadAssetAt(items, index + 1, results, callback)
        }
    }

    fun applyAsset(type: String, path: String, title: String = ""): String {
        val source = resolvePluginAsset(path)
            ?: return JSONObject().put("ok", false).put("error", "invalid_path").toString()
        if (!source.isFile) return JSONObject().put("ok", false).put("error", "file_not_found").toString()
        return runCatching {
            when (type.trim().lowercase()) {
                "bad_hid", "bad_usb", "duckyscript" -> {
                    val destination = File(context.filesDir, "plugin_bad_usb_script.txt")
                    source.copyTo(destination, overwrite = true)
                    context.getSharedPreferences("plugin_asset_bridge", Context.MODE_PRIVATE).edit()
                        .putString("bad_usb_title", title.ifBlank { source.nameWithoutExtension }).apply()
                }
                "ir", "ir_remote" -> {
                    val commands = IrRepository.parseIrFile(source)
                    if (commands.isEmpty()) error("invalid_ir_file")
                    UserIrRemoteStore.addRemote(context, title.ifBlank { source.nameWithoutExtension }, commands)
                }
                "qr_html", "qr_audio_html", "html" -> {
                    val html = source.readText(Charsets.UTF_8)
                    if (html.isBlank()) error("empty_html")
                    val destination = File(context.filesDir, "qr_spoofer/custom_page.html")
                    destination.parentFile?.mkdirs()
                    destination.writeText(html, Charsets.UTF_8)
                    context.getSharedPreferences("qr_prefs", Context.MODE_PRIVATE).edit()
                        .putString("page_type", "custom_html")
                        .putString("custom_html_name", title.ifBlank { source.name })
                        .apply()
                }
                else -> error("unsupported_asset_type")
            }
            JSONObject().put("ok", true).put("type", type).put("path", path).toString()
        }.getOrElse {
            JSONObject().put("ok", false).put("error", it.message ?: "apply_failed").toString()
        }
    }

    private fun resolvePluginAsset(path: String): File? {
        val base = androidApis.pluginFilesDir().canonicalFile
        val clean = path.trim().replace('\\', '/').removePrefix("/")
        if (clean.isBlank()) return null
        val file = File(base, clean).canonicalFile
        return file.takeIf { it.path == base.path || it.path.startsWith(base.path + File.separator) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun navigate(screenId: String) {
        mainHandler.post { onNavigate(screenId) }
    }

    fun refresh() {
        mainHandler.post {
            PluginRegistry.touch()
            onRefresh()
        }
    }

    fun toast(text: String) {
        mainHandler.post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    fun getSettingJson(key: String, fallbackJson: String): String = prefs.getString(key, null) ?: fallbackJson

    fun setSettingJson(key: String, valueJson: String): Boolean {
        prefs.edit().putString(key, valueJson).apply()
        refresh()
        return true
    }

    fun shellExec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(false).start()
            val out = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val err = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val code = process.waitFor()
            JSONObject().put("ok", code == 0).put("code", code).put("out", out).put("err", err).put("via", "shell").toString()
        } catch (t: Throwable) {
            JSONObject().put("ok", false).put("code", -1).put("out", "").put("err", t.message ?: t.javaClass.simpleName).put("via", "shell").toString()
        }
    }

    fun rootExec(command: String): String {
        return try {
            val result = RootUtils.executeRootCommand(command)
            JSONObject().put("ok", result.first == 0).put("code", result.first).put("out", result.second).put("err", "").put("via", "root").toString()
        } catch (t: Throwable) {
            JSONObject().put("ok", false).put("code", -1).put("out", "").put("err", t.message ?: t.javaClass.simpleName).put("via", "root").toString()
        }
    }

    fun shizukuExec(command: String): String = ShizukuHelper.runShellCommandWithOutput(command)

    fun smartExec(command: String): String {
        val shizuku = runCatching { JSONObject(shizukuExec(command)) }.getOrNull()
        if (shizuku?.optInt("code", -1) == 0) return shizuku.put("via", "shizuku").toString()
        val root = JSONObject(rootExec(command))
        if (root.optBoolean("ok")) return root.toString()
        return JSONObject(shellExec(command)).put("shizukuError", shizuku?.optString("err")).put("rootError", root.optString("err")).toString()
    }

    fun bleStartScan(scanId: String, callback: PyObject, batchMs: Long = 350L, maxDevices: Int = 128): Boolean {
        return deviceApis.bleStartScan(scanId, { payload -> runCatching { callback.call(payload) } }, batchMs, maxDevices)
    }

    fun bleStopScan(scanId: String) {
        deviceApis.stopBleScan(scanId)
    }

    fun release() {
        deviceApis.stopAllBleScans()
        androidApis.release()
    }
}
