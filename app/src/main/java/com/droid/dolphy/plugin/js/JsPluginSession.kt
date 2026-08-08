package com.droid.dolphy.plugin.js

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.droid.dolphy.BleSection
import com.droid.dolphy.BleSpamRuntime
import com.droid.dolphy.ContinuityMode
import com.droid.dolphy.ContinuityType
import com.droid.dolphy.EasySetupDevice
import com.droid.dolphy.SpamType
import com.droid.dolphy.plugin.PluginLibraryRegistry
import com.droid.dolphy.plugin.PluginRegistry
import com.droid.dolphy.plugin.bridge.PluginAndroidApis
import com.droid.dolphy.plugin.bridge.PluginDeviceApis
import com.droid.dolphy.IrRepository
import com.droid.dolphy.plugin.model.OtherCardContribution
import com.droid.dolphy.plugin.model.OtherSections
import com.droid.dolphy.plugin.model.PluginBottomSheetSpec
import com.droid.dolphy.plugin.model.PluginDialogSpec
import com.droid.dolphy.plugin.model.PluginManifest
import com.droid.dolphy.plugin.model.PluginMediaAction
import com.droid.dolphy.plugin.model.PluginMediaRequest
import com.droid.dolphy.plugin.model.PluginPermissionRequest
import com.droid.dolphy.plugin.model.PluginSnackbarSpec
import com.droid.dolphy.plugin.model.SettingsItemContribution
import com.droid.dolphy.plugin.model.SettingsSectionContribution
import com.droid.dolphy.plugin.model.UiNode
import com.droid.dolphy.transmitIr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger





