@file:OptIn(ExperimentalMaterial3Api::class)
package com.droid.dolphy.hid

import com.droid.dolphy.DolphyIconButton

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.CompositionLocalProvider
import com.droid.dolphy.AppLocale
import com.droid.dolphy.DolphyCircularProgressIndicator
import com.droid.dolphy.DolphyTheme
import com.droid.dolphy.LocalExpressiveEnabled
import com.droid.dolphy.LocalLiquidGlassBackdrop
import com.droid.dolphy.LocalLiquidGlassButtons
import com.droid.dolphy.LocalLiquidGlassEnabled
import com.droid.dolphy.LocalLiquidGlassNav
import com.droid.dolphy.LocalLiquidGlassTopBars
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import com.kyant.backdrop.backdrops.emptyBackdrop

class HidKeyboardActivity : ComponentActivity(), KeyboardService.Listener {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    companion object {
        private const val PREFS = "DolphyPrefs"
        private const val KEY_LAST_HID_ADDRESS = "hid_last_target_address"
        private const val KEY_LAST_HID_NAME = "hid_last_target_name"
        private const val KEY_LAST_HID_KIND = "hid_last_target_kind"
        private const val MAX_SYNC_RECONNECT_ATTEMPTS = 5
    }

    sealed class HidStatus {
        object Disconnected : HidStatus()
        object Connecting : HidStatus()
        data class Connected(val deviceName: String) : HidStatus()
    }

    enum class BtDeviceKind(@StringRes val titleRes: Int) {
        Computer(R.string.hid_device_pc),
        IPhone(R.string.hid_device_iphone),
        Phone(R.string.hid_device_phone),
        Tablet(R.string.hid_device_tablet),
        Headphones(R.string.hid_device_headphones),
        Tv(R.string.hid_device_tv),
        Wearable(R.string.hid_device_wearable),
        Other(R.string.hid_device_other)
    }
    enum class ControlScreenMode { Mobile, Pc, BadHid }
    data class BtDevice(val name: String, val address: String, val paired: Boolean, val kind: BtDeviceKind)

    private var keyboardService: KeyboardService? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var keyboardServiceBound = false
    private val hidConnectionState = mutableStateOf<Connection?>(null)
    private val capsLockState = mutableStateOf(false)
    private val hidStatus = mutableStateOf<HidStatus>(HidStatus.Disconnected)
    private val showKeyboardScreen = mutableStateOf(false)
    private val controlScreenMode = mutableStateOf(ControlScreenMode.Mobile)
    private val showFailedDialog = mutableStateOf(false)
    private val deviceMap = mutableStateOf<Map<String, BtDevice>>(emptyMap())
    private val isScanning = mutableStateOf(false)
    private var pendingDeviceAddress: String? = null
    private var pendingDeviceName: String? = null
    private var pendingDeviceKind: BtDeviceKind = BtDeviceKind.Other
    private var lastConnectedTimeMs = 0L
    private var autoNavigateOnConnect = false
    private var waitingForPcBond = false
    private var waitingForPcBondUntilMs = 0L
    private var activeDeviceAddress: String? = null
    private var activeDeviceKind: BtDeviceKind = BtDeviceKind.Other
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reconnectInProgress = false
    private var reconnectAttemptCount = 0
    private var userInitiatedSession = false
    private val settingsPrefs by lazy { getSharedPreferences("DolphyPrefs", Context.MODE_PRIVATE) }
    private val showReconnectBanner = mutableStateOf(false)
    private val showLongConnectingDialog = mutableStateOf(false)
    private val showBlueDuckyInfoDialog = mutableStateOf(false)
    private var originalBtName: String? = null
    private var lastConnectingToastAtMs = 0L

    private val syncTicker = object : Runnable {
        override fun run() {
            synchronizeWithService(allowAutoNavigate = false)
            mainHandler.postDelayed(this, 1000L)
        }
    }

