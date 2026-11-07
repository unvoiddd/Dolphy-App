@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.droid.dolphy

import android.Manifest
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlin.math.roundToInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.*
import com.droid.dolphy.nfc.ui.*
import com.droid.dolphy.plugin.PluginBluetoothHooks
import com.droid.dolphy.qr.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class SpamViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpamViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpamViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SpamViewModel(private val application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("DolphyPrefs", Context.MODE_PRIVATE)

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean("onboarding_completed", false))
    val onboardingCompleted = _onboardingCompleted.asStateFlow()

    private val _lastSeenVersion = MutableStateFlow(prefs.getString("last_seen_version", ""))
    val lastSeenVersion = _lastSeenVersion.asStateFlow()

    fun completeWelcomeDialog() {


        val version = runCatching {
            application.packageManager.getPackageInfo(application.packageName, 0).versionName
        }.getOrNull()?.takeIf { !it.isNullOrBlank() } ?: "2.3"
        _lastSeenVersion.value = version
        _onboardingCompleted.value = true
        prefs.edit()
            .putString("last_seen_version", version)
            .putBoolean("onboarding_completed", true)
            .apply()
    }


    private val _themeMode = MutableStateFlow(prefs.getInt("theme_mode", 0))
    val themeMode: StateFlow<Int> = _themeMode


    private val _isDarkThemeLegacy = MutableStateFlow(prefs.getBoolean("is_dark_theme", true))
    val isDarkThemeLegacy: StateFlow<Boolean> = _isDarkThemeLegacy


    private val _isDarkTheme = MutableStateFlow(
        when (prefs.getInt("theme_mode", 0)) {
            1 -> true
            2 -> false
            else -> (application.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    )
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme

    private val _isCustomBackgroundEnabled = MutableStateFlow(prefs.getBoolean("custom_bg_enabled", false))
    val isCustomBackgroundEnabled: StateFlow<Boolean> = _isCustomBackgroundEnabled

    private val _customBackgroundUri = MutableStateFlow<Uri?>(prefs.getString("custom_bg_uri", null)?.toUri())
    val customBackgroundUri: StateFlow<Uri?> = _customBackgroundUri

    private val _accentColor = MutableStateFlow(Color(prefs.getInt("accent_color", OrangeAccent.toArgb())))
    val accentColor: StateFlow<Color> = _accentColor

    private val _isAdaptiveColor = MutableStateFlow(prefs.getBoolean("is_adaptive_color", true))
    val isAdaptiveColor: StateFlow<Boolean> = _isAdaptiveColor

    private val _flipperFontEnabled = MutableStateFlow(prefs.getBoolean("flipper_font_enabled", false))
    val flipperFontEnabled: StateFlow<Boolean> = _flipperFontEnabled
    private val _flipperFontScale = MutableStateFlow(prefs.getFloat("flipper_font_scale", 1.08f))
    val flipperFontScale: StateFlow<Float> = _flipperFontScale
    private val _uiScale = MutableStateFlow(prefs.getFloat("ui_scale", 0.9f).coerceIn(0.8f, 1.2f))
    val uiScale: StateFlow<Float> = _uiScale
    private val _liquidGlassEnabled = MutableStateFlow(false)
    val liquidGlassEnabled: StateFlow<Boolean> = _liquidGlassEnabled
    private val _liquidGlassButtons = MutableStateFlow(prefs.getBoolean("liquid_glass_buttons", true))
    val liquidGlassButtons: StateFlow<Boolean> = _liquidGlassButtons
    private val _liquidGlassTopBars = MutableStateFlow(prefs.getBoolean("liquid_glass_topbars", true))
    val liquidGlassTopBars: StateFlow<Boolean> = _liquidGlassTopBars
    private val _liquidGlassNav = MutableStateFlow(prefs.getBoolean("liquid_glass_nav", true))
    val liquidGlassNav: StateFlow<Boolean> = _liquidGlassNav

    val glassNavEnabled: StateFlow<Boolean> = _liquidGlassEnabled
    private val _animatedBackgroundEnabled = MutableStateFlow(prefs.getBoolean("animated_background_enabled", false))
    val animatedBackgroundEnabled: StateFlow<Boolean> = _animatedBackgroundEnabled
    private val _expressiveEnabled = MutableStateFlow(prefs.getBoolean("md3_expressive", false))
    val expressiveEnabled: StateFlow<Boolean> = _expressiveEnabled
    private val _appLanguage = MutableStateFlow(prefs.getString("app_language", "ru") ?: "ru")
    val appLanguage: StateFlow<String> = _appLanguage
    private val _cyclicDolphinAnimationEnabled = MutableStateFlow(prefs.getBoolean("cyclic_dolphin_animation_enabled", false))
    val cyclicDolphinAnimationEnabled: StateFlow<Boolean> = _cyclicDolphinAnimationEnabled
    private val _quickStartupEnabled = MutableStateFlow(prefs.getBoolean("quick_startup_enabled", false))
    val quickStartupEnabled: StateFlow<Boolean> = _quickStartupEnabled
    private val _performanceModeEnabled = MutableStateFlow(prefs.getBoolean("performance_mode_enabled", false))
    val performanceModeEnabled: StateFlow<Boolean> = _performanceModeEnabled
    private val _fabDestinationRoute = MutableStateFlow(prefs.getString("fab_destination_route", "other") ?: "other")
    val fabDestinationRoute: StateFlow<String> = _fabDestinationRoute
    private val _fabEnabled = MutableStateFlow(prefs.getBoolean("fab_enabled", true))
    val fabEnabled: StateFlow<Boolean> = _fabEnabled

    private val _hidTouchpadSensitivityMove = MutableStateFlow(prefs.getFloat("hid_touchpad_sensitivity_move", 1.5f))
    val hidTouchpadSensitivityMove: StateFlow<Float> = _hidTouchpadSensitivityMove

    private val _hidTouchpadSensitivityScroll = MutableStateFlow(prefs.getFloat("hid_touchpad_sensitivity_scroll", 0.4f))
    val hidTouchpadSensitivityScroll: StateFlow<Float> = _hidTouchpadSensitivityScroll

    private val _isNameSpoofingEnabled = MutableStateFlow(prefs.getBoolean("is_name_spoofing_enabled", false))
    val isNameSpoofingEnabled: StateFlow<Boolean> = _isNameSpoofingEnabled

    private val _spoofedName = MutableStateFlow(prefs.getString("spoofed_name", Build.MODEL) ?: Build.MODEL)
    val spoofedName: StateFlow<String> = _spoofedName

    private val _functionsMenuViewType = MutableStateFlow(prefs.getInt("functions_menu_view_type", 0))
    val functionsMenuViewType: StateFlow<Int> = _functionsMenuViewType

    private val _functionsTileSize = MutableStateFlow(prefs.getFloat("functions_tile_size", 140f))
    val functionsTileSize: StateFlow<Float> = _functionsTileSize

    private val _appStyleMode = MutableStateFlow(prefs.getInt("app_style_mode", 0))
    val appStyleMode: StateFlow<Int> = _appStyleMode

    fun setAppStyleMode(mode: Int) {
        prefs.edit { putInt("app_style_mode", mode) }
        _appStyleMode.value = mode
    }

    private val _pinnedCardRoutes = MutableStateFlow(
        prefs.getString("pinned_card_routes_list", "")
            ?.split(",")
            ?.filter { it.isNotBlank() } ?: emptyList()
    )
    val pinnedCardRoutes: StateFlow<List<String>> = _pinnedCardRoutes

    fun togglePinCard(route: String) {
        val current = _pinnedCardRoutes.value.toMutableList()
        if (current.contains(route)) {
            current.remove(route)
        } else {
            if (current.size < 4) {
                current.add(route)
            } else {
                current.removeAt(current.size - 1)
                current.add(route)
            }
        }
        prefs.edit { putString("pinned_card_routes_list", current.joinToString(",")) }
        _pinnedCardRoutes.value = current
    }

    fun unpinCard(route: String) {
        val current = _pinnedCardRoutes.value.toMutableList()
        current.remove(route)
        prefs.edit { putString("pinned_card_routes_list", current.joinToString(",")) }
        _pinnedCardRoutes.value = current
    }

    fun pinCard(route: String) {
        val current = _pinnedCardRoutes.value.toMutableList()
        if (!current.contains(route)) {
            if (current.size >= 4) {
                current.removeAt(current.size - 1)
            }
            current.add(route)
            prefs.edit { putString("pinned_card_routes_list", current.joinToString(",")) }
            _pinnedCardRoutes.value = current
        }
    }

    fun recordCardUsage(route: String) {
        val count = prefs.getInt("card_usage_$route", 0)
        prefs.edit { putInt("card_usage_$route", count + 1) }
    }

    fun getCardUsage(route: String): Int {
        return prefs.getInt("card_usage_$route", 0)
    }

    fun completeOnboarding() {
        prefs.edit { putBoolean("onboarding_completed", true) }
        _onboardingCompleted.value = true
    }

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit { putBoolean("is_dark_theme", isDark) }
        _isDarkThemeLegacy.value = isDark
        if (_themeMode.value == 0) {
            _isDarkTheme.value = isDark
        }
    }

    fun setThemeMode(mode: Int) {
        prefs.edit { putInt("theme_mode", mode) }
        _themeMode.value = mode
        _isDarkTheme.value = when (mode) {
            1 -> true
            2 -> false
            else -> (application.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun refreshSystemTheme() {
        if (_themeMode.value == 0) {
            _isDarkTheme.value = (application.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun setCustomBackgroundEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("custom_bg_enabled", enabled) }
        _isCustomBackgroundEnabled.value = enabled
    }

    fun setCustomBackgroundUri(uri: Uri) {
        prefs.edit { putString("custom_bg_uri", uri.toString()) }
        _customBackgroundUri.value = uri
    }

    fun setAccentColor(color: Color) {
        prefs.edit {
            putInt("accent_color", color.toArgb())
            putBoolean("is_adaptive_color", false)
        }
        _accentColor.value = color
        _isAdaptiveColor.value = false
    }

    fun setAdaptiveColor(enabled: Boolean) {
        prefs.edit { putBoolean("is_adaptive_color", enabled) }
        _isAdaptiveColor.value = enabled
    }

    private fun getSystemAccentColor(): Color {
        return try {
            val baseColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                val context = getApplication<Application>().applicationContext
                val colorInt = context.resources.getColor(android.R.color.system_accent1_500, context.theme)
                Color(colorInt)
            } else {
                OrangeAccent
            }


            lerp(baseColor, OrangeAccent, 0.35f)
        } catch (e: Exception) {
            OrangeAccent
        }
    }

    fun setFlipperFontEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("flipper_font_enabled", enabled) }
        _flipperFontEnabled.value = enabled
    }

    fun setFlipperFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.9f, 1.4f)
        prefs.edit { putFloat("flipper_font_scale", clamped) }
        _flipperFontScale.value = clamped
    }

    fun setUiScale(scale: Float) {
        val stepped = ((scale.coerceIn(0.8f, 1.2f) * 20f).roundToInt() / 20f).coerceIn(0.8f, 1.2f)
        prefs.edit { putFloat("ui_scale", stepped) }
        _uiScale.value = stepped
    }

    fun setFunctionsMenuViewType(type: Int) {
        prefs.edit { putInt("functions_menu_view_type", type) }
        _functionsMenuViewType.value = type
    }

    fun setFunctionsTileSize(size: Float) {
        val clamped = size.coerceIn(100f, 200f)
        prefs.edit { putFloat("functions_tile_size", clamped) }
        _functionsTileSize.value = clamped
    }

    fun setLiquidGlassEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("liquid_glass_enabled", false) }
        _liquidGlassEnabled.value = false
    }

    fun setLiquidGlassButtons(enabled: Boolean) {
        prefs.edit { putBoolean("liquid_glass_buttons", enabled) }
        _liquidGlassButtons.value = enabled
    }

    fun setLiquidGlassTopBars(enabled: Boolean) {
        prefs.edit { putBoolean("liquid_glass_topbars", enabled) }
        _liquidGlassTopBars.value = enabled
    }

    fun setLiquidGlassNav(enabled: Boolean) {
        prefs.edit { putBoolean("liquid_glass_nav", enabled) }
        _liquidGlassNav.value = enabled
    }

    fun setGlassNavEnabled(enabled: Boolean) = setLiquidGlassEnabled(enabled)

    fun setAnimatedBackgroundEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("animated_background_enabled", enabled) }
        _animatedBackgroundEnabled.value = enabled
    }

    fun setExpressiveEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("md3_expressive", enabled) }
        _expressiveEnabled.value = enabled
    }

    fun setAppLanguage(language: String) {
        val normalized = if (language == "en") "en" else "ru"
        prefs.edit { putString("app_language", normalized) }
        _appLanguage.value = normalized
    }

    fun setCyclicDolphinAnimationEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("cyclic_dolphin_animation_enabled", enabled) }
        _cyclicDolphinAnimationEnabled.value = enabled
    }

    fun setQuickStartupEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("quick_startup_enabled", enabled) }
        _quickStartupEnabled.value = enabled
    }

    fun setPerformanceModeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("performance_mode_enabled", enabled) }
        _performanceModeEnabled.value = enabled
    }

    fun maybeAutoEnablePerformanceMode() {
        if (prefs.getBoolean("performance_mode_auto_applied", false)) return
        prefs.edit { putBoolean("performance_mode_auto_applied", true) }
        if (_performanceModeEnabled.value) return
        val weakDevice = try {
            val am = application.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            am.isLowRamDevice || memoryInfo.totalMem < 4_500_000_000L
        } catch (e: Exception) {
            false
        }
        if (weakDevice) {
            setPerformanceModeEnabled(true)
            DolphyToast.show(application.getString(R.string.settings_performance_mode_auto_enabled))
        }
    }

    fun setFabDestinationRoute(route: String) {
        prefs.edit { putString("fab_destination_route", route) }
        _fabDestinationRoute.value = route
    }

    fun setFabEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("fab_enabled", enabled) }
        _fabEnabled.value = enabled
    }

    fun setHidTouchpadSensitivityMove(value: Float) {
        prefs.edit { putFloat("hid_touchpad_sensitivity_move", value) }
        _hidTouchpadSensitivityMove.value = value
    }

    fun setHidTouchpadSensitivityScroll(value: Float) {
        prefs.edit { putFloat("hid_touchpad_sensitivity_scroll", value) }
        _hidTouchpadSensitivityScroll.value = value
    }

    fun setNameSpoofingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("is_name_spoofing_enabled", enabled) }
        _isNameSpoofingEnabled.value = enabled

        if (enabled) {
            applyNameSpoofing(_spoofedName.value)
        }
    }

    private fun applyNameSpoofing(name: String) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                bluetoothAdapter?.name = name
                Log.d("SpamViewModel", "Name updated to: $name")
            } catch (e: SecurityException) {
                Log.e("SpamViewModel", "No permission for name change", e)
            } catch (e: Exception) {
                Log.e("SpamViewModel", "Failed to apply spoofing", e)
            }
        }
    }

    fun setSpoofedName(name: String) {
        prefs.edit { putString("spoofed_name", name) }
        _spoofedName.value = name
    }

    private val bluetoothManager: BluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser
    private val bleScanner: BluetoothLeScanner?
        get() = runCatching { bluetoothAdapter?.bluetoothLeScanner }.getOrNull()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices
    private val _isScanningDevices = MutableStateFlow(false)
    val isScanningDevices: StateFlow<Boolean> = _isScanningDevices

    private val _selectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val selectedDevice: StateFlow<BluetoothDevice?> = _selectedDevice

    private val _isSpamming = MutableStateFlow(false)
    val isSpamming: StateFlow<Boolean> = _isSpamming

    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName

    private val _sliderValue = MutableStateFlow(0f)
    val sliderValue: StateFlow<Float> = _sliderValue

    private var classicSpamRunnable: Runnable? = null

    private val _bleSpamStates = MutableStateFlow(mapOf("iPhone" to false, "Samsung" to false, "Windows" to false, "Xiaomi" to false))
    val bleSpamStates = _bleSpamStates.asStateFlow()

    private val _bleSpamming = MutableStateFlow(false)
    val bleSpamming: StateFlow<Boolean> = _bleSpamming

    val bleDelay: StateFlow<Int> = BleSpamRuntime.bleDelay
    val spammingStates: StateFlow<Map<Pair<SpamType, Any?>, Boolean>> = BleSpamRuntime.spammingStates
    val kitchenSinkActive: StateFlow<Boolean> = BleSpamRuntime.kitchenSinkActive

    private val _bleTabIndex = MutableStateFlow(0)
    val bleTabIndex: StateFlow<Int> = _bleTabIndex

    private val _advertisePresets = MutableStateFlow(loadAdvertisePresets())
    val advertisePresets: StateFlow<List<AdvertisePreset>> = _advertisePresets.asStateFlow()

    private val _activeAdvertisePresetId = MutableStateFlow<Long?>(null)
    val activeAdvertisePresetId: StateFlow<Long?> = _activeAdvertisePresetId.asStateFlow()

    private var customAdvertiseCallback: AdvertiseCallback? = null
    private var customAdvertiseLoopRunnable: Runnable? = null

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions

    private val _bluetoothEnabled = MutableStateFlow(false)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled

    private val handler = Handler(Looper.getMainLooper())
    private val advertiseCallback = object : AdvertiseCallback() {}

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _bluetoothEnabled.value = state == BluetoothAdapter.STATE_ON
            }
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { foundDevice ->
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
                        if (!_discoveredDevices.value.any { it.address == foundDevice.address }) {
                            _discoveredDevices.value += foundDevice
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanningDevices.value = false
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.device?.let { scannedDevice ->
                if (ActivityCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
                if (!_discoveredDevices.value.any { it.address == scannedDevice.address }) {
                    _discoveredDevices.value += scannedDevice
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { result ->
                result.device?.let { scannedDevice ->
                    if (ActivityCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@forEach
                    if (!_discoveredDevices.value.any { it.address == scannedDevice.address }) {
                        _discoveredDevices.value += scannedDevice
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanningDevices.value = false
        }
    }


    init {
        BleSpamRuntime.init(application)
        checkBluetoothState()
        application.registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        val discoveryFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(discoveryReceiver, discoveryFilter, Context.RECEIVER_EXPORTED)
        } else {
            application.registerReceiver(discoveryReceiver, discoveryFilter)
        }
    }

    private fun checkBluetoothState() {
        _bluetoothEnabled.value = runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false)
    }

    fun onPermissionsGranted() {
        val required = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        _hasPermissions.value = required.all { ContextCompat.checkSelfPermission(application, it) == PackageManager.PERMISSION_GRANTED }
        checkBluetoothState()
    }
    fun onPermissionsDenied() { _hasPermissions.value = false }
    fun onDeviceNameChanged(newName: String) { _deviceName.value = newName }
    fun onSliderValueChanged(newValue: Float) {
        val decision = PluginBluetoothHooks.action("bluetooth.all.interval.change", JSONObject().put("intervalMs", newValue))
        if (decision.cancelled || decision.handled) return
        _sliderValue.value = runCatching { JSONObject(decision.payloadJson).optDouble("intervalMs", newValue.toDouble()).toFloat() }
            .getOrDefault(newValue).coerceIn(0f, 2000f)
    }
    fun selectDevice(device: BluetoothDevice) {
        val decision = PluginBluetoothHooks.action(
            "bluetooth.all.device.select",
            JSONObject().put("address", device.address).put("name", (try { device.name } catch (_: SecurityException) { null })),
        )
        if (!decision.cancelled && !decision.handled) _selectedDevice.value = device
    }

    fun startDeviceScan() {
        val pluginDecision = PluginBluetoothHooks.action("bluetooth.all.scan.start")
        if (pluginDecision.cancelled || pluginDecision.handled) return
        if (!_hasPermissions.value || !_bluetoothEnabled.value) return
        if (ActivityCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        if (ActivityCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        if (bluetoothAdapter?.isDiscovering == true) {
            runCatching { bluetoothAdapter?.cancelDiscovery() }
        }
        try { bleScanner?.stopScan(bleScanCallback) } catch (_: SecurityException) { }

        _discoveredDevices.value = emptyList()
        runCatching {
            bluetoothAdapter?.bondedDevices?.forEach { bonded ->
                if (!_discoveredDevices.value.any { it.address == bonded.address }) {
                    _discoveredDevices.value += bonded
                }
            }
        }


        val classicStarted = bluetoothAdapter?.startDiscovery() == true


        bleScanner?.startScan(bleScanCallback)

        _isScanningDevices.value = classicStarted
    }

    fun startSpam() {
        val device = _selectedDevice.value
        val pluginDecision = PluginBluetoothHooks.action(
            "bluetooth.all.start",
            JSONObject().put("address", device?.address).put("name", runCatching { device?.name }.getOrNull()).put("intervalMs", _sliderValue.value),
        )
        if (pluginDecision.cancelled || pluginDecision.handled) return
        runCatching { JSONObject(pluginDecision.payloadJson).optDouble("intervalMs", _sliderValue.value.toDouble()).toFloat() }
            .getOrNull()?.let { _sliderValue.value = it.coerceIn(0f, 2000f) }
        if (!_hasPermissions.value || !_bluetoothEnabled.value || _selectedDevice.value == null) return
        if (ActivityCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        _selectedDevice.value?.let { device ->
            classicSpamRunnable = object : Runnable {
                override fun run() {
                    if (ActivityCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
                    device.createBond()
                    handler.postDelayed(this, _sliderValue.value.toLong().coerceAtLeast(50))
                }
            }
            handler.post(classicSpamRunnable!!)
            _isSpamming.value = true
        }
    }

    fun stopSpam() {
        val pluginDecision = PluginBluetoothHooks.action("bluetooth.all.stop", JSONObject().put("active", _isSpamming.value))
        if (pluginDecision.cancelled || pluginDecision.handled) return
        classicSpamRunnable?.let { handler.removeCallbacks(it) }
        _isSpamming.value = false
    }

    fun setBleDelay(delay: Int) {
        BleSpamRuntime.setBleDelay(delay)
    }

    fun setBleTabIndex(index: Int) {
        _bleTabIndex.value = index
    }

    fun addAdvertisePreset(
        name: String,
        companyCode: Int,
        payloadHex: String,
        randomizeMac: Boolean,
        intervalMs: Int
    ) {
        val decision = PluginBluetoothHooks.action(
            "bluetooth.advert.preset.add",
            JSONObject().put("name", name).put("companyCode", companyCode).put("payloadHex", payloadHex).put("randomizeMac", randomizeMac).put("intervalMs", intervalMs),
        )
        if (decision.cancelled || decision.handled) return
        val changed = runCatching { JSONObject(decision.payloadJson) }.getOrNull()
        val newPreset = AdvertisePreset(
            id = System.nanoTime(),
            name = changed?.optString("name", name) ?: name,
            companyCode = changed?.optInt("companyCode", companyCode) ?: companyCode,
            payloadHex = changed?.optString("payloadHex", payloadHex) ?: payloadHex,
            randomizeMac = changed?.optBoolean("randomizeMac", randomizeMac) ?: randomizeMac,
            intervalMs = (changed?.optInt("intervalMs", intervalMs) ?: intervalMs).coerceIn(200, 1000),
        )
        _advertisePresets.value = _advertisePresets.value + newPreset
        persistAdvertisePresets(_advertisePresets.value)
    }

    fun updateAdvertisePreset(updated: AdvertisePreset) {
        val decision = PluginBluetoothHooks.action("bluetooth.advert.preset.update", updated.toPluginJson())
        if (decision.cancelled || decision.handled) return
        val effective = runCatching { JSONObject(decision.payloadJson) }.getOrNull()?.toAdvertisePreset(updated) ?: updated
        _advertisePresets.value = _advertisePresets.value.map { current ->
            if (current.id == effective.id) effective else current
        }
        persistAdvertisePresets(_advertisePresets.value)
    }

    fun deleteAdvertisePreset(presetId: Long) {
        val decision = PluginBluetoothHooks.action("bluetooth.advert.preset.delete", JSONObject().put("id", presetId))
        if (decision.cancelled || decision.handled) return
        if (_activeAdvertisePresetId.value == presetId) {
            stopAdvertisePreset()
        }
        _advertisePresets.value = _advertisePresets.value.filterNot { it.id == presetId }
        persistAdvertisePresets(_advertisePresets.value)
    }

    fun startAdvertisePreset(presetId: Long): AdvertiseStartResult {
        val storedPreset = _advertisePresets.value.firstOrNull { it.id == presetId }
            ?: return AdvertiseStartResult.Error(application.getString(R.string.error_preset_not_found))
        val decision = PluginBluetoothHooks.action("bluetooth.advert.preset.start", storedPreset.toPluginJson())
        if (decision.cancelled) return AdvertiseStartResult.Error("Cancelled by plugin")
        if (decision.handled) {
            _activeAdvertisePresetId.value = storedPreset.id
            return AdvertiseStartResult.Started
        }
        val preset = runCatching { JSONObject(decision.payloadJson) }.getOrNull()?.toAdvertisePreset(storedPreset) ?: storedPreset

        if (_activeAdvertisePresetId.value != null) {
            return AdvertiseStartResult.Error(application.getString(R.string.error_advertising_already_running))
        }

        if (!hasAdvertisePermissions()) {
            return AdvertiseStartResult.PermissionRequired
        }

        if (bluetoothAdapter?.isEnabled != true) {
            return AdvertiseStartResult.Error(application.getString(R.string.error_enable_bluetooth_first))
        }

        if (bluetoothAdapter?.isMultipleAdvertisementSupported != true) {
            return AdvertiseStartResult.Error(application.getString(R.string.error_ble_advertising_unsupported))
        }

        val payloadBytes = try {
            HexUtils.hexToByteArray(preset.payloadHex)
        } catch (e: IllegalArgumentException) {
            return AdvertiseStartResult.Error(e.message ?: application.getString(R.string.error_invalid_hex_payload))
        }

        if (payloadBytes.size + 4 > 31) {
            return AdvertiseStartResult.Error(application.getString(R.string.error_payload_too_long_legacy))
        }

        val startError = startAdvertiseCycleOnce(preset, payloadBytes)
        if (startError != null) return AdvertiseStartResult.Error(startError)

        customAdvertiseLoopRunnable = object : Runnable {
            override fun run() {
                if (_activeAdvertisePresetId.value != preset.id) return
                stopCustomAdvertiseInternal(clearActive = false, clearLoop = false)
                val error = startAdvertiseCycleOnce(preset, payloadBytes)
                if (error != null) {
                    stopAdvertisePreset()
                    return
                }
                val jitter = if (preset.randomizeMac) (0..80).random() else 0
                handler.postDelayed(this, (preset.intervalMs + jitter).toLong())
            }
        }
        handler.postDelayed(customAdvertiseLoopRunnable!!, preset.intervalMs.toLong())
        return AdvertiseStartResult.Started
    }

    fun stopAdvertisePreset() {
        val decision = PluginBluetoothHooks.action("bluetooth.advert.preset.stop", JSONObject().put("id", _activeAdvertisePresetId.value))
        if (decision.cancelled) return
        if (decision.handled) {
            customAdvertiseLoopRunnable?.let { handler.removeCallbacks(it) }
            customAdvertiseLoopRunnable = null
            customAdvertiseCallback = null
            _activeAdvertisePresetId.value = null
            return
        }
        stopCustomAdvertiseInternal(clearActive = true)
    }

    private fun startAdvertiseCycleOnce(preset: AdvertisePreset, payloadBytes: ByteArray): String? {
        val bleAdvertiser = advertiser ?: return application.getString(R.string.error_advertiser_unavailable)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(preset.companyCode, payloadBytes)
            .build()
        val pluginDecision = PluginBluetoothHooks.interceptAdvertising(
            data,
            null,
            JSONObject().put("source", "advert").put("preset", preset.toPluginJson()),
        )
        if (pluginDecision.skipNative) {
            _activeAdvertisePresetId.value = preset.id
            return null
        }

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _activeAdvertisePresetId.value = preset.id
            }

            override fun onStartFailure(errorCode: Int) {
                _activeAdvertisePresetId.value = null
            }
        }

        customAdvertiseCallback = callback
        return try {
            bleAdvertiser.startAdvertising(settings, pluginDecision.advertiseData, pluginDecision.scanResponse, callback)
            null
        } catch (_: SecurityException) {
            application.getString(R.string.error_no_ble_advertise_permission)
        } catch (t: Throwable) {
            application.getString(R.string.error_advertising_start, t.message ?: "")
        }
    }

    private fun stopCustomAdvertiseInternal(clearActive: Boolean, clearLoop: Boolean = clearActive) {
        val pluginDecision = PluginBluetoothHooks.action(
            "bluetooth.advertising.stop",
            JSONObject().put("source", "advert").put("presetId", _activeAdvertisePresetId.value).put("clearActive", clearActive).put("clearLoop", clearLoop),
        )
        if (pluginDecision.cancelled) return
        if (clearLoop) {
            customAdvertiseLoopRunnable?.let { handler.removeCallbacks(it) }
            customAdvertiseLoopRunnable = null
        }
        if (!pluginDecision.handled) customAdvertiseCallback?.let { callback ->
            try {
                advertiser?.stopAdvertising(callback)
            } catch (_: SecurityException) {

            }
        }
        customAdvertiseCallback = null
        if (clearActive) {
            _activeAdvertisePresetId.value = null
        }
    }

    private fun hasAdvertisePermissions(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        return required.all {
            ActivityCompat.checkSelfPermission(application, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun loadAdvertisePresets(): List<AdvertisePreset> {
        val raw = prefs.getString("advertise_presets_json", "[]") ?: "[]"
        val array = JSONArray(raw)
        val result = mutableListOf<AdvertisePreset>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result += AdvertisePreset(
                id = item.optLong("id"),
                name = item.optString("name"),
                companyCode = item.optInt("companyCode"),
                payloadHex = item.optString("payloadHex"),
                randomizeMac = item.optBoolean("randomizeMac", true),
                intervalMs = item.optInt("intervalMs", 200)
            )
        }
        return result
    }

    private fun persistAdvertisePresets(items: List<AdvertisePreset>) {
        val array = JSONArray()
        items.forEach { preset ->
            array.put(
                JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("companyCode", preset.companyCode)
                    put("payloadHex", preset.payloadHex)
                    put("randomizeMac", preset.randomizeMac)
                    put("intervalMs", preset.intervalMs)
                }
            )
        }
        prefs.edit { putString("advertise_presets_json", array.toString()) }
    }

    fun toggleBleSpam(type: SpamType, subtype: Any?) {
        BleSpamRuntime.toggleBleSpam(type, subtype)
    }

    fun stopAllBleSpam() {
        BleSpamRuntime.stopAllBleSpam()
    }

    fun toggleKitchenSink() {
        BleSpamRuntime.toggleKitchenSink()
    }

    private fun getAdvertiseDataFor(brand: String): AdvertiseData {
        val builder = AdvertiseData.Builder().setIncludeDeviceName(false)
        when (brand) {
            "iPhone" -> {
                val manufacturerData = byteArrayOf(0x4C, 0x00, 0x02, 0x0F, 0x07, 0x19, 0x01, 0x07, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                builder.addManufacturerData(0x004C, manufacturerData.drop(2).toByteArray())
            }
            "Samsung" -> {
                builder.addServiceUuid(ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB"))
                builder.addServiceData(ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB"), byteArrayOf(0x01, 0x23, 0x45))
            }
            "Windows" -> {
                val manufacturerData = byteArrayOf(0x06, 0x00, 0x03, 0x00, 0x80.toByte())
                builder.addManufacturerData(0x0006, manufacturerData.drop(2).toByteArray())
            }
        }
        return builder.build()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            application.unregisterReceiver(discoveryReceiver)
            application.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {   }
        try { bleScanner?.stopScan(bleScanCallback) } catch (_: SecurityException) { }
        stopSpam()
        stopAllBleSpam()
        stopAdvertisePreset()
    }
}