class JsPluginSession(
    private val appContext: Context,
    val manifest: PluginManifest,
    private val sourceCode: String,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val device = PluginDeviceApis(appContext)
    private val androidApis = PluginAndroidApis(appContext, manifest.id)
    private val uiBuilder = JsUiBuilder()
    private val prefs = appContext.getSharedPreferences("plugin_prefs_${manifest.id}", Context.MODE_PRIVATE)
    private val state = ConcurrentHashMap<String, Any?>()
    private val _dialog = MutableStateFlow<PluginDialogSpec?>(null)
    val dialog: StateFlow<PluginDialogSpec?> = _dialog.asStateFlow()
    private val _snackbar = MutableStateFlow<PluginSnackbarSpec?>(null)
    val snackbar: StateFlow<PluginSnackbarSpec?> = _snackbar.asStateFlow()
    private val _bottomSheet = MutableStateFlow<PluginBottomSheetSpec?>(null)
    val bottomSheet: StateFlow<PluginBottomSheetSpec?> = _bottomSheet.asStateFlow()

    @Volatile private var scope: Scriptable? = null
    @Volatile private var started = false
    @Volatile private var stateVersion: Int = 0
    @Volatile var lastError: String? = null
        private set

    var navigateToScreen: ((String) -> Unit)? = null
    var requestUiRefresh: (() -> Unit)? = null

    
    var mediaRequestHandler: ((PluginMediaRequest) -> Unit)? = null
    var permissionRequestHandler: ((PluginPermissionRequest) -> Unit)? = null

    private val timerSeq = AtomicInteger(0)
    private val timers = ConcurrentHashMap<Int, Runnable>()

    private val refreshDebounceMs = 80L
    private val refreshRunnable = Runnable { requestUiRefresh?.invoke() }

    private fun scheduleUiRefresh() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, refreshDebounceMs)
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    fun onDialogButton(callbackId: String?) {
        _dialog.value = null
        if (callbackId != null) onCallback(callbackId, null)
    }

    fun dismissSnackbar() {
        _snackbar.value = null
    }

    fun onSnackbarAction(callbackId: String?) {
        _snackbar.value = null
        if (callbackId != null) onCallback(callbackId, null)
    }

    fun dismissBottomSheet() {
        _bottomSheet.value = null
    }

    fun onBottomSheetButton(callbackId: String?) {
        _bottomSheet.value = null
        if (callbackId != null) onCallback(callbackId, null)
    }

    private fun clearAllTimers() {
        timers.keys.toList().forEach { id ->
            timers.remove(id)?.let { mainHandler.removeCallbacks(it) }
        }
    }

    fun start() {
        synchronized(this) {
            stopInternal(clearRegistry = false)
            try {
                withRhino { cx, _ ->
                    val sc = cx.initStandardObjects()
                    scope = sc
                    uiBuilder.cx = cx
                    uiBuilder.scope = sc
                    val api = try {
                        buildApi(cx, sc)
                    } catch (t: Throwable) {

                        Log.e(TAG, "buildApi partial failure for ${manifest.id}", t)
                        lastError = "api: ${t.javaClass.simpleName}: ${t.message}"
                        cx.newObject(sc)
                    }
                    ScriptableObject.putProperty(sc, "api", api)
                    ScriptableObject.putProperty(sc, "console", buildConsole(cx, sc))
                    try {
                        cx.evaluateString(sc, sourceCode, manifest.id, 1, null)
                        callIfExists(cx, sc, "onLoad", api)
                        if (lastError == null) lastError = null
                        started = true
                    } catch (e: Exception) {
                        lastError = e.message
                        Log.e(TAG, "Plugin ${manifest.id} load failed", e)
                        started = true
                    } catch (t: Throwable) {

                        lastError = "${t.javaClass.simpleName}: ${t.message}"
                        Log.e(TAG, "Plugin ${manifest.id} fatal load error", t)
                        started = true
                        scope = sc
                    }
                }
            } catch (t: Throwable) {
                lastError = "${t.javaClass.simpleName}: ${t.message}"
                Log.e(TAG, "Plugin ${manifest.id} Rhino start failed", t)
                scope = null
                started = false

                throw t
            }
        }
    }

    fun stop() {
        synchronized(this) {
            try {
                withRhino { cx, sc ->
                    if (sc != null) callIfExists(cx, sc, "onUnload")
                }
            } catch (_: Exception) {
            }
            stopInternal(clearRegistry = true)
            device.stopAllBleScans()
            androidApis.release()
            mainHandler.removeCallbacks(refreshRunnable)
            clearAllTimers()
            _dialog.value = null
            _snackbar.value = null
            _bottomSheet.value = null
        }
    }

    private fun stopInternal(clearRegistry: Boolean) {
        scope = null
        started = false
        uiBuilder.clearAllCallbacks()
        if (clearRegistry) {
            PluginRegistry.clearPlugin(manifest.id)
            PluginLibraryRegistry.clearPlugin(manifest.id)
        }
    }

    fun renderScreen(screenId: String): UiNode {
        synchronized(this) {
            val sc = scope
            if (!started || sc == null) {
                return errorNode(lastError ?: "Плагин не загружен")
            }
            return try {
                withRhino { cx, _ ->
                    uiBuilder.clearCallbacks()
                    uiBuilder.cx = cx
                    uiBuilder.scope = sc
                    val ui = uiBuilder.createUiObject(cx, sc)
                    val api = ScriptableObject.getProperty(sc, "api")
                    val stateObj = stateToJs(cx, sc)
                    val fnName = "screen_$screenId"
                    val fn = ScriptableObject.getProperty(sc, fnName)
                    val result = when {
                        fn is org.mozilla.javascript.Function ->
                            fn.call(cx, sc, sc, arrayOf(ui, api, stateObj))
                        else -> {
                            val main = ScriptableObject.getProperty(sc, "screen_main")
                            if (main is org.mozilla.javascript.Function) {
                                main.call(cx, sc, sc, arrayOf(ui, api, stateObj))
                            } else {
                                return@withRhino errorNode("Функция $fnName / screen_main не найдена")
                            }
                        }
                    }
                    val node = uiBuilder.unwrap(result)
                    if (node is UiNode.Empty || (node is UiNode.Scaffold && node.content is UiNode.Empty)) {
                        Log.w(TAG, "render produced empty tree for ${manifest.id}/$screenId")
                        if (lastError != null) errorNode(lastError!!)
                        else errorNode("Пустой UI (проверьте return ui.scaffold({ content: ... }))")
                    } else {
                        lastError = null
                        node
                    }
                }
            } catch (e: Exception) {
                lastError = e.message
                Log.e(TAG, "renderScreen $screenId", e)
                errorNode(e.message ?: "render error")
            }
        }
    }

    fun onCallback(id: String?, value: Any? = Undefined) {
        if (id == null) return



        val run = Runnable {
            synchronized(this) {
                try {
                    withRhino { cx, sc ->
                        if (sc == null) return@withRhino
                        uiBuilder.cx = cx
                        uiBuilder.scope = sc
                        if (value === Undefined) {
                            uiBuilder.invokeCallback(id)
                        } else {
                            uiBuilder.invokeCallback(id, value)
                        }
                    }

                    scheduleUiRefresh()
                } catch (e: Exception) {
                    Log.w(TAG, "onCallback", e)
                    lastError = e.message
                    scheduleUiRefresh()
                }
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            run.run()
        } else {
            mainHandler.post(run)
        }
    }

    fun getStateVersion(): Int = stateVersion

    private fun <T> withRhino(block: (RhinoContext, Scriptable?) -> T): T {
        val cx = RhinoContext.enter()
        try {
            cx.optimizationLevel = -1
            try {
                cx.languageVersion = RhinoContext.VERSION_ES6
            } catch (_: Exception) {
            }
            return block(cx, scope)
        } finally {
            try {
                RhinoContext.exit()
            } catch (_: Exception) {
            }
        }
    }

    private fun errorNode(msg: String): UiNode {
        return UiNode.Scaffold(
            topBar = UiNode.TopBar(manifest.name, showBack = true),
            content = UiNode.Column(
                listOf(
                    UiNode.Text(manifest.name, "headlineSmall"),
                    UiNode.Spacer(8f),
                    UiNode.Text(msg, "bodyMedium", color = "error"),
                    UiNode.Spacer(12f),
                    UiNode.Text("id=${manifest.id} v${manifest.version}", "labelSmall", color = "muted"),
                ),
                padding = 16f,
                spacing = 8f,
                fillMaxSize = true,
            ),
        )
    }

    private fun callIfExists(cx: RhinoContext, sc: Scriptable, name: String, vararg args: Any?) {
        val fn = ScriptableObject.getProperty(sc, name)
        if (fn is org.mozilla.javascript.Function) {
            fn.call(cx, sc, sc, args)
        }
    }

    private fun buildApi(cx: RhinoContext, sc: Scriptable): Scriptable {
        val api = cx.newObject(sc)

        fun fn(name: String, body: (Array<Any?>) -> Any?) {
            ScriptableObject.putProperty(api, name, object : BaseFunction() {
                override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                    return body(args)
                }
            })
        }

        fn("setState") { args ->
            val obj = args.getOrNull(0)
            if (obj is Scriptable) {
                for (id in ScriptableObject.getPropertyIds(obj)) {
                    val k = id.toString()
                    state[k] = jsToJvm(ScriptableObject.getProperty(obj, k))
                }
                stateVersion++

                scheduleUiRefresh()
            }
            null
        }
        fn("getState") { _ ->



            val c = RhinoContext.getCurrentContext() ?: return@fn null
            val s = scope ?: return@fn null
            stateToJs(c, s)
        }
        fn("toast") { args ->
            val msg = args.getOrNull(0)?.toString() ?: return@fn null
            mainHandler.post {
                Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
            }
            null
        }
        fn("log") { args ->
            Log.i("Plugin:${manifest.id}", args.joinToString(" ") { it?.toString() ?: "null" })
            null
        }
        fn("navigate") { args ->
            val screen = args.getOrNull(0)?.toString() ?: return@fn null
            mainHandler.post { navigateToScreen?.invoke(screen) }
            null
        }

        val prefsObj = cx.newObject(sc)
        ScriptableObject.putProperty(prefsObj, "get", object : BaseFunction() {
            override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                val key = args.getOrNull(0)?.toString() ?: return null
                val def = args.getOrNull(1)
                return when (def) {
                    is Boolean -> prefs.getBoolean(key, def)
                    is Number -> prefs.getFloat(key, def.toFloat()).toDouble()
                    else -> prefs.getString(key, def?.toString())
                }
            }
        })
        ScriptableObject.putProperty(prefsObj, "set", object : BaseFunction() {
            override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                val key = args.getOrNull(0)?.toString() ?: return null
                when (val v = args.getOrNull(1)) {
                    is Boolean -> prefs.edit().putBoolean(key, v).apply()
                    is Number -> prefs.edit().putFloat(key, v.toFloat()).apply()
                    else -> prefs.edit().putString(key, v?.toString() ?: "").apply()
                }
                return null
            }
        })
        ScriptableObject.putProperty(api, "prefs", prefsObj)

        val other = cx.newObject(sc)
        ScriptableObject.putProperty(other, "add", object : BaseFunction() {
            override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                val p = asMap(args.getOrNull(0))
                PluginRegistry.addOtherCard(
                    OtherCardContribution(
                        pluginId = manifest.id,
                        section = OtherSections.normalize(p["section"]?.toString()),
                        title = p["title"]?.toString() ?: manifest.name,
                        description = p["description"]?.toString() ?: "",
                        icon = p["icon"]?.toString() ?: "extension",
                        iconTintArgb = (p["color"] as? Number)?.toLong(),
                        screenId = p["screen"]?.toString() ?: p["screenId"]?.toString() ?: "main",
                        order = (p["order"] as? Number)?.toInt() ?: 0,
                    )
                )
                return null
            }
        })
        ScriptableObject.putProperty(api, "other", other)

        val settings = cx.newObject(sc)
        ScriptableObject.putProperty(settings, "addSection", object : BaseFunction() {
            override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                val p = asMap(args.getOrNull(0))
                val title = p["title"]?.toString() ?: manifest.name
                val itemsRaw = p["items"]
                val items = mutableListOf<SettingsItemContribution>()
                if (itemsRaw is org.mozilla.javascript.NativeArray) {
                    for (i in 0 until itemsRaw.length.toInt()) {
                        val m = asMap(itemsRaw.get(i, itemsRaw))
                        when (m["type"]?.toString()?.lowercase()) {
                            "switch" -> items += SettingsItemContribution.SwitchItem(
                                manifest.id,
                                m["key"]?.toString() ?: "sw_$i",
                                m["title"]?.toString() ?: "",
                                m["subtitle"]?.toString() ?: "",
                                m["value"] as? Boolean ?: m["default"] as? Boolean ?: false,
                            )
                            "slider" -> items += SettingsItemContribution.SliderItem(
                                manifest.id,
                                m["key"]?.toString() ?: "sl_$i",
                                m["title"]?.toString() ?: "",
                                m["subtitle"]?.toString() ?: "",
                                (m["min"] as? Number)?.toFloat() ?: 0f,
                                (m["max"] as? Number)?.toFloat() ?: 100f,
                                (m["value"] as? Number)?.toFloat()
                                    ?: (m["default"] as? Number)?.toFloat()
                                    ?: 50f,
                                (m["steps"] as? Number)?.toInt() ?: 0,
                            )
                            "nav" -> items += SettingsItemContribution.NavItem(
                                manifest.id,
                                m["title"]?.toString() ?: "",
                                m["subtitle"]?.toString() ?: "",
                                m["icon"]?.toString() ?: "extension",
                                m["screen"]?.toString() ?: "main",
                            )
                            "card" -> items += SettingsItemContribution.CardItem(
                                manifest.id,
                                m["title"]?.toString() ?: "",
                                m["subtitle"]?.toString() ?: "",
                                m["icon"]?.toString() ?: "extension",
                                m["screen"]?.toString(),
                            )
                            "header" -> items += SettingsItemContribution.Header(
                                manifest.id,
                                m["title"]?.toString() ?: "",
                            )
                            else -> items += SettingsItemContribution.CardItem(
                                manifest.id,
                                m["title"]?.toString() ?: "Item",
                                m["subtitle"]?.toString() ?: "",
                                m["icon"]?.toString() ?: "extension",
                                m["screen"]?.toString(),
                            )
                        }
                    }
                }
                PluginRegistry.addSettingsSection(
                    SettingsSectionContribution(manifest.id, title, items, (p["order"] as? Number)?.toInt() ?: 0)
                )
                return null
            }
        })
        ScriptableObject.putProperty(api, "settings", settings)


        ScriptableObject.putProperty(api, "dialog", module(cx, sc) { put ->
            put("show") { args ->
                val raw = args.getOrNull(0)
                val title: String
                val message: String
                val cancelable: Boolean
                val buttons = mutableListOf<UiNode.DialogButton>()

                if (raw is Scriptable) {
                    title = ScriptableObject.getProperty(raw, "title")?.toString()?.takeIf {
                        it != "undefined" && it != "null"
                    } ?: ""
                    val msgProp = ScriptableObject.getProperty(raw, "message")
                        ?: ScriptableObject.getProperty(raw, "text")
                        ?: ScriptableObject.getProperty(raw, "body")
                    message = msgProp?.toString()?.takeIf { it != "undefined" && it != "null" } ?: ""
                    cancelable = when (val c = ScriptableObject.getProperty(raw, "cancelable")) {
                        is Boolean -> c
                        else -> true
                    }
                    val buttonsRaw = ScriptableObject.getProperty(raw, "buttons")
                    if (buttonsRaw is Scriptable) {
                        val len = (ScriptableObject.getProperty(buttonsRaw, "length") as? Number)?.toInt() ?: 0
                        for (i in 0 until len) {
                            val item = ScriptableObject.getProperty(buttonsRaw, i)
                            if (item is Scriptable) {
                                val text = ScriptableObject.getProperty(item, "text")?.toString()
                                    ?: ScriptableObject.getProperty(item, "label")?.toString()
                                    ?: "OK"
                                val style = ScriptableObject.getProperty(item, "style")?.toString()
                                    ?: if (i == 0) "filled" else "text"
                                val onClick = ScriptableObject.getProperty(item, "onClick")
                                buttons += UiNode.DialogButton(
                                    text,
                                    style,
                                    uiBuilder.registerPersistentCallback(onClick),
                                )
                            }
                        }
                    }
                    if (buttons.isEmpty()) {
                        val confirm = ScriptableObject.getProperty(raw, "confirmText")?.toString()
                            ?: ScriptableObject.getProperty(raw, "ok")?.toString()
                            ?: "OK"
                        val dismissRaw = ScriptableObject.getProperty(raw, "dismissText")
                            ?: ScriptableObject.getProperty(raw, "cancel")
                        val dismiss = dismissRaw?.toString()?.takeIf {
                            it != "undefined" && it != "null"
                        }
                        val onConfirm = ScriptableObject.getProperty(raw, "onConfirm")
                            ?: ScriptableObject.getProperty(raw, "onOk")
                        val onDismiss = ScriptableObject.getProperty(raw, "onDismiss")
                            ?: ScriptableObject.getProperty(raw, "onCancel")
                        buttons += UiNode.DialogButton(
                            confirm,
                            "filled",
                            uiBuilder.registerPersistentCallback(onConfirm),
                        )
                        if (dismiss != null) {
                            buttons += UiNode.DialogButton(
                                dismiss,
                                "text",
                                uiBuilder.registerPersistentCallback(onDismiss),
                            )
                        }
                    }
                } else {
                    title = ""
                    message = raw?.toString() ?: ""
                    cancelable = true
                    buttons += UiNode.DialogButton("OK", "filled", null)
                }

                mainHandler.post {
                    _dialog.value = PluginDialogSpec(title, message, buttons, cancelable)
                }
                true
            }
            put("dismiss") {
                mainHandler.post { _dialog.value = null }
                null
            }
        })

        ScriptableObject.putProperty(api, "snackbar", module(cx, sc) { put ->
            put("show") { args ->
                val first = args.getOrNull(0)
                val message: String
                val actionLabel: String?
                val actionFn: Any?
                val duration: Long
                if (first is Scriptable && first !is org.mozilla.javascript.Function) {
                    val o = asMap(first)
                    message = (o["message"] ?: o["text"] ?: o["title"])?.toString() ?: ""
                    actionLabel = (o["action"] ?: o["actionLabel"] ?: o["label"])?.toString()
                    actionFn = o["onAction"] ?: o["onClick"]
                    duration = (o["duration"] as? Number)?.toLong()
                        ?: (o["durationMs"] as? Number)?.toLong()
                        ?: 3000L
                } else {
                    message = first?.toString() ?: ""
                    actionLabel = args.getOrNull(1)?.toString()
                    actionFn = args.getOrNull(2)
                    duration = (args.getOrNull(3) as? Number)?.toLong() ?: 3000L
                }
                if (message.isBlank()) return@put false
                val actionId = uiBuilder.registerPersistentCallback(actionFn)
                mainHandler.post {
                    _snackbar.value = PluginSnackbarSpec(
                        message = message,
                        actionLabel = actionLabel?.takeIf { it.isNotBlank() && it != "undefined" },
                        actionId = actionId,
                        durationMs = duration.coerceIn(1000L, 15000L),
                    )
                }
                true
            }
            put("dismiss") {
                mainHandler.post { _snackbar.value = null }
                null
            }
        })

        ScriptableObject.putProperty(api, "bottomSheet", module(cx, sc) { put ->
            put("show") { args ->
                val raw = args.getOrNull(0)
                val title: String
                val message: String
                val cancelable: Boolean
                val buttons = mutableListOf<UiNode.DialogButton>()
                if (raw is Scriptable) {
                    title = ScriptableObject.getProperty(raw, "title")?.toString()?.takeIf {
                        it != "undefined" && it != "null"
                    } ?: ""
                    val msgProp = ScriptableObject.getProperty(raw, "message")
                        ?: ScriptableObject.getProperty(raw, "text")
                        ?: ScriptableObject.getProperty(raw, "body")
                    message = msgProp?.toString()?.takeIf { it != "undefined" && it != "null" } ?: ""
                    cancelable = when (val c = ScriptableObject.getProperty(raw, "cancelable")) {
                        is Boolean -> c
                        else -> true
                    }
                    val buttonsRaw = ScriptableObject.getProperty(raw, "buttons")
                    if (buttonsRaw is Scriptable) {
                        val len = (ScriptableObject.getProperty(buttonsRaw, "length") as? Number)?.toInt() ?: 0
                        for (i in 0 until len) {
                            val item = ScriptableObject.getProperty(buttonsRaw, i)
                            if (item is Scriptable) {
                                val text = ScriptableObject.getProperty(item, "text")?.toString()
                                    ?: ScriptableObject.getProperty(item, "label")?.toString()
                                    ?: "OK"
                                val style = ScriptableObject.getProperty(item, "style")?.toString() ?: "text"
                                val onClick = ScriptableObject.getProperty(item, "onClick")
                                buttons += UiNode.DialogButton(
                                    text,
                                    style,
                                    uiBuilder.registerPersistentCallback(onClick),
                                )
                            }
                        }
                    }
                } else {
                    title = ""
                    message = raw?.toString() ?: ""
                    cancelable = true
                    buttons += UiNode.DialogButton("OK", "filled", null)
                }
                mainHandler.post {
                    _bottomSheet.value = PluginBottomSheetSpec(title, message, buttons, cancelable)
                }
                true
            }
            put("dismiss") {
                mainHandler.post { _bottomSheet.value = null }
                null
            }
        })

        ScriptableObject.putProperty(api, "wifi", module(cx, sc) { put ->
            put("isEnabled") { device.wifiIsEnabled() }
            put("startScan") { args ->
                val force = args.getOrNull(0) as? Boolean ?: false
                device.wifiStartScan(force)
            }
            put("getScanResults") { args ->
                val max = (args.getOrNull(0) as? Number)?.toInt() ?: 0
                val minRssi = (args.getOrNull(1) as? Number)?.toInt()
                device.wifiScanResultsJson(max, minRssi)
            }
            put("connectionInfo") { device.wifiConnectionInfoJson() }
            put("openSettings") { device.wifiOpenSettings(); null }
            put("disconnect") { androidApis.wifiDisconnect() }
            put("reconnect") { androidApis.wifiReconnect() }
            put("setEnabled") { args ->
                device.wifiSetEnabled(args.getOrNull(0) as? Boolean ?: true)
            }
            put("enable") { device.wifiSetEnabled(true) }
            put("disable") { device.wifiSetEnabled(false) }
            put("configuredNetworks") { device.wifiConfiguredNetworksJson() }
            put("is5GHzSupported") { device.wifiIs5GHzBandSupported() }
            put("isP2pSupported") { device.wifiIsP2pSupported() }
            put("addSuggestion") { args ->
                val ssid = args.getOrNull(0)?.toString() ?: return@put false
                val pass = args.getOrNull(1)?.toString()
                androidApis.wifiAddSuggestion(ssid, pass)
            }
            put("scan") { args ->
                val first = args.getOrNull(0)
                val opts = if (first is Scriptable && first !is org.mozilla.javascript.Function) asMap(first) else emptyMap()
                val cb = when {
                    first is org.mozilla.javascript.Function -> first
                    else -> opts["callback"] ?: opts["onResult"] ?: args.getOrNull(1)
                }
                val max = (opts["maxResults"] as? Number)?.toInt() ?: 40
                val minRssi = (opts["minRssi"] as? Number)?.toInt()
                val force = opts["force"] as? Boolean ?: false
                device.wifiStartScan(force)
                mainHandler.postDelayed({
                    val json = device.wifiScanResultsJson(max, minRssi)
                    invokeJs(cb, json, refreshUi = false)
                }, 2800)
                true
            }
            put("detailedInfo") { androidApis.wifiDetailedInfoJson() }
            put("dhcpInfo") { androidApis.wifiDhcpInfoJson() }
            put("channels") { androidApis.wifiChannelsJson() }
            put("macAddress") { androidApis.wifiMacAddress() }
            put("p2pDiscover") { args ->
                val cb = args.getOrNull(0)
                device.wifiP2pDiscover { invokeJs(cb, it) }
                null
            }
            put("p2pConnect") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.wifiP2pConnect(addr) { invokeJs(cb, it) }
                null
            }
            put("p2pDisconnect") { args ->
                val cb = args.getOrNull(0)
                device.wifiP2pDisconnect { invokeJs(cb, it) }
                null
            }
            put("p2pGroupInfo") { args ->
                val cb = args.getOrNull(0)
                device.wifiP2pGroupInfo { invokeJs(cb, it) }
                null
            }
        })

        ScriptableObject.putProperty(api, "ble", module(cx, sc) { put ->
            put("isEnabled") { device.btIsEnabled() }
            put("capabilities") { device.btCapabilitiesJson() }
            put("hasScanner") { device.btLeScannerAvailable() }
            put("hasAdvertiser") { device.btLeAdvertiserAvailable() }
            put("startScan") { args ->
                val first = args.getOrNull(0)
                val opts = if (first is Scriptable && first !is org.mozilla.javascript.Function) asMap(first) else emptyMap()
                val cb = when {
                    first is org.mozilla.javascript.Function -> first
                    else -> opts["onDevice"] ?: opts["callback"] ?: args.getOrNull(1)
                }
                val batchMs = (opts["batchMs"] as? Number)?.toLong() ?: 400L
                val maxDevices = (opts["maxDevices"] as? Number)?.toInt() ?: 80
                device.bleStartScan(manifest.id, { json ->
                    invokeJs(cb, json, refreshUi = false)
                }, batchMs, maxDevices)
            }
            put("stopScan") { device.stopBleScan(manifest.id); null }
            put("advertise") { args ->


                val first = args.getOrNull(0)
                if (first is Scriptable && first !is org.mozilla.javascript.Function) {
                    val o = asMap(first)
                    androidApis.bleAdvertiseStart(
                        (o["manufacturerId"] as? Number)?.toInt() ?: (o["id"] as? Number)?.toInt() ?: 0xFFFF,
                        o["payloadHex"]?.toString() ?: o["hex"]?.toString() ?: o["payload"]?.toString() ?: "",
                        o["connectable"] as? Boolean ?: false,
                        o["includeName"] as? Boolean ?: false,
                    )
                } else {
                    val id = (args.getOrNull(0) as? Number)?.toInt() ?: 0xFFFF
                    val hex = args.getOrNull(1)?.toString() ?: return@put false
                    androidApis.bleAdvertiseStart(id, hex)
                }
            }
            put("stopAdvertise") { androidApis.bleAdvertiseStop(); null }
            put("connect") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val cb = args.getOrNull(1)
                androidApis.gattConnect(addr) { invokeJs(cb, it, refreshUi = false) }
            }
            put("disconnect") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put null
                androidApis.gattDisconnect(addr)
                null
            }
            put("write") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val svc = args.getOrNull(1)?.toString() ?: return@put false
                val ch = args.getOrNull(2)?.toString() ?: return@put false
                val hex = args.getOrNull(3)?.toString() ?: return@put false
                androidApis.gattWrite(addr, svc, ch, hex)
            }
            put("read") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val svc = args.getOrNull(1)?.toString() ?: return@put false
                val ch = args.getOrNull(2)?.toString() ?: return@put false
                androidApis.gattRead(addr, svc, ch)
            }
            put("enableNotifications") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val svc = args.getOrNull(1)?.toString() ?: return@put false
                val ch = args.getOrNull(2)?.toString() ?: return@put false
                val enable = args.getOrNull(3) as? Boolean ?: true
                androidApis.gattEnableNotifications(addr, svc, ch, enable)
            }
            put("disableNotifications") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val svc = args.getOrNull(1)?.toString() ?: return@put false
                val ch = args.getOrNull(2)?.toString() ?: return@put false
                androidApis.gattEnableNotifications(addr, svc, ch, false)
            }
            put("requestMtu") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val mtu = (args.getOrNull(1) as? Number)?.toInt() ?: 512
                androidApis.gattRequestMtu(addr, mtu)
            }
            put("readRssi") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                androidApis.gattReadRssi(addr)
            }
            put("setPreferredPhy") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val txPhy = (args.getOrNull(1) as? Number)?.toInt() ?: 1
                val rxPhy = (args.getOrNull(2) as? Number)?.toInt() ?: 1
                androidApis.gattSetPreferredPhy(addr, txPhy, rxPhy)
            }
            put("discoverServices") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                androidApis.gattDiscoverServices(addr)
            }
            put("services") { args ->
                androidApis.gattServicesJson(args.getOrNull(0)?.toString() ?: return@put "[]")
            }
            put("writeDescriptor") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val svc = args.getOrNull(1)?.toString() ?: return@put false
                val ch = args.getOrNull(2)?.toString() ?: return@put false
                val desc = args.getOrNull(3)?.toString() ?: return@put false
                val hex = args.getOrNull(4)?.toString() ?: return@put false
                androidApis.gattWriteDescriptor(addr, svc, ch, desc, hex)
            }
            put("readDescriptor") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val svc = args.getOrNull(1)?.toString() ?: return@put false
                val ch = args.getOrNull(2)?.toString() ?: return@put false
                val desc = args.getOrNull(3)?.toString() ?: return@put false
                androidApis.gattReadDescriptor(addr, svc, ch, desc)
            }
            put("writeNoResponse") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val svc = args.getOrNull(1)?.toString() ?: return@put false
                val ch = args.getOrNull(2)?.toString() ?: return@put false
                val hex = args.getOrNull(3)?.toString() ?: return@put false
                androidApis.gattWriteNoResponse(addr, svc, ch, hex)
            }
            put("startScanFiltered") { args ->
                val first = args.getOrNull(0)
                val opts = if (first is Scriptable && first !is org.mozilla.javascript.Function) asMap(first) else emptyMap()
                val cb = when {
                    first is org.mozilla.javascript.Function -> first
                    else -> opts["onDevice"] ?: opts["callback"] ?: args.getOrNull(1)
                }
                device.bleStartScanFiltered(
                    manifest.id,
                    opts["name"]?.toString(), opts["address"]?.toString(), opts["serviceUuid"]?.toString(),
                    { json -> invokeJs(cb, json, refreshUi = false) },
                )
            }
            put("advertiseCustom") { args ->
                val first = args.getOrNull(0)
                if (first is Scriptable && first !is org.mozilla.javascript.Function) {
                    val o = asMap(first)
                    val svcUuids = (o["serviceUuids"] as? org.mozilla.javascript.NativeArray)?.let { arr ->
                        (0 until arr.length.toInt()).mapNotNull { arr.get(it, arr)?.toString() }
                    }
                    androidApis.bleAdvertiseCustom(
                        (o["manufacturerId"] as? Number)?.toInt(),
                        o["manufacturerData"]?.toString() ?: o["payload"]?.toString(),
                        svcUuids,
                        o["serviceDataUuid"]?.toString(),
                        o["serviceData"]?.toString(),
                        o["includeName"] as? Boolean ?: false,
                        o["includeTxPower"] as? Boolean ?: false,
                        o["connectable"] as? Boolean ?: false,
                    )
                } else false
            }
            put("serverStart") { args ->
                val first = args.getOrNull(0)
                val cb = args.getOrNull(1) ?: (if (first is org.mozilla.javascript.Function) first else null)
                val servicesJson = if (first is Scriptable && first !is org.mozilla.javascript.Function) {
                    val arr = first as? org.mozilla.javascript.NativeArray
                    if (arr != null) {
                        org.json.JSONArray().also { ja ->
                            for (i in 0 until arr.length.toInt()) {
                                val s = asMap(arr.get(i, arr))
                                val sObj = org.json.JSONObject().put("uuid", s["uuid"]?.toString() ?: "")
                                val chars = s["characteristics"] as? org.mozilla.javascript.NativeArray
                                if (chars != null) {
                                    val ca = org.json.JSONArray()
                                    for (j in 0 until chars.length.toInt()) {
                                        val c = asMap(chars.get(j, chars))
                                        ca.put(org.json.JSONObject()
                                            .put("uuid", c["uuid"]?.toString() ?: "")
                                            .put("properties", (c["properties"] as? Number)?.toInt() ?: 27)
                                            .put("permissions", (c["permissions"] as? Number)?.toInt() ?: 17))
                                    }
                                    sObj.put("characteristics", ca)
                                }
                                ja.put(sObj)
                            }
                        }.toString()
                    } else "[]"
                } else args.getOrNull(0)?.toString() ?: "[]"
                val finalCb = args.getOrNull(1) ?: args.getOrNull(0).takeIf { it is org.mozilla.javascript.Function }
                androidApis.bleServerStart(servicesJson) { invokeJs(finalCb, it, refreshUi = false) }
            }
            put("serverStop") { androidApis.bleServerStop(); null }
            put("serverNotify") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val svc = args.getOrNull(1)?.toString() ?: return@put false
                val ch = args.getOrNull(2)?.toString() ?: return@put false
                val hex = args.getOrNull(3)?.toString() ?: return@put false
                androidApis.bleServerNotify(addr, svc, ch, hex)
            }
            put("serverRespond") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val reqId = (args.getOrNull(1) as? Number)?.toInt() ?: return@put false
                val hex = args.getOrNull(2)?.toString()
                androidApis.bleServerSendResponse(addr, reqId, 0, 0, hex)
            }
        })


        ScriptableObject.putProperty(api, "bt", module(cx, sc) { put ->
            put("isEnabled") { device.btIsEnabled() }
            put("state") { device.btState() }
            put("capabilities") { device.btCapabilitiesJson() }
            put("name") { androidApis.btName() }
            put("address") { androidApis.btAddress() }
            put("bondedDevices") { androidApis.btBondedDevicesJson() }
            put("startDiscovery") { args ->
                val cb = args.getOrNull(0)
                androidApis.btStartDiscovery { invokeJs(cb, it, refreshUi = false) }
            }
            put("stopDiscovery") { androidApis.btStopDiscovery(); null }
            put("openSettings") { androidApis.btOpenSettings() }
            put("enable") { androidApis.btEnable() }
            put("disable") { androidApis.btDisable() }
            put("setName") { args -> androidApis.btSetName(args.getOrNull(0)?.toString() ?: return@put false) }
            put("createBond") { args -> androidApis.btCreateBond(args.getOrNull(0)?.toString() ?: return@put false) }
            put("removeBond") { args -> androidApis.btRemoveBond(args.getOrNull(0)?.toString() ?: return@put false) }
            put("getBondState") { args -> androidApis.btGetBondState(args.getOrNull(0)?.toString() ?: return@put -1) }
            put("scanMode") { androidApis.btScanMode() }
            put("deviceInfo") { args -> androidApis.btDeviceInfoJson(args.getOrNull(0)?.toString() ?: return@put "{}") }
            put("connectRfcomm") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val uuid = args.getOrNull(1)?.toString() ?: "00001101-0000-1000-8000-00805F9B34FB"
                val cb = args.getOrNull(2) ?: args.getOrNull(1).takeIf { it is org.mozilla.javascript.Function }
                androidApis.btConnectRfcomm(addr, if (args.getOrNull(1) is org.mozilla.javascript.Function) "00001101-0000-1000-8000-00805F9B34FB" else uuid) { invokeJs(cb, it, refreshUi = false) }
            }
            put("sendRfcomm") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val hex = args.getOrNull(1)?.toString() ?: return@put false
                androidApis.btSendRfcomm(addr, hex)
            }
            put("sendRfcommText") { args ->
                val addr = args.getOrNull(0)?.toString() ?: return@put false
                val text = args.getOrNull(1)?.toString() ?: return@put false
                androidApis.btSendRfcommText(addr, text)
            }
            put("disconnectRfcomm") { args ->
                androidApis.btDisconnectRfcomm(args.getOrNull(0)?.toString() ?: return@put null)
                null
            }
            put("rfcommConnected") { androidApis.btRfcommConnectedJson() }
        })

        ScriptableObject.putProperty(api, "nfc", module(cx, sc) { put ->
            put("isAvailable") { device.nfcIsAvailable() }
            put("isEnabled") { device.nfcIsEnabled() }
            put("capabilities") { device.nfcCapabilitiesJson() }
            put("openSettings") { device.nfcOpenSettings(); null }
            put("openTools") {
                mainHandler.post { navigateToScreen?.invoke("__app__:other/nfc_tools") }
                null
            }
            put("rootEnable") { args ->
                val cb = args.getOrNull(0)
                device.nfcRootSetEnabled(true) { invokeJs(cb, it) }
                null
            }
            put("rootDisable") { args ->
                val cb = args.getOrNull(0)
                device.nfcRootSetEnabled(false) { invokeJs(cb, it) }
                null
            }
            put("lastTag") { device.nfcLastTagJson() }
            put("onTag") { args ->
                val cb = args.getOrNull(0)
                if (cb is org.mozilla.javascript.Function) {
                    device.nfcSetTagCallback { json -> invokeJs(cb, json, refreshUi = true) }
                } else {
                    device.nfcSetTagCallback(null)
                }
                null
            }
            put("readNdef") { args ->
                val cb = args.getOrNull(0)
                device.nfcReadNdef { invokeJs(cb, it) }
                null
            }
            put("writeNdef") { args ->
                val text = args.getOrNull(0)?.toString() ?: return@put null
                val isUri = args.getOrNull(1) as? Boolean ?: text.startsWith("http")
                val cb = args.getOrNull(2) ?: args.getOrNull(1).takeIf { it is org.mozilla.javascript.Function }
                device.nfcWriteNdef(text, if (args.getOrNull(1) is org.mozilla.javascript.Function) text.startsWith("http") else isUri) { invokeJs(cb, it) }
                null
            }
            put("writeNdefRaw") { args ->
                val records = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.nfcWriteNdefRaw(records) { invokeJs(cb, it) }
                null
            }
            put("transceive") { args ->
                val tech = args.getOrNull(0)?.toString() ?: "nfca"
                val hex = args.getOrNull(1)?.toString() ?: return@put null
                val cb = args.getOrNull(2)
                device.nfcTransceive(tech, hex) { invokeJs(cb, it) }
                null
            }
            put("mifareRead") { args ->
                val sector = (args.getOrNull(0) as? Number)?.toInt() ?: 0
                val keyHex = args.getOrNull(1)?.toString()?.takeIf { it != "undefined" && it != "null" }
                val keyType = args.getOrNull(2)?.toString() ?: "A"
                val cb = args.getOrNull(3) ?: args.getOrNull(2).takeIf { it is org.mozilla.javascript.Function }
                    ?: args.getOrNull(1).takeIf { it is org.mozilla.javascript.Function }
                device.nfcMifareClassicRead(
                    sector,
                    if (args.getOrNull(1) is org.mozilla.javascript.Function) null else keyHex,
                    if (args.getOrNull(2) is org.mozilla.javascript.Function) "A" else keyType,
                ) { invokeJs(cb, it) }
                null
            }
            put("mifareWrite") { args ->
                val sector = (args.getOrNull(0) as? Number)?.toInt() ?: return@put null
                val block = (args.getOrNull(1) as? Number)?.toInt() ?: return@put null
                val dataHex = args.getOrNull(2)?.toString() ?: return@put null
                val keyHex = args.getOrNull(3)?.toString()?.takeIf { it != "undefined" && it != "null" }
                val keyType = args.getOrNull(4)?.toString() ?: "A"
                val cb = args.getOrNull(5) ?: args.getOrNull(4).takeIf { it is org.mozilla.javascript.Function }
                    ?: args.getOrNull(3).takeIf { it is org.mozilla.javascript.Function }
                device.nfcMifareClassicWrite(sector, block, dataHex,
                    if (args.getOrNull(3) is org.mozilla.javascript.Function) null else keyHex,
                    if (args.getOrNull(4) is org.mozilla.javascript.Function) "A" else keyType,
                ) { invokeJs(cb, it) }
                null
            }
            put("ultralightRead") { args ->
                val page = (args.getOrNull(0) as? Number)?.toInt() ?: 0
                val cb = args.getOrNull(1)
                device.nfcMifareUltralightRead(page) { invokeJs(cb, it) }
                null
            }
            put("ultralightWrite") { args ->
                val page = (args.getOrNull(0) as? Number)?.toInt() ?: return@put null
                val dataHex = args.getOrNull(1)?.toString() ?: return@put null
                val cb = args.getOrNull(2)
                device.nfcMifareUltralightWrite(page, dataHex) { invokeJs(cb, it) }
                null
            }
        })

        ScriptableObject.putProperty(api, "ir", module(cx, sc) { put ->
            put("status") { device.irStatusJson() }
            put("hasEmitter") { androidApis.irHasEmitter() }
            put("hasIrEmitter") { androidApis.irHasEmitter() }
            put("carrierFrequencies") { androidApis.irCarrierFrequenciesJson() }
            put("getCarrierFrequencies") { androidApis.irCarrierFrequenciesJson() }
            val irTransmitBody: (Array<Any?>) -> Any? = { args ->
                val first = args.getOrNull(0)
                if (first is Scriptable && first !is org.mozilla.javascript.NativeArray &&
                    first !is org.mozilla.javascript.Function
                ) {
                    val o = asMap(first)
                    val freq = (o["freq"] as? Number)?.toInt()
                        ?: (o["frequency"] as? Number)?.toInt()
                        ?: 38000
                    val pattern = toIntArray(o["pattern"] ?: o["pulses"])
                    androidApis.irTransmit(freq, pattern)
                } else {
                    val freq = (args.getOrNull(0) as? Number)?.toInt() ?: 38000
                    val pattern = toIntArray(args.getOrNull(1))
                    androidApis.irTransmit(freq, pattern)
                }
            }
            put("transmit", irTransmitBody)
            put("send", irTransmitBody)
            put("toggleStorm") { device.irToggleStorm() }
            put("toggleJammer") { device.irToggleJammer() }
            put("openRemotes") {
                mainHandler.post { navigateToScreen?.invoke("__app__:other/ir_flipper_home") }
                null
            }
            put("listButtons") { args ->
                val path = args.getOrNull(0)?.toString() ?: return@put "[]"
                try {
                    val buttons = loadIrButtons(path)
                    org.json.JSONArray().also { arr ->
                        buttons.forEach { b ->
                            arr.put(
                                org.json.JSONObject()
                                    .put("name", b.name)
                                    .put("frequency", b.frequency)
                                    .put("protocol", b.protocol)
                                    .put("len", b.pattern.size),
                            )
                        }
                    }.toString()
                } catch (e: Exception) {
                    "[]"
                }
            }
            put("playFile") { args ->
                val path = args.getOrNull(0)?.toString() ?: return@put false
                val name = args.getOrNull(1)?.toString()
                try {
                    val buttons = loadIrButtons(path)
                    val targets = if (name.isNullOrBlank() || name == "undefined") {
                        buttons
                    } else {
                        buttons.filter { it.name.equals(name, ignoreCase = true) }
                    }
                    if (targets.isEmpty()) return@put false
                    targets.forEach { transmitIr(appContext, it) }
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "ir.playFile", e)
                    false
                }
            }
            put("playButton") { args ->
                val path = args.getOrNull(0)?.toString() ?: return@put false
                val name = args.getOrNull(1)?.toString() ?: return@put false
                try {
                    val btn = loadIrButtons(path).firstOrNull { it.name.equals(name, ignoreCase = true) }
                        ?: return@put false
                    transmitIr(appContext, btn)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        })

        ScriptableObject.putProperty(api, "net", module(cx, sc) { put ->
            put("active") { device.networkActiveJson() }
            put("detail") { androidApis.connectivityDetailJson() }
            put("interfaces") { androidApis.netInterfacesJson() }
            put("http") { args ->
                val method = args.getOrNull(0)?.toString() ?: "GET"
                val url = args.getOrNull(1)?.toString() ?: return@put null
                val body = args.getOrNull(2)?.toString()
                val headers = args.getOrNull(3)?.toString()
                val cb = args.getOrNull(4)
                device.httpRequest(method, url, body, headers) { invokeJs(cb, it) }
                null
            }
            put("download") { args ->
                val first = args.getOrNull(0)
                val url: String
                val destRel: String
                val headers: String?
                val cb: Any?
                if (first is Scriptable && first !is org.mozilla.javascript.Function) {
                    val o = asMap(first)
                    url = (o["url"] ?: o["src"])?.toString() ?: return@put false
                    destRel = (o["path"] ?: o["dest"] ?: o["file"])?.toString() ?: "downloads/file.bin"
                    headers = o["headers"]?.toString()
                    cb = o["callback"] ?: o["onDone"] ?: args.getOrNull(1)
                } else {
                    url = first?.toString() ?: return@put false
                    destRel = args.getOrNull(1)?.toString() ?: "downloads/file.bin"
                    headers = null
                    cb = args.getOrNull(2)
                }
                val dest = java.io.File(androidApis.pluginFilesDir(), destRel.trim().removePrefix("/").replace("..", ""))
                if (!dest.canonicalPath.startsWith(androidApis.pluginFilesDir().canonicalPath)) return@put false
                device.httpDownload(url, dest, headers) { json ->
                    try {
                        val o = org.json.JSONObject(json)
                        if (o.optBoolean("ok")) {
                            o.put("path", destRel.trim().removePrefix("/"))
                        }
                        invokeJs(cb, o.toString())
                    } catch (_: Exception) {
                        invokeJs(cb, json)
                    }
                }
                true
            }
            put("tcpReachable") { args ->
                val host = args.getOrNull(0)?.toString() ?: return@put null
                val port = (args.getOrNull(1) as? Number)?.toInt() ?: 80
                val timeout = (args.getOrNull(2) as? Number)?.toInt() ?: 1500
                val cb = args.getOrNull(3)
                device.tcpReachable(host, port, timeout) { invokeJs(cb, it) }
                null
            }
            put("ping") { args ->
                val host = args.getOrNull(0)?.toString() ?: return@put null
                val timeout = (args.getOrNull(1) as? Number)?.toInt() ?: 2000
                val cb = args.getOrNull(2)
                device.pingHost(host, timeout) { invokeJs(cb, it) }
                null
            }
            put("portScan") { args ->
                val host = args.getOrNull(0)?.toString() ?: return@put null
                val parsed = toIntArray(args.getOrNull(1))
                val ports = if (parsed.isEmpty()) {
                    intArrayOf(22, 80, 443, 8080, 554, 8000, 8443)
                } else parsed
                val timeout = (args.getOrNull(2) as? Number)?.toInt() ?: 400
                val cb = args.getOrNull(3)
                device.portScan(host, ports, timeout) { invokeJs(cb, it) }
                null
            }
            put("nsdDiscover") { args ->
                val type = args.getOrNull(0)?.toString() ?: "_http._tcp"
                val cb = args.getOrNull(1)
                androidApis.nsdDiscover(type) { invokeJs(cb, it, refreshUi = false) }
            }
            put("nsdStop") { androidApis.nsdStop(); null }
        })

        ScriptableObject.putProperty(api, "root", module(cx, sc) { put ->
            put("available") { device.rootAvailable() }
            put("isRooted") { device.rootAvailable() }
            put("exec") { args ->
                val cmd = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootExec(cmd) { invokeJs(cb, it) }
                null
            }
            put("script") { args ->
                val script = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootExecScript(script) { invokeJs(cb, it) }
                null
            }
            put("id") { args ->
                val cb = args.getOrNull(0)
                device.rootId { invokeJs(cb, it) }
                null
            }
            put("which") { args ->
                val bin = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootWhich(bin) { invokeJs(cb, it) }
                null
            }
            put("exists") { args ->
                val path = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootExists(path) { invokeJs(cb, it) }
                null
            }
            put("list") { args ->
                val path = args.getOrNull(0)?.toString() ?: "/"
                val cb = args.getOrNull(1)
                device.rootList(path) { invokeJs(cb, it) }
                null
            }
            put("ls") { args ->
                val path = args.getOrNull(0)?.toString() ?: "/"
                val cb = args.getOrNull(1)
                device.rootList(path) { invokeJs(cb, it) }
                null
            }
            put("stat") { args ->
                val path = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootStat(path) { invokeJs(cb, it) }
                null
            }
            put("read") { args ->
                val path = args.getOrNull(0)?.toString() ?: return@put null
                val max = (args.getOrNull(1) as? Number)?.toInt() ?: 2_000_000
                val cb = args.getOrNull(2) ?: args.getOrNull(1).takeIf { it !is Number }
                device.rootReadText(path, if (args.getOrNull(1) is Number) max else 2_000_000) { invokeJs(cb, it) }
                null
            }
            put("readBase64") { args ->
                val path = args.getOrNull(0)?.toString() ?: return@put null
                val max = (args.getOrNull(1) as? Number)?.toInt() ?: 2_000_000
                val cb = args.getOrNull(2) ?: args.getOrNull(1).takeIf { it !is Number }
                device.rootReadBase64(path, if (args.getOrNull(1) is Number) max else 2_000_000) { invokeJs(cb, it) }
                null
            }
            put("write") { args ->
                val path = args.getOrNull(0)?.toString() ?: return@put null
                val content = args.getOrNull(1)?.toString() ?: ""
                val cb = args.getOrNull(2)
                device.rootWriteText(path, content) { invokeJs(cb, it) }
                null
            }
            put("writeBase64") { args ->
                val path = args.getOrNull(0)?.toString() ?: return@put null
                val b64 = args.getOrNull(1)?.toString() ?: return@put null
                val cb = args.getOrNull(2)
                device.rootWriteBase64(path, b64) { invokeJs(cb, it) }
                null
            }
            put("mkdir") { args ->
                val path = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootMkdir(path) { invokeJs(cb, it) }
                null
            }
            put("delete") { args ->
                val path = args.getOrNull(0)?.toString() ?: return@put null
                val recursive = args.getOrNull(1) as? Boolean ?: true
                val cb = args.getOrNull(2) ?: args.getOrNull(1).takeIf { it !is Boolean }
                device.rootDelete(path, if (args.getOrNull(1) is Boolean) recursive else true) { invokeJs(cb, it) }
                null
            }
            put("rm") { args ->
                val path = args.getOrNull(0)?.toString() ?: return@put null
                val recursive = args.getOrNull(1) as? Boolean ?: true
                val cb = args.getOrNull(2) ?: args.getOrNull(1).takeIf { it !is Boolean }
                device.rootDelete(path, if (args.getOrNull(1) is Boolean) recursive else true) { invokeJs(cb, it) }
                null
            }
            put("copy") { args ->
                val src = args.getOrNull(0)?.toString() ?: return@put null
                val dst = args.getOrNull(1)?.toString() ?: return@put null
                val cb = args.getOrNull(2)
                device.rootCopy(src, dst) { invokeJs(cb, it) }
                null
            }
            put("move") { args ->
                val src = args.getOrNull(0)?.toString() ?: return@put null
                val dst = args.getOrNull(1)?.toString() ?: return@put null
                val cb = args.getOrNull(2)
                device.rootMove(src, dst) { invokeJs(cb, it) }
                null
            }
            put("chmod") { args ->
                val mode = args.getOrNull(0)?.toString() ?: return@put null
                val path = args.getOrNull(1)?.toString() ?: return@put null
                val cb = args.getOrNull(2)
                device.rootChmod(mode, path) { invokeJs(cb, it) }
                null
            }
            put("chown") { args ->
                val owner = args.getOrNull(0)?.toString() ?: return@put null
                val path = args.getOrNull(1)?.toString() ?: return@put null
                val cb = args.getOrNull(2)
                device.rootChown(owner, path) { invokeJs(cb, it) }
                null
            }
            put("getprop") { args ->
                val key = args.getOrNull(0)?.toString()
                val cb = args.getOrNull(1) ?: args.getOrNull(0).takeIf { it is org.mozilla.javascript.Function }
                device.rootGetprop(if (args.getOrNull(0) is org.mozilla.javascript.Function) null else key) {
                    invokeJs(cb, it)
                }
                null
            }
            put("setprop") { args ->
                val key = args.getOrNull(0)?.toString() ?: return@put null
                val value = args.getOrNull(1)?.toString() ?: ""
                val cb = args.getOrNull(2)
                device.rootSetprop(key, value) { invokeJs(cb, it) }
                null
            }
            put("packages") { args ->
                val filter = args.getOrNull(0)?.toString()
                val cb = args.getOrNull(1) ?: args.getOrNull(0).takeIf { it is org.mozilla.javascript.Function }
                device.rootPackages(
                    if (args.getOrNull(0) is org.mozilla.javascript.Function) null else filter,
                ) { invokeJs(cb, it) }
                null
            }
            put("pmPath") { args ->
                val pkg = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootPmPath(pkg) { invokeJs(cb, it) }
                null
            }
            put("install") { args ->
                val apk = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootPmInstall(apk) { invokeJs(cb, it) }
                null
            }
            put("uninstall") { args ->
                val pkg = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootPmUninstall(pkg) { invokeJs(cb, it) }
                null
            }
            put("disable") { args ->
                val pkg = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootPmDisable(pkg) { invokeJs(cb, it) }
                null
            }
            put("enable") { args ->
                val pkg = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootPmEnable(pkg) { invokeJs(cb, it) }
                null
            }
            put("clear") { args ->
                val pkg = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootPmClear(pkg) { invokeJs(cb, it) }
                null
            }
            put("grant") { args ->
                val pkg = args.getOrNull(0)?.toString() ?: return@put null
                val perm = args.getOrNull(1)?.toString() ?: return@put null
                val cb = args.getOrNull(2)
                device.rootPmGrant(pkg, perm) { invokeJs(cb, it) }
                null
            }
            put("revoke") { args ->
                val pkg = args.getOrNull(0)?.toString() ?: return@put null
                val perm = args.getOrNull(1)?.toString() ?: return@put null
                val cb = args.getOrNull(2)
                device.rootPmRevoke(pkg, perm) { invokeJs(cb, it) }
                null
            }
            put("forceStop") { args ->
                val pkg = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootForceStop(pkg) { invokeJs(cb, it) }
                null
            }
            put("start") { args ->
                val component = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootAmStart(component) { invokeJs(cb, it) }
                null
            }
            put("broadcast") { args ->
                val action = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootAmBroadcast(action) { invokeJs(cb, it) }
                null
            }
            put("settingsGet") { args ->
                val ns = args.getOrNull(0)?.toString() ?: "system"
                val key = args.getOrNull(1)?.toString() ?: return@put null
                val cb = args.getOrNull(2)
                device.rootSettingsGet(ns, key) { invokeJs(cb, it) }
                null
            }
            put("settingsPut") { args ->
                val ns = args.getOrNull(0)?.toString() ?: "system"
                val key = args.getOrNull(1)?.toString() ?: return@put null
                val value = args.getOrNull(2)?.toString() ?: ""
                val cb = args.getOrNull(3)
                device.rootSettingsPut(ns, key, value) { invokeJs(cb, it) }
                null
            }
            put("remount") { args ->
                val rw = when (val v = args.getOrNull(0)) {
                    is Boolean -> v
                    is String -> v.equals("rw", true) || v == "true"
                    else -> true
                }
                val cb = args.getOrNull(1) ?: args.getOrNull(0).takeIf { it is org.mozilla.javascript.Function }
                device.rootRemount(rw) { invokeJs(cb, it) }
                null
            }
            put("mount") { args ->
                val cb = args.getOrNull(0)
                device.rootMount { invokeJs(cb, it) }
                null
            }
            put("df") { args ->
                val cb = args.getOrNull(0)
                device.rootDf { invokeJs(cb, it) }
                null
            }
            put("ps") { args ->
                val filter = args.getOrNull(0)?.toString()
                val cb = args.getOrNull(1) ?: args.getOrNull(0).takeIf { it is org.mozilla.javascript.Function }
                device.rootPs(if (args.getOrNull(0) is org.mozilla.javascript.Function) null else filter) {
                    invokeJs(cb, it)
                }
                null
            }
            put("kill") { args ->
                val target = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootKill(target) { invokeJs(cb, it) }
                null
            }
            put("dmesg") { args ->
                val lines = (args.getOrNull(0) as? Number)?.toInt() ?: 100
                val cb = args.getOrNull(1) ?: args.getOrNull(0).takeIf { it is org.mozilla.javascript.Function }
                device.rootDmesg(if (args.getOrNull(0) is Number) lines else 100) { invokeJs(cb, it) }
                null
            }
            put("logcat") { args ->
                val lines = (args.getOrNull(0) as? Number)?.toInt() ?: 100
                val cb = args.getOrNull(1) ?: args.getOrNull(0).takeIf { it is org.mozilla.javascript.Function }
                device.rootLogcat(if (args.getOrNull(0) is Number) lines else 100) { invokeJs(cb, it) }
                null
            }
            put("services") { args ->
                val cb = args.getOrNull(0)
                device.rootServiceList { invokeJs(cb, it) }
                null
            }
            put("iptables") { args ->
                val a = args.getOrNull(0)?.toString() ?: "-L -n"
                val cb = args.getOrNull(1)
                device.rootIptables(a) { invokeJs(cb, it) }
                null
            }
            put("ip") { args ->
                val a = args.getOrNull(0)?.toString() ?: "addr"
                val cb = args.getOrNull(1)
                device.rootIp(a) { invokeJs(cb, it) }
                null
            }
            put("ifconfig") { args ->
                val cb = args.getOrNull(0)
                device.rootIfconfig { invokeJs(cb, it) }
                null
            }
            put("sysctl") { args ->
                val key = args.getOrNull(0)?.toString()
                val value = args.getOrNull(1)?.toString()
                val cb = args.getOrNull(2) ?: args.getOrNull(1).takeIf { it is org.mozilla.javascript.Function }
                    ?: args.getOrNull(0).takeIf { it is org.mozilla.javascript.Function }
                device.rootSysctl(
                    if (args.getOrNull(0) is org.mozilla.javascript.Function) null else key,
                    if (args.getOrNull(1) is org.mozilla.javascript.Function) null else value,
                ) { invokeJs(cb, it) }
                null
            }
            put("reboot") { args ->
                val mode = args.getOrNull(0)?.toString()
                val cb = args.getOrNull(1) ?: args.getOrNull(0).takeIf { it is org.mozilla.javascript.Function }
                device.rootReboot(
                    if (args.getOrNull(0) is org.mozilla.javascript.Function) null else mode,
                ) { invokeJs(cb, it) }
                null
            }
            put("pull") { args ->
                val remote = args.getOrNull(0)?.toString() ?: return@put null
                val localRel = args.getOrNull(1)?.toString() ?: "root_pull/${java.io.File(remote).name}"
                val cb = args.getOrNull(2)
                val local = java.io.File(
                    androidApis.pluginFilesDir(),
                    localRel.trim().removePrefix("/").replace("..", ""),
                )
                if (!local.canonicalPath.startsWith(androidApis.pluginFilesDir().canonicalPath)) return@put false
                device.rootPullToFile(remote, local) { json ->
                    try {
                        val o = org.json.JSONObject(json)
                        if (o.optBoolean("ok")) o.put("path", localRel.trim().removePrefix("/"))
                        invokeJs(cb, o.toString())
                    } catch (_: Exception) {
                        invokeJs(cb, json)
                    }
                }
                null
            }
            put("push") { args ->
                val localRel = args.getOrNull(0)?.toString() ?: return@put null
                val remote = args.getOrNull(1)?.toString() ?: return@put null
                val cb = args.getOrNull(2)
                val local = java.io.File(
                    androidApis.pluginFilesDir(),
                    localRel.trim().removePrefix("/").replace("..", ""),
                )
                if (!local.canonicalPath.startsWith(androidApis.pluginFilesDir().canonicalPath)) return@put false
                device.rootPushFromFile(local, remote) { invokeJs(cb, it) }
                null
            }
            put("quote") { args ->
                device.shellQuote(args.getOrNull(0)?.toString() ?: "")
            }
            put("selinux") { args ->
                val cb = args.getOrNull(0)
                device.rootSelinux { invokeJs(cb, it) }
                null
            }
            put("selinuxSet") { args ->
                val mode = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootSelinuxSet(mode) { invokeJs(cb, it) }
                null
            }
            put("magiskModules") { args ->
                val cb = args.getOrNull(0)
                device.rootMagiskModules { invokeJs(cb, it) }
                null
            }
            put("magiskToggleModule") { args ->
                val name = args.getOrNull(0)?.toString() ?: return@put null
                val enable = args.getOrNull(1) as? Boolean ?: true
                val cb = args.getOrNull(2)
                device.rootMagiskToggleModule(name, enable) { invokeJs(cb, it) }
                null
            }
            put("magiskInstallModule") { args ->
                val zip = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootMagiskInstallModule(zip) { invokeJs(cb, it) }
                null
            }
            put("buildPropGet") { args ->
                val key = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootBuildPropGet(key) { invokeJs(cb, it) }
                null
            }
            put("buildPropSet") { args ->
                val key = args.getOrNull(0)?.toString() ?: return@put null
                val value = args.getOrNull(1)?.toString() ?: ""
                val cb = args.getOrNull(2)
                device.rootBuildPropSet(key, value) { invokeJs(cb, it) }
                null
            }
            put("macSpoof") { args ->
                val iface = args.getOrNull(0)?.toString() ?: return@put null
                val mac = args.getOrNull(1)?.toString() ?: return@put null
                val cb = args.getOrNull(2)
                device.rootMacSpoof(iface, mac) { invokeJs(cb, it) }
                null
            }
            put("dumpsys") { args ->
                val service = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootDumpsys(service) { invokeJs(cb, it) }
                null
            }
            put("inputTap") { args ->
                val x = (args.getOrNull(0) as? Number)?.toInt() ?: return@put null
                val y = (args.getOrNull(1) as? Number)?.toInt() ?: return@put null
                val cb = args.getOrNull(2)
                device.rootInputTap(x, y) { invokeJs(cb, it) }
                null
            }
            put("inputSwipe") { args ->
                val x1 = (args.getOrNull(0) as? Number)?.toInt() ?: return@put null
                val y1 = (args.getOrNull(1) as? Number)?.toInt() ?: return@put null
                val x2 = (args.getOrNull(2) as? Number)?.toInt() ?: return@put null
                val y2 = (args.getOrNull(3) as? Number)?.toInt() ?: return@put null
                val dur = (args.getOrNull(4) as? Number)?.toInt() ?: 300
                val cb = args.getOrNull(5)
                device.rootInputSwipe(x1, y1, x2, y2, dur) { invokeJs(cb, it) }
                null
            }
            put("inputText") { args ->
                val text = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootInputText(text) { invokeJs(cb, it) }
                null
            }
            put("inputKeyevent") { args ->
                val keycode = (args.getOrNull(0) as? Number)?.toInt() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootInputKeyevent(keycode) { invokeJs(cb, it) }
                null
            }
            put("screencap") { args ->
                val dest = args.getOrNull(0)?.toString() ?: "/sdcard/screencap.png"
                val cb = args.getOrNull(1) ?: args.getOrNull(0).takeIf { it is org.mozilla.javascript.Function }
                device.rootScreencap(if (args.getOrNull(0) is org.mozilla.javascript.Function) "/sdcard/screencap.png" else dest) { invokeJs(cb, it) }
                null
            }
            put("screenrecord") { args ->
                val dest = args.getOrNull(0)?.toString() ?: "/sdcard/screenrecord.mp4"
                val dur = (args.getOrNull(1) as? Number)?.toInt() ?: 30
                val cb = args.getOrNull(2)
                device.rootScreenrecord(dest, dur) { invokeJs(cb, it) }
                null
            }
            put("wmSize") { args -> val cb = args.getOrNull(0); device.rootWmSize { invokeJs(cb, it) }; null }
            put("wmDensity") { args -> val cb = args.getOrNull(0); device.rootWmDensity { invokeJs(cb, it) }; null }
            put("wmSizeSet") { args ->
                val w = (args.getOrNull(0) as? Number)?.toInt() ?: return@put null
                val h = (args.getOrNull(1) as? Number)?.toInt() ?: return@put null
                val cb = args.getOrNull(2)
                device.rootWmSizeSet(w, h) { invokeJs(cb, it) }; null
            }
            put("wmDensitySet") { args ->
                val d = (args.getOrNull(0) as? Number)?.toInt() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootWmDensitySet(d) { invokeJs(cb, it) }; null
            }
            put("wmSizeReset") { args -> val cb = args.getOrNull(0); device.rootWmSizeReset { invokeJs(cb, it) }; null }
            put("wmDensityReset") { args -> val cb = args.getOrNull(0); device.rootWmDensityReset { invokeJs(cb, it) }; null }
            put("initDList") { args -> val cb = args.getOrNull(0); device.rootInitDList { invokeJs(cb, it) }; null }
            put("initDRun") { args ->
                val script = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.rootInitDRun(script) { invokeJs(cb, it) }; null
            }
            put("wifiEnable") { args ->
                val cb = args.getOrNull(0)
                device.rootWifiSetEnabled(true) { invokeJs(cb, it) }; null
            }
            put("wifiDisable") { args ->
                val cb = args.getOrNull(0)
                device.rootWifiSetEnabled(false) { invokeJs(cb, it) }; null
            }
            put("btEnable") { args ->
                val cb = args.getOrNull(0)
                device.rootBtSetEnabled(true) { invokeJs(cb, it) }; null
            }
            put("btDisable") { args ->
                val cb = args.getOrNull(0)
                device.rootBtSetEnabled(false) { invokeJs(cb, it) }; null
            }
            put("nfcEnable") { args ->
                val cb = args.getOrNull(0)
                device.nfcRootSetEnabled(true) { invokeJs(cb, it) }; null
            }
            put("nfcDisable") { args ->
                val cb = args.getOrNull(0)
                device.nfcRootSetEnabled(false) { invokeJs(cb, it) }; null
            }
            put("wifiStatus") { args ->
                val cb = args.getOrNull(0)
                device.rootWifiStatus { invokeJs(cb, it) }; null
            }
            put("btStatus") { args ->
                val cb = args.getOrNull(0)
                device.rootBtStatus { invokeJs(cb, it) }; null
            }
            put("nfcStatus") { args ->
                val cb = args.getOrNull(0)
                device.rootNfcStatus { invokeJs(cb, it) }; null
            }
            put("iwScan") { args ->
                val iface = args.getOrNull(0)?.toString() ?: "wlan0"
                val cb = args.getOrNull(1) ?: args.getOrNull(0).takeIf { it is org.mozilla.javascript.Function }
                device.rootIwlistScan(
                    if (args.getOrNull(0) is org.mozilla.javascript.Function) "wlan0" else iface,
                ) { invokeJs(cb, it) }
                null
            }
            put("hciconfig") { args ->
                val cb = args.getOrNull(0)
                device.rootHciconfig { invokeJs(cb, it) }; null
            }
        })

        ScriptableObject.putProperty(api, "shizuku", module(cx, sc) { put ->
            put("available") { device.shizukuAvailable() }
            put("hasPermission") { device.shizukuHasPermission() }
            put("requestPermission") { device.shizukuRequestPermission(); null }
            put("exec") { args ->
                val cmd = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.shizukuExec(cmd) { invokeJs(cb, it) }
                null
            }
        })

        ScriptableObject.putProperty(api, "shell", module(cx, sc) { put ->
            put("exec") { args ->
                val cmd = args.getOrNull(0)?.toString() ?: return@put null
                val cb = args.getOrNull(1)
                device.shellExecSmart(cmd) { invokeJs(cb, it) }
                null
            }
            put("via") {
                when {
                    device.shizukuHasPermission() -> "shizuku"
                    device.rootAvailable() -> "root"
                    else -> "none"
                }
            }
            put("quote") { args -> device.shellQuote(args.getOrNull(0)?.toString() ?: "") }
        })

        ScriptableObject.putProperty(api, "clipboard", module(cx, sc) { put ->
            put("get") { androidApis.clipboardGet() }
            put("set") { args ->
                androidApis.clipboardSet(args.getOrNull(0)?.toString() ?: "")
            }
        })

        ScriptableObject.putProperty(api, "vibrator", module(cx, sc) { put ->
            put("vibrate") { args ->
                val ms = (args.getOrNull(0) as? Number)?.toLong() ?: 50L
                androidApis.vibrate(ms)
            }
            put("pattern") { args ->
                val arr = toLongArray(args.getOrNull(0))
                val repeat = (args.getOrNull(1) as? Number)?.toInt() ?: -1
                androidApis.vibratePattern(arr, repeat)
            }
            put("cancel") { androidApis.vibrateCancel(); null }
        })

        ScriptableObject.putProperty(api, "notify", module(cx, sc) { put ->
            put("show") { args ->

                val first = args.getOrNull(0)
                if (first is Scriptable && first !is org.mozilla.javascript.Function) {
                    val o = asMap(first)
                    androidApis.notifyShow(
                        (o["id"] as? Number)?.toInt() ?: 1,
                        o["title"]?.toString() ?: "",
                        o["text"]?.toString() ?: o["message"]?.toString() ?: "",
                        o["channel"]?.toString() ?: "dolphy_plugins",
                    )
                } else {
                    val id = (args.getOrNull(0) as? Number)?.toInt() ?: 1
                    val title = args.getOrNull(1)?.toString() ?: ""
                    val text = args.getOrNull(2)?.toString() ?: ""
                    androidApis.notifyShow(id, title, text)
                }
            }
            put("cancel") { args ->
                androidApis.notifyCancel((args.getOrNull(0) as? Number)?.toInt() ?: 1)
                null
            }
        })

        ScriptableObject.putProperty(api, "intent", module(cx, sc) { put ->
            put("openUrl") { args -> androidApis.openUrl(args.getOrNull(0)?.toString() ?: "") }
            put("shareText") { args ->
                androidApis.shareText(
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString() ?: "Share",
                )
            }
            put("openSettings") { args ->
                androidApis.openSettings(args.getOrNull(0)?.toString())
            }
            put("start") { args ->
                val action = args.getOrNull(0)?.toString() ?: return@put false
                val data = args.getOrNull(1)?.toString()
                val extras = args.getOrNull(2)?.toString()
                androidApis.startActivity(action, data, extras)
            }
            put("dial") { args -> androidApis.dial(args.getOrNull(0)?.toString() ?: "") }
        })

        ScriptableObject.putProperty(api, "files", module(cx, sc) { put ->
            put("list") { args -> androidApis.filesList(args.getOrNull(0)?.toString() ?: "") }
            put("read") { args -> androidApis.filesRead(args.getOrNull(0)?.toString() ?: "") }
            put("write") { args ->
                androidApis.filesWrite(
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString() ?: "",
                    args.getOrNull(2) as? Boolean ?: false,
                )
            }
            put("append") { args ->
                androidApis.filesAppend(
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString() ?: "",
                )
            }
            put("delete") { args -> androidApis.filesDelete(args.getOrNull(0)?.toString() ?: "") }
            put("exists") { args -> androidApis.filesExists(args.getOrNull(0)?.toString() ?: "") }
            put("mkdir") { args -> androidApis.filesMkdir(args.getOrNull(0)?.toString() ?: "") }
            put("stat") { args -> androidApis.filesStat(args.getOrNull(0)?.toString() ?: "") }
            put("copy") { args ->
                androidApis.filesCopy(
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString() ?: "",
                )
            }
            put("move") { args ->
                androidApis.filesMove(
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString() ?: "",
                )
            }
            put("absolute") { args -> androidApis.filesAbsolute(args.getOrNull(0)?.toString() ?: "") }
            put("writeBase64") { args ->
                androidApis.filesWriteBase64(
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString() ?: "",
                )
            }
            put("readBase64") { args -> androidApis.filesReadBase64(args.getOrNull(0)?.toString() ?: "") }
            put("share") { args ->
                androidApis.filesShare(
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString(),
                    args.getOrNull(2)?.toString() ?: "Share",
                )
            }
            put("openWith") { args ->
                androidApis.filesOpenWith(
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString(),
                )
            }
            put("export") { args -> requestMediaPick(PluginMediaAction.CREATE_DOCUMENT, args) }
            put("saveAs") { args -> requestMediaPick(PluginMediaAction.CREATE_DOCUMENT, args) }
            put("pick") { args -> requestMediaPick(PluginMediaAction.PICK_FILE, args) }
            put("pickFile") { args -> requestMediaPick(PluginMediaAction.PICK_FILE, args) }
            put("add") { args -> requestMediaPick(PluginMediaAction.PICK_FILE, args) }
        })

        val mediaPickBody: (PluginMediaAction, Array<Any?>) -> Any? = { action, args ->
            requestMediaPick(action, args)
        }
        ScriptableObject.putProperty(api, "media", module(cx, sc) { put ->
            put("pickFile") { mediaPickBody(PluginMediaAction.PICK_FILE, it) }
            put("pick") { mediaPickBody(PluginMediaAction.PICK_FILE, it) }
            put("addFile") { mediaPickBody(PluginMediaAction.PICK_FILE, it) }
            put("pickImage") { mediaPickBody(PluginMediaAction.PICK_IMAGE, it) }
            put("pickPhoto") { mediaPickBody(PluginMediaAction.PICK_IMAGE, it) }
            put("addPhoto") { mediaPickBody(PluginMediaAction.PICK_IMAGE, it) }
            put("addImage") { mediaPickBody(PluginMediaAction.PICK_IMAGE, it) }
            put("pickVideo") { mediaPickBody(PluginMediaAction.PICK_VIDEO, it) }
            put("addVideo") { mediaPickBody(PluginMediaAction.PICK_VIDEO, it) }
            put("takePhoto") { mediaPickBody(PluginMediaAction.TAKE_PHOTO, it) }
            put("capture") { mediaPickBody(PluginMediaAction.TAKE_PHOTO, it) }
            put("export") { mediaPickBody(PluginMediaAction.CREATE_DOCUMENT, it) }
            put("saveAs") { mediaPickBody(PluginMediaAction.CREATE_DOCUMENT, it) }
            put("share") { args ->
                androidApis.filesShare(
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString(),
                    args.getOrNull(2)?.toString() ?: "Share",
                )
            }
            put("hasCamera") { androidApis.hasCamera() }
        })

        ScriptableObject.putProperty(api, "camera", module(cx, sc) { put ->
            put("hasCamera") { androidApis.hasCamera() }
            put("available") { androidApis.hasCamera() }
            put("takePhoto") { mediaPickBody(PluginMediaAction.TAKE_PHOTO, it) }
            put("capture") { mediaPickBody(PluginMediaAction.TAKE_PHOTO, it) }
            put("pickImage") { mediaPickBody(PluginMediaAction.PICK_IMAGE, it) }
            put("pickVideo") { mediaPickBody(PluginMediaAction.PICK_VIDEO, it) }
        })

        ScriptableObject.putProperty(api, "usb", module(cx, sc) { put ->
            put("devices") { androidApis.usbDevicesJson() }
        })

        ScriptableObject.putProperty(api, "location", module(cx, sc) { put ->
            put("isEnabled") { androidApis.locationIsEnabled() }
            put("last") { androidApis.locationLastJson() }
            put("openSettings") { androidApis.openSettings("location") }
        })

        ScriptableObject.putProperty(api, "sensors", module(cx, sc) { put ->
            put("list") { androidApis.sensorsListJson() }
            put("start") { args ->
                val type = (args.getOrNull(0) as? Number)?.toInt() ?: return@put null
                val cb = args.getOrNull(1)
                androidApis.sensorStart(type) { invokeJs(cb, it, refreshUi = false) }
            }
            put("stop") { args ->
                androidApis.sensorStop(args.getOrNull(0)?.toString() ?: "")
                null
            }
            put("stopAll") { androidApis.sensorStopAll(); null }
        })

        ScriptableObject.putProperty(api, "audio", module(cx, sc) { put ->
            put("playUrl") { args ->
                val url = args.getOrNull(0)?.toString() ?: return@put false
                val cb = args.getOrNull(1)
                androidApis.audioPlayUrl(url) { invokeJs(cb, it, refreshUi = false) }
            }
            put("playFile") { args ->
                androidApis.audioPlayFile(args.getOrNull(0)?.toString() ?: "")
            }
            put("stop") { androidApis.audioStop(); null }
            put("tone") { args ->
                val tone = (args.getOrNull(0) as? Number)?.toInt() ?: 24
                val dur = (args.getOrNull(1) as? Number)?.toInt() ?: 200
                androidApis.audioTone(tone, dur)
            }
        })

        ScriptableObject.putProperty(api, "pm", module(cx, sc) { put ->
            put("hasPermission") { args ->
                androidApis.hasPermission(normalizePermission(args.getOrNull(0)?.toString() ?: ""))
            }
            put("hasFeature") { args ->
                androidApis.hasFeature(args.getOrNull(0)?.toString() ?: "")
            }
            put("features") { androidApis.featuresJson() }
            put("request") { args -> requestPermissions(args) }
            put("requestPermission") { args -> requestPermissions(args) }
            put("requestMultiple") { args -> requestPermissions(args) }
        })

        ScriptableObject.putProperty(api, "crypto", module(cx, sc) { put ->
            put("md5") { args -> androidApis.cryptoHash("md5", args.getOrNull(0)?.toString() ?: "") }
            put("sha1") { args -> androidApis.cryptoHash("sha1", args.getOrNull(0)?.toString() ?: "") }
            put("sha256") { args -> androidApis.cryptoHash("sha256", args.getOrNull(0)?.toString() ?: "") }
            put("sha512") { args -> androidApis.cryptoHash("sha512", args.getOrNull(0)?.toString() ?: "") }
            put("hash") { args ->
                androidApis.cryptoHash(
                    args.getOrNull(0)?.toString() ?: "sha256",
                    args.getOrNull(1)?.toString() ?: "",
                )
            }
            put("hashFile") { args ->
                androidApis.cryptoHashFile(
                    args.getOrNull(0)?.toString() ?: "sha256",
                    args.getOrNull(1)?.toString() ?: "",
                )
            }
            put("base64Encode") { args -> androidApis.cryptoBase64Encode(args.getOrNull(0)?.toString() ?: "") }
            put("base64Decode") { args -> androidApis.cryptoBase64Decode(args.getOrNull(0)?.toString() ?: "") }
            put("hexEncode") { args -> androidApis.cryptoHexEncode(args.getOrNull(0)?.toString() ?: "") }
            put("hexDecode") { args -> androidApis.cryptoHexDecode(args.getOrNull(0)?.toString() ?: "") }
            put("randomHex") { args ->
                androidApis.cryptoRandomHex((args.getOrNull(0) as? Number)?.toInt() ?: 16)
            }
            put("uuid") { androidApis.cryptoUuid() }
        })

        ScriptableObject.putProperty(api, "timers", module(cx, sc) { put ->
            put("setTimeout") { args ->
                val ms = (args.getOrNull(0) as? Number)?.toLong()
                    ?: (args.getOrNull(1) as? Number)?.toLong()
                    ?: 0L
                val fn = when {
                    args.getOrNull(0) is org.mozilla.javascript.Function -> args.getOrNull(0)
                    else -> args.getOrNull(1)
                }
                if (fn !is org.mozilla.javascript.Function) return@put -1
                val id = timerSeq.incrementAndGet()
                val r = Runnable {
                    timers.remove(id)
                    invokeJs(fn, null, refreshUi = true)
                }
                timers[id] = r
                mainHandler.postDelayed(r, ms.coerceIn(0L, 600_000L))
                id
            }
            put("setInterval") { args ->
                val ms = (args.getOrNull(0) as? Number)?.toLong()
                    ?: (args.getOrNull(1) as? Number)?.toLong()
                    ?: 1000L
                val fn = when {
                    args.getOrNull(0) is org.mozilla.javascript.Function -> args.getOrNull(0)
                    else -> args.getOrNull(1)
                }
                if (fn !is org.mozilla.javascript.Function) return@put -1
                val id = timerSeq.incrementAndGet()
                val delay = ms.coerceIn(50L, 600_000L)
                lateinit var r: Runnable
                r = Runnable {
                    if (!timers.containsKey(id)) return@Runnable
                    invokeJs(fn, null, refreshUi = true)
                    if (timers.containsKey(id)) {
                        mainHandler.postDelayed(r, delay)
                    }
                }
                timers[id] = r
                mainHandler.postDelayed(r, delay)
                id
            }
            put("clear") { args ->
                val id = (args.getOrNull(0) as? Number)?.toInt() ?: return@put false
                timers.remove(id)?.let { mainHandler.removeCallbacks(it) }
                true
            }
            put("clearTimeout") { args ->
                val id = (args.getOrNull(0) as? Number)?.toInt() ?: return@put false
                timers.remove(id)?.let { mainHandler.removeCallbacks(it) }
                true
            }
            put("clearInterval") { args ->
                val id = (args.getOrNull(0) as? Number)?.toInt() ?: return@put false
                timers.remove(id)?.let { mainHandler.removeCallbacks(it) }
                true
            }
            put("clearAll") {
                clearAllTimers()
                null
            }
        })

        ScriptableObject.putProperty(api, "haptics", module(cx, sc) { put ->
            put("vibrate") { args ->
                val ms = (args.getOrNull(0) as? Number)?.toLong() ?: 50L
                androidApis.vibrate(ms)
            }
            put("pattern") { args ->
                val arr = toLongArray(args.getOrNull(0))
                val repeat = (args.getOrNull(1) as? Number)?.toInt() ?: -1
                androidApis.vibratePattern(arr, repeat)
            }
            put("cancel") { androidApis.vibrateCancel(); null }
        })

        ScriptableObject.putProperty(api, "device", module(cx, sc) { put ->
            put("info") { androidApis.deviceInfoExtendedJson() }
            put("infoBasic") { device.deviceInfoJson() }
            put("battery") { androidApis.batteryJson() }
            put("features") { androidApis.featuresJson() }
        })


        ScriptableObject.putProperty(api, "app", module(cx, sc) { put ->
            put("open") { args ->
                val route = args.getOrNull(0)?.toString() ?: return@put false
                mainHandler.post {
                    navigateToScreen?.invoke(
                        if (route.startsWith("__app__:")) route else "__app__:$route"
                    )
                }
                true
            }
            put("packageName") { appContext.packageName }
        })

        ScriptableObject.putProperty(api, "assets", module(cx, sc) { put ->
            put("list") { args ->
                androidApis.assetsList(args.getOrNull(0)?.toString() ?: "")
            }
            put("exists") { args ->
                androidApis.assetsExists(args.getOrNull(0)?.toString() ?: "")
            }
            put("read") { args ->
                androidApis.assetsReadText(args.getOrNull(0)?.toString() ?: "")
            }
            put("readText") { args ->
                androidApis.assetsReadText(args.getOrNull(0)?.toString() ?: "")
            }
            put("readBase64") { args ->
                androidApis.assetsReadBase64(args.getOrNull(0)?.toString() ?: "")
            }
            put("dataUri") { args ->
                androidApis.assetsDataUri(args.getOrNull(0)?.toString() ?: "")
            }
        })

        fn("exportApi") { args ->
            exportLibrary(args, PluginLibraryRegistry.Kind.API)
        }
        fn("exportDesign") { args ->
            exportLibrary(args, PluginLibraryRegistry.Kind.DESIGN)
        }
        fn("importApi") { args ->
            val name = args.getOrNull(0)?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@fn null
            PluginLibraryRegistry.import(name, PluginLibraryRegistry.Kind.API)
                ?: PluginLibraryRegistry.import(name)
        }
        fn("importDesign") { args ->
            val name = args.getOrNull(0)?.toString()?.trim().orEmpty()
            if (name.isEmpty()) return@fn null
            val bindUi = args.getOrNull(1) !is Boolean || args.getOrNull(1) == true
            val raw = PluginLibraryRegistry.import(name, PluginLibraryRegistry.Kind.DESIGN)
                ?: PluginLibraryRegistry.import(name)
                ?: return@fn null
            if (!bindUi) return@fn raw
            val c = RhinoContext.getCurrentContext() ?: return@fn raw
            val s = scope ?: return@fn raw
            bindDesignPack(c, s, raw)
        }
        fn("listApis") { _ ->
            val c = RhinoContext.getCurrentContext() ?: return@fn null
            val s = scope ?: return@fn null
            c.newArray(s, PluginLibraryRegistry.list(PluginLibraryRegistry.Kind.API).toTypedArray())
        }
        fn("listDesigns") { _ ->
            val c = RhinoContext.getCurrentContext() ?: return@fn null
            val s = scope ?: return@fn null
            c.newArray(s, PluginLibraryRegistry.list(PluginLibraryRegistry.Kind.DESIGN).toTypedArray())
        }

        ScriptableObject.putProperty(api, "bleSpam", module(cx, sc) { put ->
            put("start") { args ->
                val target = args.getOrNull(0)?.toString() ?: return@put false
                bleSpamStart(target)
            }
            put("stop") { args ->
                val target = args.getOrNull(0)?.toString()
                if (target.isNullOrBlank() || target.equals("all", true)) {
                    BleSpamRuntime.stopAllBleSpam()
                    true
                } else {
                    bleSpamStop(target)
                }
            }
            put("toggle") { args ->
                val target = args.getOrNull(0)?.toString() ?: return@put false
                if (bleSpamIsActive(target)) bleSpamStop(target) else bleSpamStart(target)
            }
            put("stopAll") {
                BleSpamRuntime.stopAllBleSpam()
                null
            }
            put("isActive") { args ->
                bleSpamIsActive(args.getOrNull(0)?.toString() ?: "")
            }
            put("active") {
                val active = mutableListOf<String>()
                if (BleSpamRuntime.isSectionActive(BleSection.IOS)) active += "apple"
                if (BleSpamRuntime.isSectionActive(BleSection.SAMSUNG)) active += "samsung"
                if (BleSpamRuntime.isSectionActive(BleSection.ANDROID)) active += "android"
                if (BleSpamRuntime.isSectionActive(BleSection.WINDOWS)) active += "windows"
                if (BleSpamRuntime.isSectionActive(BleSection.XIAOMI)) active += "xiaomi"
                if (BleSpamRuntime.isSectionActive(BleSection.PHANTOM)) active += "phantom"
                if (BleSpamRuntime.kitchenSinkActive.value) active += "all"
                org.json.JSONArray(active).toString()
            }
            put("setDelay") { args ->
                val ms = (args.getOrNull(0) as? Number)?.toInt() ?: return@put false
                BleSpamRuntime.setBleDelay(ms)
                true
            }
            put("getDelay") { BleSpamRuntime.bleDelay.value }
            put("startMode") { args ->
                val first = args.getOrNull(0)
                val map = if (first is Scriptable) asMap(first) else emptyMap()
                val typeStr = map["type"]?.toString()?.lowercase()
                    ?: args.getOrNull(0)?.toString()?.lowercase()
                    ?: return@put false
                val spamType = when (typeStr) {
                    "continuity", "apple", "ios", "iphone" -> SpamType.CONTINUITY
                    "easy_setup", "easysetup", "samsung" -> SpamType.EASY_SETUP
                    "fast_pair", "fastpair", "android" -> SpamType.FAST_PAIR
                    "swift_pair", "swiftpair", "windows" -> SpamType.SWIFT_PAIR
                    "xiaomi" -> SpamType.XIAOMI
                    "phantom" -> SpamType.PHANTOM
                    else -> return@put false
                }
                val subtype: Any? = when (spamType) {
                    SpamType.CONTINUITY -> {
                        val sub = (map["subtype"] ?: map["mode"] ?: args.getOrNull(1))?.toString()?.lowercase()
                        val crash = map["crash"] as? Boolean ?: false
                        val contType = when (sub) {
                            "action", "nearby" -> ContinuityType.ACTION
                            "notyour", "not_your", "notyourdevice" -> ContinuityType.NOTYOURDEVICE
                            else -> ContinuityType.DEVICE
                        }
                        ContinuityMode(contType, crash || sub == "crash")
                    }
                    SpamType.EASY_SETUP -> {
                        val sub = (map["subtype"] ?: args.getOrNull(1))?.toString()?.lowercase()
                        when (sub) {
                            "watch" -> EasySetupDevice.Type.WATCH
                            else -> EasySetupDevice.Type.BUDS
                        }
                    }
                    else -> null
                }
                BleSpamRuntime.toggleBleSpam(spamType, subtype)
                true
            }
            put("sections") {
                """["apple","samsung","android","xiaomi","windows","phantom","all"]"""
            }
        })

        ScriptableObject.putProperty(api, "pluginId", manifest.id)
        ScriptableObject.putProperty(api, "pluginName", manifest.name)
        ScriptableObject.putProperty(api, "isLibrary", manifest.isLibrary)
        ScriptableObject.putProperty(api, "isDesignLibrary", manifest.isDesignLibrary)

        val dolphyNs = buildDolphyNamespace(cx, sc, api)
        ScriptableObject.putProperty(api, "dolphy", dolphyNs)
        ScriptableObject.putProperty(sc, "dolphy", dolphyNs)

        return api
    }

    
    private fun exportLibrary(args: Array<Any?>, defaultKind: PluginLibraryRegistry.Kind): Any {
        val first = args.getOrNull(0)
        val name: String
        val value: Any?
        var androidAlias: String? = null
        var dolphyAlias: String? = null
        var kind = defaultKind

        if (first is Scriptable && first !is org.mozilla.javascript.Function &&
            args.getOrNull(1) !is Scriptable
        ) {
            val o = asMap(first)
            name = (o["name"] ?: o["id"] ?: o["apiName"])?.toString()?.trim().orEmpty()
            value = o["api"] ?: o["value"] ?: o["exports"] ?: o["object"]
            androidAlias = (o["android"] ?: o["androidName"] ?: o["androidApi"])?.toString()
            dolphyAlias = (o["dolphy"] ?: o["dolphyName"] ?: o["dolphyApi"])?.toString()
            val kindRaw = (o["kind"] ?: o["type"])?.toString()?.lowercase()
            if (kindRaw in setOf("design", "ui", "theme", "material", "m3")) {
                kind = PluginLibraryRegistry.Kind.DESIGN
            } else if (kindRaw in setOf("api", "lib", "logic")) {
                kind = PluginLibraryRegistry.Kind.API
            }
        } else {
            name = first?.toString()?.trim().orEmpty()
            value = args.getOrNull(1)
            val meta = args.getOrNull(2)
            if (meta is Scriptable) {
                val o = asMap(meta)
                androidAlias = (o["android"] ?: o["androidName"])?.toString()
                dolphyAlias = (o["dolphy"] ?: o["dolphyName"])?.toString()
            } else if (meta is String) {
                androidAlias = meta
                dolphyAlias = args.getOrNull(3)?.toString()
            }
        }

        if (name.isEmpty()) return false
        if (value !is Scriptable) {
            Log.w(TAG, "export($name): value must be a JS object")
            return false
        }
        PluginLibraryRegistry.export(
            pluginId = manifest.id,
            apiName = name,
            value = value,
            kind = kind,
            androidName = androidAlias,
            dolphyName = dolphyAlias,
        )
        return true
    }

    
    private fun bindDesignPack(cx: RhinoContext, sc: Scriptable, pack: Scriptable): Scriptable {
        val bound = cx.newObject(sc)
        val ui = uiBuilder.createUiObject(cx, sc)
        for (id in ScriptableObject.getPropertyIds(pack)) {
            val key = id.toString()
            val prop = ScriptableObject.getProperty(pack, key)
            if (prop is org.mozilla.javascript.Function) {
                ScriptableObject.putProperty(bound, key, object : BaseFunction() {
                    override fun call(
                        c: RhinoContext,
                        scope: Scriptable,
                        thisObj: Scriptable,
                        args: Array<Any?>,
                    ): Any? {
                        val first = args.getOrNull(0)
                        val looksLikeUi = first is Scriptable &&
                            first !is org.mozilla.javascript.Function &&
                            (
                                ScriptableObject.hasProperty(first, "button") ||
                                    ScriptableObject.hasProperty(first, "scaffold") ||
                                    ScriptableObject.hasProperty(first, "text") ||
                                    ScriptableObject.hasProperty(first, "m3")
                                )
                        val callArgs: Array<Any?> = if (looksLikeUi) {
                            args
                        } else {
                            arrayOf(ui, *args)
                        }
                        return prop.call(c, scope, pack, callArgs)
                    }
                })
            } else if (prop is Scriptable && prop !is org.mozilla.javascript.Function) {
                ScriptableObject.putProperty(bound, key, bindDesignPack(cx, sc, prop))
            } else {
                ScriptableObject.putProperty(bound, key, prop)
            }
        }
        return bound
    }

    
    private fun buildDolphyNamespace(cx: RhinoContext, sc: Scriptable, api: Scriptable): Scriptable {
        fun ns(build: (Scriptable) -> Unit): Scriptable {
            val o = cx.newObject(sc)
            build(o)
            return o
        }
        fun putFn(obj: Scriptable, name: String, body: (Array<Any?>) -> Any?) {
            ScriptableObject.putProperty(obj, name, object : BaseFunction() {
                override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                    return body(args)
                }
            })
        }
        fun callApi(path: String, method: String, args: Array<Any?>): Any? {
            return try {
                val mod = ScriptableObject.getProperty(api, path)
                if (mod !is Scriptable) return null
                val fn = ScriptableObject.getProperty(mod, method)
                if (fn is org.mozilla.javascript.Function) {
                    fn.call(RhinoContext.getCurrentContext() ?: cx, sc, mod, args)
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "dolphy.$method.$path", e)
                null
            }
        }

        val root = cx.newObject(sc)

        ScriptableObject.putProperty(root, "send", ns { o ->
            putFn(o, "ir") { callApi("ir", "send", it) }
            putFn(o, "notify") { callApi("notify", "show", it) }
            putFn(o, "clipboard") { callApi("clipboard", "set", it) }
            putFn(o, "ble") { callApi("ble", "advertise", it) }
            putFn(o, "gatt") { callApi("ble", "write", it) }
        })

        ScriptableObject.putProperty(root, "pick", ns { o ->
            putFn(o, "file") { callApi("media", "pickFile", it) }
            putFn(o, "image") { callApi("media", "pickImage", it) }
            putFn(o, "photo") { callApi("media", "pickImage", it) }
            putFn(o, "video") { callApi("media", "pickVideo", it) }
            putFn(o, "camera") { callApi("media", "takePhoto", it) }
        })
        ScriptableObject.putProperty(root, "add", ns { o ->
            putFn(o, "file") { callApi("media", "addFile", it) }
            putFn(o, "image") { callApi("media", "addImage", it) }
            putFn(o, "photo") { callApi("media", "addPhoto", it) }
            putFn(o, "video") { callApi("media", "addVideo", it) }
        })
        putFn(root, "takePhoto") { callApi("camera", "takePhoto", it) }
        putFn(root, "export") { callApi("files", "export", it) }
        putFn(root, "download") { callApi("net", "download", it) }
        putFn(root, "snackbar") { callApi("snackbar", "show", it) }

        ScriptableObject.putProperty(root, "scan", ns { o ->
            putFn(o, "wifi") { callApi("wifi", "scan", it) }
            putFn(o, "ble") { callApi("ble", "startScan", it) }
            putFn(o, "bt") { callApi("bt", "startDiscovery", it) }
            putFn(o, "nsd") { callApi("net", "nsdDiscover", it) }
        })

        ScriptableObject.putProperty(root, "exec", ns { o ->
            putFn(o, "shell") { callApi("shell", "exec", it) }
            putFn(o, "root") { callApi("root", "exec", it) }
            putFn(o, "shizuku") { callApi("shizuku", "exec", it) }
        })

        ScriptableObject.putProperty(root, "toggle", ns { o ->
            putFn(o, "irStorm") { callApi("ir", "toggleStorm", it) }
            putFn(o, "irJammer") { callApi("ir", "toggleJammer", it) }
            putFn(o, "bleSpam") { callApi("bleSpam", "toggle", it) }
        })

        ScriptableObject.putProperty(root, "read", ns { o ->
            putFn(o, "clipboard") { callApi("clipboard", "get", it) }
            putFn(o, "files") { callApi("files", "read", it) }
            putFn(o, "assets") { callApi("assets", "read", it) }
            putFn(o, "battery") { callApi("device", "battery", it) }
            putFn(o, "location") { callApi("location", "last", it) }
        })

        ScriptableObject.putProperty(root, "write", ns { o ->
            putFn(o, "files") { callApi("files", "write", it) }
            putFn(o, "clipboard") { callApi("clipboard", "set", it) }
            putFn(o, "gatt") { callApi("ble", "write", it) }
        })

        ScriptableObject.putProperty(root, "play", ns { o ->
            putFn(o, "audio") { callApi("audio", "playUrl", it) }
            putFn(o, "file") { callApi("audio", "playFile", it) }
            putFn(o, "tone") { callApi("audio", "tone", it) }
        })

        putFn(root, "vibrate") { callApi("vibrator", "vibrate", it) }
        putFn(root, "openUrl") { callApi("intent", "openUrl", it) }
        putFn(root, "http") { callApi("net", "http", it) }

        for (mod in listOf(
            "wifi", "ble", "bt", "nfc", "ir", "net", "root", "shizuku", "shell",
            "clipboard", "vibrator", "notify", "intent", "files", "usb", "location",
            "sensors", "audio", "pm", "device", "app", "assets", "bleSpam", "dialog",
            "media", "camera", "crypto", "timers", "haptics", "snackbar", "bottomSheet",
        )) {
            val m = ScriptableObject.getProperty(api, mod)
            if (m != null && m != Scriptable.NOT_FOUND) {
                ScriptableObject.putProperty(root, mod, m)
            }
        }

        putFn(root, "exportApi") { exportLibrary(it, PluginLibraryRegistry.Kind.API) }
        putFn(root, "exportDesign") { exportLibrary(it, PluginLibraryRegistry.Kind.DESIGN) }
        putFn(root, "importApi") { args ->
            val n = args.getOrNull(0)?.toString()?.trim().orEmpty()
            if (n.isEmpty()) null else PluginLibraryRegistry.import(n)
        }
        putFn(root, "importDesign") { args ->
            val n = args.getOrNull(0)?.toString()?.trim().orEmpty()
            if (n.isEmpty()) return@putFn null
            val raw = PluginLibraryRegistry.import(n, PluginLibraryRegistry.Kind.DESIGN)
                ?: PluginLibraryRegistry.import(n)
                ?: return@putFn null
            val c = RhinoContext.getCurrentContext() ?: return@putFn raw
            val s = scope ?: return@putFn raw
            bindDesignPack(c, s, raw)
        }

        return root
    }

    private fun resolveBleSection(target: String): BleSection? {
        return when (target.trim().lowercase()) {
            "apple", "ios", "iphone", "continuity", "airpods" -> BleSection.IOS
            "samsung", "easysetup", "easy_setup", "buds", "galaxy" -> BleSection.SAMSUNG
            "android", "fastpair", "fast_pair", "google" -> BleSection.ANDROID
            "xiaomi", "mi", "quickconnect" -> BleSection.XIAOMI
            "windows", "swiftpair", "swift_pair", "ms", "microsoft" -> BleSection.WINDOWS
            "phantom" -> BleSection.PHANTOM
            else -> null
        }
    }

    private fun bleSpamStart(target: String): Boolean {
        val t = target.trim().lowercase()
        if (t == "all" || t == "kitchen" || t == "kitchensink") {
            if (!BleSpamRuntime.kitchenSinkActive.value) {
                BleSpamRuntime.toggleKitchenSink()
            }
            return true
        }
        val section = resolveBleSection(t) ?: return false
        if (!BleSpamRuntime.isSectionActive(section)) {
            BleSpamRuntime.toggleSection(section)
        }
        return true
    }

    private fun bleSpamStop(target: String): Boolean {
        val t = target.trim().lowercase()
        if (t == "all" || t == "kitchen" || t == "kitchensink") {
            if (BleSpamRuntime.kitchenSinkActive.value) {
                BleSpamRuntime.toggleKitchenSink()
            }
            BleSpamRuntime.stopAllBleSpam()
            return true
        }
        val section = resolveBleSection(t) ?: return false
        if (BleSpamRuntime.isSectionActive(section)) {
            BleSpamRuntime.toggleSection(section)
        }
        return true
    }

    private fun bleSpamIsActive(target: String): Boolean {
        val t = target.trim().lowercase()
        if (t.isEmpty()) return false
        if (t == "all" || t == "kitchen" || t == "kitchensink") {
            return BleSpamRuntime.kitchenSinkActive.value
        }
        val section = resolveBleSection(t) ?: return false
        return BleSpamRuntime.isSectionActive(section)
    }

    
    private fun requestMediaPick(action: PluginMediaAction, args: Array<Any?>): Any? {
        val first = args.getOrNull(0)
        val second = args.getOrNull(1)
        val opts: Map<String, Any?>
        val cb: Any?
        when {
            first is org.mozilla.javascript.Function -> {
                opts = emptyMap()
                cb = first
            }
            first is Scriptable -> {
                opts = asMap(first)
                cb = opts["onResult"] ?: opts["callback"] ?: opts["onDone"]
                    ?: opts["onSuccess"] ?: second
            }
            first is CharSequence && action == PluginMediaAction.CREATE_DOCUMENT -> {
                opts = mapOf(
                    "path" to first.toString(),
                    "name" to (second as? CharSequence)?.toString(),
                )
                cb = args.getOrNull(2) ?: second.takeIf { it is org.mozilla.javascript.Function }
            }
            else -> {
                opts = emptyMap()
                cb = first ?: second
            }
        }

        val mimeList = mutableListOf<String>()
        val rawMimes = opts["mimeTypes"] ?: opts["mime"] ?: opts["type"]
        when (rawMimes) {
            is List<*> -> rawMimes.mapNotNullTo(mimeList) { it?.toString() }
            is String -> {
                rawMimes.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                    .let { mimeList.addAll(it) }
            }
        }
        if (mimeList.isEmpty()) {
            when (action) {
                PluginMediaAction.PICK_IMAGE, PluginMediaAction.TAKE_PHOTO -> mimeList += "image/*"
                PluginMediaAction.PICK_VIDEO -> mimeList += "video/*"
                PluginMediaAction.PICK_FILE, PluginMediaAction.CREATE_DOCUMENT -> mimeList += "*/*"
            }
        }
        val multiple = (opts["multiple"] as? Boolean) == true
        val destPath = opts["path"]?.toString()
        val includeBase64 = (opts["includeBase64"] as? Boolean) == true
        val suggestedName = opts["name"]?.toString()
        val req = PluginMediaRequest(
            action = action,
            mimeTypes = mimeList,
            multiple = multiple,
            destPath = destPath,
            includeBase64 = includeBase64,
            suggestedName = suggestedName,
            onResult = { res ->
                if (cb is org.mozilla.javascript.Function) {
                    val cx = org.mozilla.javascript.Context.getCurrentContext()
                    val scope = org.mozilla.javascript.ScriptableObject.getTopLevelScope(cb)
                    if (cx != null && scope != null) {
                        try {
                            cb.call(cx, scope, scope, arrayOf(res))
                        } catch (_: Exception) {}
                    }
                }
            }
        )
        mediaRequestHandler?.invoke(req)
        return Undefined
    }

    fun importMediaUri(uriString: String, destPath: String?, includeBase64: Boolean): String {
        return try {
            androidApis.importUriToSandbox(
                android.net.Uri.parse(uriString),
                destPath,
                includeBase64,
            )
        } catch (e: Exception) {
            org.json.JSONObject().put("ok", false).put("error", e.message).toString()
        }
    }

    fun createCameraCaptureTarget(fileName: String? = null): Pair<java.io.File, android.net.Uri>? =
        androidApis.createCameraCaptureTarget(fileName)

    fun importCameraFile(file: java.io.File, destPath: String?, includeBase64: Boolean): String =
        androidApis.importCameraFile(file, destPath, includeBase64)

    fun exportSandboxToUri(sandboxPath: String, destUri: String): String =
        androidApis.exportSandboxToUri(sandboxPath, android.net.Uri.parse(destUri))

    
    fun onNfcTag(tag: android.nfc.Tag) {
        device.onNfcTagDiscovered(tag)
    }

    private fun toIntArray(v: Any?): IntArray {
        return when (v) {
            is IntArray -> v
            is List<*> -> v.mapNotNull { (it as? Number)?.toInt() }.toIntArray()
            is org.mozilla.javascript.NativeArray -> {
                IntArray(v.length.toInt()) { i ->
                    (v.get(i, v) as? Number)?.toInt() ?: 0
                }
            }
            is Scriptable -> {
                val len = (ScriptableObject.getProperty(v, "length") as? Number)?.toInt() ?: 0
                IntArray(len) { i ->
                    (ScriptableObject.getProperty(v, i) as? Number)?.toInt() ?: 0
                }
            }
            is String -> v.split(',', ' ', ';').mapNotNull { it.trim().toIntOrNull() }.toIntArray()
            else -> intArrayOf()
        }
    }

    private fun toLongArray(v: Any?): LongArray {
        return when (v) {
            is LongArray -> v
            is IntArray -> LongArray(v.size) { v[it].toLong() }
            is List<*> -> v.mapNotNull { (it as? Number)?.toLong() }.toLongArray()
            is org.mozilla.javascript.NativeArray -> {
                LongArray(v.length.toInt()) { i ->
                    (v.get(i, v) as? Number)?.toLong() ?: 0L
                }
            }
            is Scriptable -> {
                val len = (ScriptableObject.getProperty(v, "length") as? Number)?.toInt() ?: 0
                LongArray(len) { i ->
                    (ScriptableObject.getProperty(v, i) as? Number)?.toLong() ?: 0L
                }
            }
            else -> longArrayOf(0, 50, 50, 50)
        }
    }

    private fun module(cx: RhinoContext, sc: Scriptable, build: (((String, (Array<Any?>) -> Any?) -> Unit) -> Unit)): Scriptable {
        val obj = cx.newObject(sc)
        build { name, body ->
            ScriptableObject.putProperty(obj, name, object : BaseFunction() {
                override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                    return body(args)
                }
            })
        }
        return obj
    }

    private fun buildConsole(cx: RhinoContext, sc: Scriptable): Scriptable {
        val console = cx.newObject(sc)
        val logFn = object : BaseFunction() {
            override fun call(c: RhinoContext, scope: Scriptable, thisObj: Scriptable, args: Array<Any?>): Any? {
                Log.d("Plugin:${manifest.id}", args.joinToString(" ") { it?.toString() ?: "null" })
                return null
            }
        }
        ScriptableObject.putProperty(console, "log", logFn)
        ScriptableObject.putProperty(console, "warn", logFn)
        ScriptableObject.putProperty(console, "error", logFn)
        return console
    }

    private fun invokeJs(fn: Any?, arg: Any?, refreshUi: Boolean = true) {
        mainHandler.post {
            synchronized(this) {
                try {
                    withRhino { cx, sc ->
                        if (sc == null || fn !is org.mozilla.javascript.Function) return@withRhino
                        uiBuilder.cx = cx
                        uiBuilder.scope = sc
                        val jsArg = when (arg) {
                            is String -> {
                                try {
                                    if (arg.trimStart().startsWith("{") || arg.trimStart().startsWith("[")) {
                                        cx.evaluateString(sc, "($arg)", "json", 1, null)
                                    } else arg
                                } catch (_: Exception) {
                                    arg
                                }
                            }
                            is Boolean, is Number -> arg
                            null -> null
                            else -> arg.toString()
                        }
                        fn.call(cx, sc, sc, arrayOf(jsArg))
                    }

                    if (refreshUi) scheduleUiRefresh()
                } catch (e: Exception) {
                    Log.w(TAG, "invokeJs", e)
                }
            }
        }
    }

    private fun stateToJs(cx: RhinoContext, sc: Scriptable): Scriptable {
        val obj = cx.newObject(sc)
        for ((k, v) in state) {
            ScriptableObject.putProperty(obj, k, jvmToJs(cx, sc, v))
        }
        return obj
    }

    private fun jvmToJs(cx: RhinoContext, sc: Scriptable, v: Any?): Any? {
        return when (v) {
            null -> null
            is Boolean, is String, is Number -> v
            is Map<*, *> -> {
                val o = cx.newObject(sc)
                v.forEach { (kk, vv) ->
                    ScriptableObject.putProperty(o, kk.toString(), jvmToJs(cx, sc, vv))
                }
                o
            }
            is List<*> -> cx.newArray(sc, v.map { jvmToJs(cx, sc, it) }.toTypedArray())
            else -> v.toString()
        }
    }

    private fun jsToJvm(v: Any?): Any? {
        return when (v) {
            null, RhinoContext.getUndefinedValue() -> null
            is Boolean, is String, is Number -> v
            is Scriptable -> {
                if (v is org.mozilla.javascript.NativeArray) {
                    (0 until v.length.toInt()).map { jsToJvm(v.get(it, v)) }
                } else {
                    asMap(v)
                }
            }
            else -> v.toString()
        }
    }

    private fun asMap(v: Any?): Map<String, Any?> {
        if (v !is Scriptable) return emptyMap()
        val map = mutableMapOf<String, Any?>()
        for (id in ScriptableObject.getPropertyIds(v)) {
            map[id.toString()] = jsToJvm(ScriptableObject.getProperty(v, id.toString()))
        }
        return map
    }

    private object Undefined

    private fun loadIrButtons(path: String): List<com.droid.dolphy.IrButton> {
        val f = java.io.File(path)
        if (f.exists()) return com.droid.dolphy.IrRepository.parseIrFile(f)
        val assetPath = path.removePrefix("file:///android_asset/").removePrefix("android_asset/")
        return com.droid.dolphy.IrRepository.parseIrAsset(appContext, assetPath)
    }

    private fun normalizePermission(p: String): String {
        return if (p.startsWith("android.permission.")) p else "android.permission.$p"
    }

    private fun requestPermissions(args: Array<Any?>): Any? {
        val perms = mutableListOf<String>()
        val first = args.getOrNull(0)
        when (first) {
            is String -> perms.add(normalizePermission(first))
            is org.mozilla.javascript.NativeArray -> {
                for (i in 0 until first.length) {
                    val v = first.get(i.toInt(), first)?.toString()
                    if (v != null) perms.add(normalizePermission(v))
                }
            }
        }
        val cb = args.getOrNull(1) as? org.mozilla.javascript.Function ?: args.getOrNull(0) as? org.mozilla.javascript.Function
        
        val req = PluginPermissionRequest(
            permissions = perms,
            onResult = { granted ->
                if (cb != null) {
                    val cx = org.mozilla.javascript.Context.getCurrentContext()
                    val scope = org.mozilla.javascript.ScriptableObject.getTopLevelScope(cb)
                    if (cx != null && scope != null) {
                        try {
                            cb.call(cx, scope, scope, arrayOf(granted))
                        } catch (e: Exception) {}
                    }
                }
            }
        )
        permissionRequestHandler?.invoke(req)
        return Undefined
    }
    companion object {
        private const val TAG = "JsPluginSession"

        fun parseManifest(source: String, fallbackId: String, fallbackName: String): PluginManifest {
            val line = source.lineSequence().firstOrNull { it.contains("@plugin", ignoreCase = true) } ?: ""
            fun fromLine(key: String): String? =
                Regex("""\b$key\s*=\s*["']([^"']+)["']""").find(line)?.groupValues?.get(1)
                    ?: Regex("""\b$key\s*=\s*([A-Za-z0-9_.-]+)""").find(line)?.groupValues?.get(1)

            fun metaQuoted(key: String): String? {
                val re2 = Regex("""//\s*@$key\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                return re2.find(source)?.groupValues?.getOrNull(1)
            }

            fun metaBare(key: String): String? {
                val re2 = Regex("""//\s*@$key\s*=\s*([A-Za-z0-9_.-]+)""", RegexOption.IGNORE_CASE)
                return re2.find(source)?.groupValues?.getOrNull(1)
            }

            val id = fromLine("id") ?: metaQuoted("id") ?: fallbackId
            val name = fromLine("name") ?: metaQuoted("name") ?: fallbackName
            val version = fromLine("version") ?: metaQuoted("version") ?: "1.0"
            val description = fromLine("description") ?: metaQuoted("description") ?: ""
            val author = fromLine("author") ?: metaQuoted("author") ?: ""

            val libraryFlag = (
                fromLine("library")
                    ?: fromLine("type")
                    ?: metaQuoted("library")
                    ?: metaBare("library")
                    ?: metaQuoted("type")
                    ?: metaBare("type")
                    ?: ""
                ).lowercase()
            val designFlag = (
                fromLine("design")
                    ?: metaQuoted("design")
                    ?: metaBare("design")
                    ?: ""
                ).lowercase()
            val isDesignLibrary = designFlag in setOf("true", "1", "yes", "design", "ui", "m3", "material") ||
                libraryFlag in setOf("design", "ui", "theme", "material", "m3")
            val isLibrary = isDesignLibrary ||
                libraryFlag in setOf("true", "1", "yes", "library", "lib")

            return PluginManifest(
                id, name, version, description, author,
                isLibrary = isLibrary,
                isDesignLibrary = isDesignLibrary,
            )
        }
    }
}

