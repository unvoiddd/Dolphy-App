package com.droid.dolphy.plugin.python

import android.content.Context
import android.nfc.Tag
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.droid.dolphy.plugin.PluginRegistry
import com.droid.dolphy.plugin.PluginSession
import com.droid.dolphy.plugin.model.PluginBottomSheetSpec
import com.droid.dolphy.plugin.model.PluginDialogSpec
import com.droid.dolphy.plugin.model.PluginManifest
import com.droid.dolphy.plugin.model.PluginMediaRequest
import com.droid.dolphy.plugin.model.PluginPermissionRequest
import com.droid.dolphy.plugin.model.PluginSnackbarSpec
import com.droid.dolphy.plugin.model.UiNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PythonPluginSession(
    private val appContext: Context,
    override val manifest: PluginManifest,
    private val sourceCode: String,
) : PluginSession {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val state = ConcurrentHashMap<String, Any?>()
    private val _dialog = MutableStateFlow<PluginDialogSpec?>(null)
    override val dialog: StateFlow<PluginDialogSpec?> = _dialog.asStateFlow()
    private val _snackbar = MutableStateFlow<PluginSnackbarSpec?>(null)
    override val snackbar: StateFlow<PluginSnackbarSpec?> = _snackbar.asStateFlow()
    private val _bottomSheet = MutableStateFlow<PluginBottomSheetSpec?>(null)
    override val bottomSheet: StateFlow<PluginBottomSheetSpec?> = _bottomSheet.asStateFlow()
    private var pythonSession: PyObject? = null
    private var bridge: PythonPluginBridge? = null
    private var stateVersion = 0
    override var lastError: String? = null
        private set
    override var navigateToScreen: ((String) -> Unit)? = null
    override var requestUiRefresh: (() -> Unit)? = null
    override var mediaRequestHandler: ((PluginMediaRequest) -> Unit)? = null
    override var permissionRequestHandler: ((PluginPermissionRequest) -> Unit)? = null

    override fun start() {
        synchronized(this) {
            stopInternal(false)
            if (!Python.isStarted()) Python.start(AndroidPlatform(appContext))
            val localBridge = PythonPluginBridge(
                context = appContext,
                pluginId = manifest.id,
                dependencies = manifest.dependencies,
                onNavigate = { screen -> navigateToScreen?.invoke(screen) },
                onRefresh = {
                    stateVersion += 1
                    requestUiRefresh?.invoke()
                },
            )
            bridge = localBridge
            try {
                val manifestJson = JSONObject()
                    .put("id", manifest.id)
                    .put("name", manifest.name)
                    .put("version", manifest.version)
                    .put("description", manifest.description)
                    .put("author", manifest.author)
                    .toString()
                val runtime = Python.getInstance().getModule("dolphy_runtime")
                pythonSession = runtime.callAttr("create_session", sourceCode, localBridge, manifestJson)
                pythonSession?.callAttr("start")
                lastError = null
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
                Log.e(TAG, "Python plugin ${manifest.id} start failed", t)
                stopInternal(false)
                throw t
            }
        }
    }

    override fun stop() {
        synchronized(this) {
            runCatching { pythonSession?.callAttr("stop") }
            stopInternal(true)
        }
    }

    private fun stopInternal(clearRegistry: Boolean) {
        pythonSession = null
        bridge?.release()
        bridge = null
        _dialog.value = null
        _snackbar.value = null
        _bottomSheet.value = null
        if (clearRegistry) PluginRegistry.clearPlugin(manifest.id)
    }

    override fun renderScreen(screenId: String): UiNode {
        return synchronized(this) {
            val session = pythonSession ?: return@synchronized errorNode(lastError ?: "Плагин не запущен")
            try {
                val stateJson = JSONObject(state as Map<*, *>).toString()
                val raw = session.callAttr("render", screenId, stateJson).toString()
                PythonUiParser.parse(raw).also { lastError = null }
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
                Log.e(TAG, "Python plugin ${manifest.id} render failed", t)
                errorNode(lastError ?: "Ошибка Python")
            }
        }
    }

    override fun onCallback(id: String?, value: Any?) {
        if (id == null) return
        val action = Runnable {
            synchronized(this) {
                try {
                    val valueJson = when (value) {
                        null -> null
                        is String -> JSONObject.quote(value)
                        is Number, is Boolean -> value.toString()
                        else -> JSONObject.wrap(value).toString()
                    }
                    pythonSession?.callAttr("invoke", id, valueJson)
                    stateVersion += 1
                    requestUiRefresh?.invoke()
                } catch (t: Throwable) {
                    lastError = t.message ?: t.javaClass.simpleName
                    Log.e(TAG, "Python callback $id failed", t)
                    requestUiRefresh?.invoke()
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) action.run() else mainHandler.post(action)
    }

    override fun getStateVersion(): Int = stateVersion
    override fun dismissDialog() { _dialog.value = null }
    override fun onDialogButton(callbackId: String?) { _dialog.value = null; onCallback(callbackId, null) }
    override fun dismissSnackbar() { _snackbar.value = null }
    override fun onSnackbarAction(callbackId: String?) { _snackbar.value = null; onCallback(callbackId, null) }
    override fun dismissBottomSheet() { _bottomSheet.value = null }
    override fun onBottomSheetButton(callbackId: String?) { _bottomSheet.value = null; onCallback(callbackId, null) }

    override fun importMediaUri(uriString: String, destPath: String?, includeBase64: Boolean): String {
        return bridge?.getAndroid()?.importUriToSandbox(Uri.parse(uriString), destPath, includeBase64)
            ?: JSONObject().put("ok", false).put("error", "no_session").toString()
    }

    override fun createCameraCaptureTarget(fileName: String?): Pair<File, Uri>? = bridge?.getAndroid()?.createCameraCaptureTarget(fileName)

    override fun importCameraFile(file: File, destPath: String?, includeBase64: Boolean): String {
        return bridge?.getAndroid()?.importCameraFile(file, destPath, includeBase64)
            ?: JSONObject().put("ok", false).put("error", "no_session").toString()
    }

    override fun onNfcTag(tag: Tag) {
        bridge?.getDevice()?.onNfcTagDiscovered(tag)
        runCatching { pythonSession?.callAttr("event", "nfc_tag", tag) }
    }

    override fun onEvent(name: String, payload: Any?) {
        runCatching { pythonSession?.callAttr("event", name, payload) }
            .onFailure { Log.w(TAG, "Python event $name failed", it) }
    }

    override fun invokeService(serviceId: String, operation: String, payloadJson: String): String? {
        return synchronized(this) {
            runCatching { pythonSession?.callAttr("service", serviceId, operation, payloadJson)?.toString() }
                .onFailure { Log.w(TAG, "Python service $serviceId.$operation failed", it) }
                .getOrNull()
        }
    }

    override fun invokeActionHook(action: String, payloadJson: String): String? {
        return synchronized(this) {
            runCatching { pythonSession?.callAttr("action_hook", action, payloadJson)?.toString() }
                .onFailure { Log.w(TAG, "Python action hook $action failed", it) }
                .getOrNull()
        }
    }

    private fun errorNode(message: String): UiNode = UiNode.Column(
        children = listOf(
            UiNode.Text("Ошибка плагина", "headlineSmall"),
            UiNode.Spacer(8f),
            UiNode.Text(message, "bodyMedium"),
        ),
        padding = 16f,
        spacing = 8f,
        fillMaxSize = true,
    )

    companion object {
        private const val TAG = "PythonPluginSession"

        fun parseManifest(source: String, fallbackId: String, fallbackName: String): PluginManifest {
            fun stringValue(key: String): String? {
                val pattern = Regex("(?m)^\\s*${Regex.escape(key)}\\s*=\\s*([\"'])(.*?)\\1\\s*$")
                return pattern.find(source)?.groupValues?.getOrNull(2)
            }
            fun boolValue(key: String): Boolean = Regex("(?m)^\\s*${Regex.escape(key)}\\s*=\\s*(True|true|1)\\s*$").containsMatchIn(source)
            fun listValue(key: String): List<String> {
                val body = Regex("(?ms)^\\s*${Regex.escape(key)}\\s*=\\s*[\\[(](.*?)[\\])]\\s*$")
                    .find(source)?.groupValues?.getOrNull(1).orEmpty()
                return Regex("[\"']([^\"']+)[\"']")
                    .findAll(body)
                    .map {
                        it.groupValues[1]
                            .trim()
                            .replace(Regex("[^A-Za-z0-9_-]"), "_")
                            .lowercase()
                            .take(64)
                    }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .toList()
            }
            val id = (stringValue("__id__") ?: fallbackId)
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                .lowercase()
                .take(64)
                .ifBlank { fallbackId }
            return PluginManifest(
                id = id,
                name = stringValue("__name__") ?: fallbackName,
                version = stringValue("__version__") ?: "1.0",
                description = stringValue("__description__") ?: "",
                author = stringValue("__author__") ?: "",
                isLibrary = boolValue("__library__"),
                isDesignLibrary = boolValue("__design_library__"),
                icon = stringValue("__icon__") ?: "extension",
                dependencies = listValue("__dependencies__"),
            )
        }
    }
}