    private val longConnectingRunnable = Runnable {
        if (!showKeyboardScreen.value && hidStatus.value is HidStatus.Connecting) {
            showLongConnectingDialog.value = true
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    d ?: return
                    val hasConnect = ActivityCompat.checkSelfPermission(this@HidKeyboardActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    if (!hasConnect) return
                    val name = runCatching { d.name ?: d.address }.getOrDefault(d.address)
                    val paired = runCatching { d.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
                    val kind = detectDeviceKind(d, name)
                    deviceMap.value = deviceMap.value.toMutableMap().apply { put(d.address, BtDevice(name, d.address, paired, kind)) }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    d ?: return

                    val target = pendingDeviceAddress
                    if (target != null && d.address == target && pendingDeviceKind == BtDeviceKind.Computer) {
                        val hasConnect = ActivityCompat.checkSelfPermission(
                            this@HidKeyboardActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasConnect) return
                        val bondState = runCatching { d.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
                        if (bondState == BluetoothDevice.BOND_BONDED) {
                            waitingForPcBond = false
                            waitingForPcBondUntilMs = 0L
                            hidStatus.value = HidStatus.Connecting
                            scheduleLongConnectingWarning()
                            keyboardService?.connectDirect(target)
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> isScanning.value = false
            }
        }
    }

    private val keyboardServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            keyboardService = (binder as? KeyboardService.LocalBinder)?.getService()
            keyboardServiceBound = true
            keyboardService?.setListener(this@HidKeyboardActivity)
            if (!showKeyboardScreen.value && hidStatus.value !is HidStatus.Connected && !userInitiatedSession) {
                keyboardService?.clearPendingDeviceAddress()
            }
            keyboardService?.initialize()
            synchronizeWithService(allowAutoNavigate = true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            keyboardService = null
            keyboardServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        restoreLastTarget()
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        bluetoothAdapter = adapter
        if (adapter == null) {
            Toast.makeText(this, getString(R.string.bluetooth_not_supported), Toast.LENGTH_SHORT).show()
            finish()
            return
        }


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            originalBtName = adapter.name
        }

        registerReceiver(discoveryReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        })
        bindKeyboardService()

        if (!adapter.isEnabled) {
            val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
            launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }

        val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) {
                keyboardService?.initialize(); loadPairedDevices(adapter); startDiscovery(adapter)
            }
        }

        val required = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.BLUETOOTH_SCAN) }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_COARSE_LOCATION)
            else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = required.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray()) else {
            keyboardService?.initialize(); loadPairedDevices(adapter); startDiscovery(adapter)
        }

        val accentColor = Color(settingsPrefs.getInt("accent_color", 0xFFFF9800.toInt()))
        val isAdaptiveColor = settingsPrefs.getBoolean("is_adaptive_color", true)
        val flipperFontEnabled = settingsPrefs.getBoolean("flipper_font_enabled", true)
        val flipperFontScale = settingsPrefs.getFloat("flipper_font_scale", 1.08f)
        val uiScale = settingsPrefs.getFloat("ui_scale", 1f).coerceIn(0.8f, 1.2f)
        val animatedBackgroundEnabled = settingsPrefs.getBoolean("animated_background_enabled", false)
        val expressiveEnabled = settingsPrefs.getBoolean("md3_expressive", false)
        val themeMode = settingsPrefs.getInt("theme_mode", 0)
        val isDarkTheme = when (themeMode) {
            1 -> true
            2 -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        setContent {
            val accentColorInt by remember { mutableStateOf(settingsPrefs.getInt("accent_color", 0xFFFF9800.toInt())) }
            val isAdaptiveColor by remember { mutableStateOf(settingsPrefs.getBoolean("is_adaptive_color", true)) }

            val accentColor = Color(accentColorInt)

            val liquidGlassEnabled = false
            val liquidButtons = remember { settingsPrefs.getBoolean("liquid_glass_buttons", true) }
            val liquidTopBars = remember { settingsPrefs.getBoolean("liquid_glass_topbars", true) }
            val liquidNav = remember { settingsPrefs.getBoolean("liquid_glass_nav", true) }
            val controlsBackdrop = remember(liquidGlassEnabled) {
                if (liquidGlassEnabled) emptyBackdrop() else null
            }

            DolphyTheme(
                darkTheme = isDarkTheme,
                accentColor = accentColor,
                isAdaptiveColor = isAdaptiveColor,
                useFlipperFont = flipperFontEnabled,
                flipperFontScale = flipperFontScale,
                uiScale = uiScale,
                animatedBackgroundEnabled = animatedBackgroundEnabled,
                expressiveEnabled = expressiveEnabled
            ) {
                CompositionLocalProvider(
                    LocalLiquidGlassEnabled provides liquidGlassEnabled,
                    LocalLiquidGlassButtons provides liquidButtons,
                    LocalLiquidGlassTopBars provides liquidTopBars,
                    LocalLiquidGlassNav provides liquidNav,
                    LocalLiquidGlassBackdrop provides controlsBackdrop,
                    LocalExpressiveEnabled provides expressiveEnabled,
                ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val status = hidStatus.value
                    val showKeyboard = showKeyboardScreen.value
                    val activeConn = hidConnectionState.value
                    val scanning = isScanning.value
                    val failedDialog = showFailedDialog.value
                    val devices = deviceMap.value.values.toList().sortedWith(compareByDescending<BtDevice> { it.paired }.thenBy { it.name })
                    var selectedAddress by remember { mutableStateOf<String?>(null) }
                    var selectedName by remember { mutableStateOf<String?>(null) }

                    BackHandler(enabled = showKeyboard) {
                        if (controlScreenMode.value == ControlScreenMode.BadHid) {
                            controlScreenMode.value = ControlScreenMode.Mobile
                        } else {
                            disconnectAndExitToOther()
                        }
                    }

                    if (showKeyboard) {
                        if (activeConn != null) {
                            if (controlScreenMode.value == ControlScreenMode.Pc) {
                                Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                                    PcHidWorkspaceScreen(activeConn, capsLockState.value, onBack = { disconnectAndExitToOther() })
                                }
                            } else {
                                Scaffold(
                                    topBar = {
                                        SectionTopBar(
                                            title = stringResource(if (controlScreenMode.value == ControlScreenMode.BadHid) R.string.hid_bad_hid_mode else R.string.hid_title),
                                            onBack = {
                                                if (controlScreenMode.value == ControlScreenMode.BadHid) {
                                                    controlScreenMode.value = ControlScreenMode.Mobile
                                                } else {
                                                    disconnectAndExitToOther()
                                                }
                                            },
                                            transparent = true
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) { pv ->
                                    Box(Modifier.fillMaxSize().padding(pv)) {
                                        HidMainScreen(
                                            activeConn,
                                            capsLockState.value,
                                            accentColor = accentColor,
                                            isBadHidMode = controlScreenMode.value == ControlScreenMode.BadHid,
                                            onModeChange = { isBad: Boolean ->
                                                controlScreenMode.value = if (isBad) ControlScreenMode.BadHid else ControlScreenMode.Mobile
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            Scaffold(
                                topBar = {
                                    SectionTopBar(
                                        title = stringResource(R.string.hid_title),
                                            onBack = { disconnectAndExitToOther() },
                                        transparent = true
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            ) { pv ->
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(pv).padding(16.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    DolphyCircularProgressIndicator()
                                    Spacer(Modifier.height(12.dp))
                                    Text(stringResource(R.string.hid_reconnecting), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.hid_connection_lost_reconnecting), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        return@Surface
                    }

                    Scaffold(
                        topBar = {
                            SectionTopBar(
                                title = stringResource(R.string.hid_title),
                                onBack = { finish() },
                                transparent = true,
                                actions = {
                                    DolphyIconButton(onClick = { startDiscovery(adapter) }) {
                                        if (scanning) DolphyCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        else Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.hid_scan))
                                    }
                                }
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { pv ->
                        Column(
                            modifier = Modifier.fillMaxSize().padding(pv).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val (statusText, statusColor) = when (status) {
                                HidStatus.Disconnected -> stringResource(R.string.hid_not_connected) to MaterialTheme.colorScheme.error
                                HidStatus.Connecting -> stringResource(R.string.hid_connecting) to MaterialTheme.colorScheme.secondary
                                is HidStatus.Connected -> stringResource(R.string.hid_connected, status.deviceName) to Color(0xFF4CAF50)
                            }
                            Text(statusText, color = statusColor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            if (showReconnectBanner.value) {
                                Text(stringResource(R.string.hid_connecting), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.hid_devices), style = MaterialTheme.typography.titleMedium)
                                if (scanning) { Row(verticalAlignment = Alignment.CenterVertically) { DolphyCircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.hid_scanning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            }

                            if (devices.isEmpty()) {
                                Text(if (scanning) stringResource(R.string.hid_scanning_message) else stringResource(R.string.hid_no_devices), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(devices, key = { it.address }) { d ->
                                        val kindTitle = stringResource(d.kind.titleRes)
                                        val pairedLabel = stringResource(R.string.hid_paired)
                                        val isSelected = d.address == selectedAddress
                                        Card(
                                            modifier = Modifier.fillMaxWidth().clickable { selectedAddress = d.address; selectedName = d.name }.border(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                RoundedCornerShape(12.dp)
                                            ),
                                            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(d.kind.icon(!d.paired), contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    Text(d.name, fontWeight = FontWeight.Medium, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                                    Text(
                                                        buildString {
                                                            append(kindTitle)
                                                            append(" • ")
                                                            append(d.address)
                                                            if (d.paired) {
                                                                append(" • ")
                                                                append(pairedLabel)
                                                            }
                                                        },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val selectedDevice = devices.find { it.address == selectedAddress }
                            val selectedKind = selectedDevice?.kind ?: BtDeviceKind.Other
                            val isComputerSelected = selectedKind == BtDeviceKind.Computer
                            val connectedKind = if (status is HidStatus.Connected) {
                                if (activeDeviceKind == BtDeviceKind.Other) pendingDeviceKind else activeDeviceKind
                            } else {
                                selectedKind
                            }
                            val openPcControls = connectedKind == BtDeviceKind.Computer
                            LaunchedEffect(status, openPcControls, showKeyboard) {
                                if (status is HidStatus.Connected && !showKeyboard) {
                                    controlScreenMode.value = if (openPcControls) ControlScreenMode.Pc else ControlScreenMode.Mobile
                                    showKeyboardScreen.value = true
                                    cancelLongConnectingWarning()
                                }
                            }

                            com.droid.dolphy.AccentButton(
                                onClick = {
                                    val addr = selectedAddress ?: return@AccentButton
                                    val isPaired = selectedDevice?.paired == true
                                    stopDiscoveryIfRunning()
                                    saveLastTarget(addr, selectedName ?: addr, selectedKind)
                                    userInitiatedSession = true
                                    reconnectInProgress = false
                                    reconnectAttemptCount = 0
                                    controlScreenMode.value = if (isComputerSelected) ControlScreenMode.Pc else ControlScreenMode.Mobile
                                    hidStatus.value = HidStatus.Connecting
                                    scheduleLongConnectingWarning()
                                    if (isPaired) {
                                        autoNavigateOnConnect = true
                                        waitingForPcBond = false
                                        waitingForPcBondUntilMs = 0L
                                        keyboardService?.connectDirect(addr)
                                    } else {
                                        autoNavigateOnConnect = isComputerSelected
                                        waitingForPcBond = isComputerSelected
                                        waitingForPcBondUntilMs = if (isComputerSelected) {
                                            System.currentTimeMillis() + 30_000L
                                        } else {
                                            0L
                                        }
                                        keyboardService?.setTargetDeviceAddress(addr)
                                    }
                                },
                                enabled = selectedAddress != null && status !is HidStatus.Connecting,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) { Text(stringResource(R.string.hid_connect)) }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                com.droid.dolphy.AccentButton(
                                    onClick = {
                                        val addr = selectedAddress ?: return@AccentButton
                                        stopDiscoveryIfRunning()
                                        saveLastTarget(addr, selectedName ?: addr, selectedKind)
                                        userInitiatedSession = true
                                        reconnectInProgress = false
                                        reconnectAttemptCount = 0
                                        controlScreenMode.value = if (isComputerSelected) ControlScreenMode.Pc else ControlScreenMode.Mobile
                                        hidStatus.value = HidStatus.Connecting
                                        scheduleLongConnectingWarning()
                                        autoNavigateOnConnect = true
                                        waitingForPcBond = false
                                        waitingForPcBondUntilMs = 0L
                                        Toast.makeText(
                                            this@HidKeyboardActivity,
                                            getString(R.string.hid_quiet_connect_toast),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        keyboardService?.connectSilentBlueDucky(addr)
                                    },
                                    enabled = selectedAddress != null && status !is HidStatus.Connecting,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.tertiary,
                                    ),
                                ) {
                                    Text(stringResource(R.string.hid_quiet_connect))
                                }
                                Surface(
                                    onClick = { showBlueDuckyInfoDialog.value = true },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 2.dp, end = 2.dp)
                                        .size(22.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    tonalElevation = 2.dp,
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "BlueDucky info",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showBlueDuckyInfoDialog.value) {
                        AlertDialog(
                            onDismissRequest = { showBlueDuckyInfoDialog.value = false },
                            title = {
                                Text(
                                    stringResource(R.string.hid_blueducky_info_title),
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            text = {
                                Text(stringResource(R.string.hid_blueducky_info_body))
                            },
                            confirmButton = {
                                com.droid.dolphy.AccentButton(onClick = { showBlueDuckyInfoDialog.value = false }) {
                                    Text(stringResource(R.string.got_it))
                                }
                            },
                        )
                    }

                    if (failedDialog) {
                        val dn = pendingDeviceName ?: getString(R.string.hid_device_lower)
                        AlertDialog(
                            onDismissRequest = { showFailedDialog.value = false },
                            title = { Text(stringResource(R.string.hid_reconnect_title)) },
                            text = { Text(stringResource(R.string.hid_reconnect_message, dn)) },
                            confirmButton = {
                                com.droid.dolphy.AccentButton(onClick = {
                                    showFailedDialog.value = false
                                    val addr = pendingDeviceAddress ?: return@AccentButton
                                    userInitiatedSession = true
                                    hidStatus.value = HidStatus.Connecting
                                    scheduleLongConnectingWarning()
                                    keyboardService?.setTargetDeviceAddress(addr)
                                }) { Text(stringResource(R.string.retry)) }
                            },
                            dismissButton = {
                                com.droid.dolphy.AccentButton(onClick = {
                                    showFailedDialog.value = false
                                    pendingDeviceAddress?.let { keyboardService?.removeBond(it) }
                                    hidStatus.value = HidStatus.Disconnected
                                }) { Text(stringResource(R.string.cancel)) }
                            }
                        )
                    }

                    if (showLongConnectingDialog.value) {
                        AlertDialog(
                            onDismissRequest = { showLongConnectingDialog.value = false },
                            title = { Text(stringResource(R.string.hid_long_connection_title)) },
                            text = { Text(stringResource(R.string.hid_long_connection_message)) },
                            confirmButton = {
                                com.droid.dolphy.AccentButton(onClick = { showLongConnectingDialog.value = false }) {
                                    Text(stringResource(R.string.got_it))
                                }
                            }
                        )
                    }
                }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRealtimeSync()
        cancelLongConnectingWarning()
        runCatching { unregisterReceiver(discoveryReceiver) }


        if (originalBtName != null && (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S)) {
            bluetoothAdapter?.name = originalBtName
        }

        if (keyboardServiceBound) {
            keyboardService?.setListener(null)
            unbindService(keyboardServiceConnection)
            keyboardServiceBound = false
            keyboardService = null
        }
    }

    override fun onResume() {
        super.onResume()
        startRealtimeSync()
        synchronizeWithService(allowAutoNavigate = true)
    }

    override fun onPause() {
        super.onPause()
        stopRealtimeSync()
    }

    override fun onConnection(connection: Connection) { hidConnectionState.value = connection }
    override fun onCapsLock(enabled: Boolean) { capsLockState.value = enabled }

    override fun onHidStateChanged(device: BluetoothDevice, state: Int) {
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                stopDiscoveryIfRunning()
                waitingForPcBond = false
                waitingForPcBondUntilMs = 0L
                reconnectInProgress = false
                reconnectAttemptCount = 0
                val hasConnect = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                val name = if (hasConnect) runCatching { device.name ?: device.address }.getOrDefault(device.address) else device.address
                val kind = detectDeviceKind(device, name)
                activeDeviceAddress = device.address
                activeDeviceKind = kind
                saveLastTarget(device.address, name, kind)
                lastConnectedTimeMs = System.currentTimeMillis()
                userInitiatedSession = true
                hidStatus.value = HidStatus.Connected(name)
                showFailedDialog.value = false
                showReconnectBanner.value = false
                cancelLongConnectingWarning()
                if (kind == BtDeviceKind.Computer) {
                    controlScreenMode.value = ControlScreenMode.Pc
                }
                if (autoNavigateOnConnect) {
                    autoNavigateOnConnect = false
                    controlScreenMode.value = if (kind == BtDeviceKind.Computer) ControlScreenMode.Pc else ControlScreenMode.Mobile
                    showKeyboardScreen.value = true
                }
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                val expectedAddress = activeDeviceAddress ?: pendingDeviceAddress
                if (expectedAddress != null && device.address != expectedAddress) {
                    return
                }
                val msSinceConnect = System.currentTimeMillis() - lastConnectedTimeMs
                if (msSinceConnect < 1500L && hidStatus.value is HidStatus.Connected) return

                val targetAddress = pendingDeviceAddress ?: activeDeviceAddress
                val keyboardSessionActive =
                    targetAddress != null &&
                        userInitiatedSession &&
                        (showKeyboardScreen.value || hidStatus.value is HidStatus.Connected || hidStatus.value is HidStatus.Connecting)
                val shouldAutoReconnect =
                    targetAddress != null &&
                        keyboardSessionActive
                if (shouldAutoReconnect) {
                    hidStatus.value = HidStatus.Connecting
                    showReconnectBanner.value = true
                    showConnectingToastIfNeeded()
                    if (!reconnectInProgress) {
                        reconnectInProgress = true
                        reconnectAttemptCount += 1
                        val delayMs = (650L + reconnectAttemptCount * 120L).coerceAtMost(1800L)
                        mainHandler.postDelayed({
                            reconnectInProgress = false
                            targetAddress.let { keyboardService?.connectDirect(it) }
                        }, delayMs)
                    }
                    return
                }

                if (waitingForPcBond && pendingDeviceKind == BtDeviceKind.Computer) {
                    val now = System.currentTimeMillis()
                    val hasConnect = ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                    val bondState = if (hasConnect) {
                        runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
                    } else {
                        BluetoothDevice.BOND_NONE
                    }
                    if (bondState == BluetoothDevice.BOND_BONDED && pendingDeviceAddress != null) {
                        hidStatus.value = HidStatus.Connecting
                        showReconnectBanner.value = true
                        keyboardService?.connectDirect(pendingDeviceAddress!!)
                        return
                    }
                    val stillWaitingUserConfirm = bondState == BluetoothDevice.BOND_BONDING || now < waitingForPcBondUntilMs
                    if (stillWaitingUserConfirm) {
                        hidStatus.value = HidStatus.Connecting
                        showReconnectBanner.value = true
                        return
                    }
                    waitingForPcBond = false
                    waitingForPcBondUntilMs = 0L
                }

                val wasConnecting = hidStatus.value is HidStatus.Connecting
                hidStatus.value = HidStatus.Disconnected
                showReconnectBanner.value = false
                cancelLongConnectingWarning()
                activeDeviceAddress = null
                activeDeviceKind = BtDeviceKind.Other
                hidConnectionState.value = null
                if (showKeyboardScreen.value && pendingDeviceAddress == null) {
                    showKeyboardScreen.value = false
                }
                if (wasConnecting) {
                    val isPaired = runCatching {
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                            device.bondState == BluetoothDevice.BOND_BONDED
                    }.getOrDefault(false)
                    if (!isPaired) showFailedDialog.value = true
                }
                if (!showKeyboardScreen.value) {
                    userInitiatedSession = false
                }
            }
            BluetoothProfile.STATE_CONNECTING -> {
                hidStatus.value = HidStatus.Connecting
                if (showKeyboardScreen.value) {
                    showReconnectBanner.value = true
                    showConnectingToastIfNeeded()
                } else {
                    scheduleLongConnectingWarning()
                }
            }
            BluetoothProfile.STATE_DISCONNECTING -> Unit
        }
    }

    private fun loadPairedDevices(adapter: BluetoothAdapter) {
        val hasConnect = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!hasConnect) return
        val current = deviceMap.value.toMutableMap()
        adapter.bondedDevices?.forEach { d ->
            val name = runCatching { d.name ?: d.address }.getOrDefault(d.address)
            current[d.address] = BtDevice(name, d.address, true, detectDeviceKind(d, name))
        }
        deviceMap.value = current
    }

    private fun startDiscovery(adapter: BluetoothAdapter) {
        val hasScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else true
        if (!hasScan) return
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        if (adapter.startDiscovery()) isScanning.value = true
    }

    private fun bindKeyboardService() {
        val intent = Intent(this, KeyboardService::class.java)
        runCatching {
            bindService(intent, keyboardServiceConnection, Context.BIND_AUTO_CREATE)
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun stopDiscoveryIfRunning() {
        val adapter = bluetoothAdapter ?: return
        val hasScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else true
        if (!hasScan) return
        if (adapter.isDiscovering) {
            runCatching { adapter.cancelDiscovery() }
            isScanning.value = false
        }
    }

    private fun startRealtimeSync() {
        mainHandler.removeCallbacks(syncTicker)
        mainHandler.post(syncTicker)
    }

    private fun stopRealtimeSync() {
        mainHandler.removeCallbacks(syncTicker)
    }

    private fun scheduleLongConnectingWarning() {
        mainHandler.removeCallbacks(longConnectingRunnable)
        if (!showKeyboardScreen.value && hidStatus.value is HidStatus.Connecting) {
            mainHandler.postDelayed(longConnectingRunnable, 10_000L)
        }
    }

    private fun cancelLongConnectingWarning() {
        mainHandler.removeCallbacks(longConnectingRunnable)
        showLongConnectingDialog.value = false
    }

    private fun disconnectAndExitToOther() {
        runCatching { keyboardService?.disconnect() }
        runCatching { keyboardService?.clearPendingDeviceAddress() }
        hidStatus.value = HidStatus.Disconnected
        hidConnectionState.value = null
        showKeyboardScreen.value = false
        userInitiatedSession = false
        reconnectInProgress = false
        reconnectAttemptCount = 0
        waitingForPcBond = false
        waitingForPcBondUntilMs = 0L
        cancelLongConnectingWarning()
        finish()
    }

    private fun showConnectingToastIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastConnectingToastAtMs >= 1800L) {
            lastConnectingToastAtMs = now
            Toast.makeText(this, getString(R.string.hid_connecting), Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreLastTarget() {
        val savedAddress = settingsPrefs.getString(KEY_LAST_HID_ADDRESS, null)
        val savedKindName = settingsPrefs.getString(KEY_LAST_HID_KIND, BtDeviceKind.Other.name)
        pendingDeviceAddress = savedAddress
        pendingDeviceName = settingsPrefs.getString(KEY_LAST_HID_NAME, null)
        pendingDeviceKind = runCatching { BtDeviceKind.valueOf(savedKindName ?: BtDeviceKind.Other.name) }
            .getOrDefault(BtDeviceKind.Other)
        if (pendingDeviceKind == BtDeviceKind.Computer) {
            controlScreenMode.value = ControlScreenMode.Pc
            activeDeviceKind = BtDeviceKind.Computer
        }
    }

    private fun saveLastTarget(address: String, name: String?, kind: BtDeviceKind) {
        pendingDeviceAddress = address
        pendingDeviceName = name
        pendingDeviceKind = kind
        settingsPrefs.edit()
            .putString(KEY_LAST_HID_ADDRESS, address)
            .putString(KEY_LAST_HID_NAME, name)
            .putString(KEY_LAST_HID_KIND, kind.name)
            .apply()
    }

    private fun synchronizeWithService(allowAutoNavigate: Boolean) {
        val service = keyboardService ?: return
        if (pendingDeviceAddress == null) {
            pendingDeviceAddress = service.getPendingDeviceAddress()
        }

        val now = System.currentTimeMillis()
        if (waitingForPcBond && pendingDeviceKind == BtDeviceKind.Computer) {
            if (now > waitingForPcBondUntilMs) {
                waitingForPcBond = false
                waitingForPcBondUntilMs = 0L
                if (hidStatus.value is HidStatus.Connecting) {
                    hidStatus.value = HidStatus.Disconnected
                    showReconnectBanner.value = false
                    showFailedDialog.value = true
                }
            }
        }

        when (service.getCurrentConnectionState()) {
            BluetoothProfile.STATE_CONNECTED -> {
                service.getCurrentConnection()?.let { hidConnectionState.value = it }
                val device = service.getConnectedDevice() ?: return
                val hasConnect = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                val name = if (hasConnect) runCatching { device.name ?: device.address }.getOrDefault(device.address) else device.address
                val kind = detectDeviceKind(device, name)
                activeDeviceAddress = device.address
                activeDeviceKind = kind
                reconnectInProgress = false
                reconnectAttemptCount = 0
                hidStatus.value = HidStatus.Connected(name)
                showReconnectBanner.value = false
                saveLastTarget(device.address, name, kind)
                if (kind == BtDeviceKind.Computer) {
                    controlScreenMode.value = ControlScreenMode.Pc
                    if (allowAutoNavigate) {
                        showKeyboardScreen.value = true
                    }
                }
            }
            BluetoothProfile.STATE_CONNECTING -> {
                if (userInitiatedSession || showKeyboardScreen.value) {
                    hidStatus.value = HidStatus.Connecting
                    showReconnectBanner.value = true
                    showConnectingToastIfNeeded()
                } else {
                    hidStatus.value = HidStatus.Disconnected
                    showReconnectBanner.value = false
                }
            }
            else -> {
                val targetAddress = pendingDeviceAddress
                val isPcBonding = waitingForPcBond && pendingDeviceKind == BtDeviceKind.Computer
                val shouldAutoReconnect =
                    targetAddress != null &&
                        userInitiatedSession &&
                        (showKeyboardScreen.value || hidStatus.value is HidStatus.Connecting || hidStatus.value is HidStatus.Connected || isPcBonding)
                if (shouldAutoReconnect) {
                    hidStatus.value = HidStatus.Connecting
                    showReconnectBanner.value = true
                    showConnectingToastIfNeeded()

                    val maxAttempts = if (isPcBonding) 50 else MAX_SYNC_RECONNECT_ATTEMPTS

                    if (!reconnectInProgress && reconnectAttemptCount < maxAttempts) {
                        reconnectInProgress = true
                        reconnectAttemptCount += 1
                        val delayMs = (550L + reconnectAttemptCount * 140L).coerceAtMost(if (isPcBonding) 2500L else 1800L)
                        mainHandler.postDelayed({
                            reconnectInProgress = false
                            targetAddress?.let { service.connectDirect(it) }
                        }, delayMs)
                    } else if (reconnectAttemptCount >= maxAttempts) {
                        hidStatus.value = HidStatus.Disconnected
                        showReconnectBanner.value = false
                        userInitiatedSession = false
                        waitingForPcBond = false
                        waitingForPcBondUntilMs = 0L
                    }
                } else if (hidStatus.value is HidStatus.Connected) {
                    hidStatus.value = HidStatus.Disconnected
                    showReconnectBanner.value = false
                    hidConnectionState.value = null
                    activeDeviceAddress = null
                    activeDeviceKind = BtDeviceKind.Other
                    showKeyboardScreen.value = false
                }
            }
        }
    }

    private fun detectDeviceKind(device: BluetoothDevice, nameHint: String): BtDeviceKind {
        val name = nameHint.lowercase()
        val devClass = device.bluetoothClass?.deviceClass
        val major = device.bluetoothClass?.majorDeviceClass
        if (major == BluetoothClass.Device.Major.COMPUTER) return BtDeviceKind.Computer
        if ("iphone" in name) return BtDeviceKind.IPhone
        if (
            "airpods" in name ||
            "earbuds" in name ||
            "buds" in name ||
            "headphones" in name ||
            "headset" in name ||
            "beats" in name
        ) return BtDeviceKind.Headphones
        if (major == BluetoothClass.Device.Major.PHONE) return BtDeviceKind.Phone
        if (major == BluetoothClass.Device.Major.AUDIO_VIDEO && devClass != null) {
            if (
                devClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
            ) {
                return BtDeviceKind.Headphones
            }
            if (
                devClass == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CONFERENCING
            ) {
                return BtDeviceKind.Tv
            }
        }
        if (major == BluetoothClass.Device.Major.WEARABLE) return BtDeviceKind.Wearable
        if (major == BluetoothClass.Device.Major.AUDIO_VIDEO) return BtDeviceKind.Tv
        return when {
            "pc" in name || "desktop" in name || "laptop" in name || "windows" in name || "macbook" in name || "imac" in name || "notebook" in name -> BtDeviceKind.Computer
            "iphone" in name || "ios" in name -> BtDeviceKind.IPhone
            "tablet" in name || "ipad" in name || "tab" in name -> BtDeviceKind.Tablet
            "phone" in name || "iphone" in name || "android" in name || "pixel" in name -> BtDeviceKind.Phone
            "airpods" in name || "earbuds" in name || "buds" in name || "headphones" in name || "headset" in name || "beats" in name || "sony wh" in name || "jbl tune" in name -> BtDeviceKind.Headphones
            "tv" in name || "smart tv" in name || "android tv" in name -> BtDeviceKind.Tv
            "watch" in name || "band" in name -> BtDeviceKind.Wearable
            else -> BtDeviceKind.Other
        }
    }

    private fun BtDeviceKind.icon(isSearching: Boolean): ImageVector = when (this) {
        BtDeviceKind.Computer -> Icons.Default.Computer
        BtDeviceKind.IPhone -> Icons.Default.PhoneIphone
        BtDeviceKind.Phone -> Icons.Default.PhoneAndroid
        BtDeviceKind.Tablet -> Icons.Default.TabletAndroid
        BtDeviceKind.Headphones -> Icons.Default.Headset
        BtDeviceKind.Tv -> Icons.Default.Tv
        BtDeviceKind.Wearable -> Icons.Default.Watch
        BtDeviceKind.Other -> if (isSearching) Icons.Default.BluetoothSearching else Icons.Default.DevicesOther
    }
}

