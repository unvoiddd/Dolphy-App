package com.droid.dolphy.bluetooth.audio

import com.droid.dolphy.DolphyIconButton

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class StressDeviceUi(
    val name: String,
    val address: String,
    val rssi: Int,
    val source: String = "SCAN"
)

object BluetoothStressBus {
    private val _running = MutableStateFlow(false)
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    private val _targetName = MutableStateFlow("")
    private val _targetAddress = MutableStateFlow("")
    private val _targetRssi = MutableStateFlow(Int.MIN_VALUE)

    val running: StateFlow<Boolean> = _running.asStateFlow()
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    val targetName: StateFlow<String> = _targetName.asStateFlow()
    val targetAddress: StateFlow<String> = _targetAddress.asStateFlow()
    val targetRssi: StateFlow<Int> = _targetRssi.asStateFlow()

    fun setRunning(value: Boolean) {
        _running.value = value
    }

    fun setTarget(name: String, address: String, rssi: Int) {
        _targetName.value = name
        _targetAddress.value = address
        _targetRssi.value = rssi
    }

    fun updateRssi(rssi: Int) {
        _targetRssi.value = rssi
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun appendLog(line: String) {
        val next = (_logs.value + line).takeLast(250)
        _logs.value = next
    }
}

class BluetoothStressService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var l2capJob: Job? = null
    private var gattJob: Job? = null
    private var scanner: BluetoothLeScanner? = null
    private val loopsCounter = AtomicLong(0)
    private var targetAddress: String = ""
    private var lastL2capError: String = ""
    private var lastGattError: String = ""
    private var l2capErrorRepeat = 0
    private var gattErrorRepeat = 0
    private var l2capBackoffMs = 140L
    private var l2capDisabledForSession = false

    private val gattCallback = object : BluetoothGattCallback() {}

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.address.equals(targetAddress, ignoreCase = true)) {
                BluetoothStressBus.updateRssi(result.rssi)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAll()
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
                val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
                val rssi = intent.getIntExtra(EXTRA_RSSI, Int.MIN_VALUE)
                startCombo(address, name, rssi)
            }
            ACTION_STOP -> {
                stopAll()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun normalizeErrorMessage(raw: String): String {
        return raw
            .replace("socket might closed", "socket might be closed")
            .replace("read ret:-1", "read ret: -1")
            .replace("timeout, read", "timeout; read")
            .trim()
    }

    private fun isLikelyUnsupportedL2cap(message: String): Boolean {
        val m = message.lowercase()
        return m.contains("read ret: -1") ||
            m.contains("socket might be closed") ||
            m.contains("timeout")
    }

    private fun startCombo(address: String, name: String, rssi: Int) {
        stopAll()
        targetAddress = address
        BluetoothStressBus.clearLogs()
        BluetoothStressBus.setTarget(name = name, address = address, rssi = rssi)
        BluetoothStressBus.setRunning(true)
        BluetoothStressBus.appendLog("START Combo for $address")
        l2capBackoffMs = 140L
        l2capDisabledForSession = false
        lastL2capError = ""
        l2capErrorRepeat = 0
        lastGattError = ""
        gattErrorRepeat = 0

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (adapter == null || device == null) {
            BluetoothStressBus.appendLog("Bluetooth adapter/device unavailable")
            BluetoothStressBus.setRunning(false)
            return
        }

        startLowLatencyScan(adapter)

        l2capJob = scope.launch {
            while (true) {
                if (l2capDisabledForSession) {
                    delay(1200)
                    continue
                }
                try {
                    val socket = device.createL2capChannel(0x1001)
                    socket.connect()
                    socket.close()
                    logBurst("L2CAP connect+close")
                    lastL2capError = ""
                    l2capErrorRepeat = 0
                    l2capBackoffMs = 140L
                } catch (t: Throwable) {
                    val msg = normalizeErrorMessage(t.message ?: t.javaClass.simpleName)
                    if (msg == lastL2capError) {
                        l2capErrorRepeat++
                        if (l2capErrorRepeat % 20 == 0) {
                            BluetoothStressBus.appendLog("L2CAP error repeats x$l2capErrorRepeat: $msg")
                        }
                    } else {
                        lastL2capError = msg
                        l2capErrorRepeat = 1
                        BluetoothStressBus.appendLog("L2CAP error: $msg")
                    }
                    if (isLikelyUnsupportedL2cap(msg) && l2capErrorRepeat >= 3) {
                        l2capDisabledForSession = true
                        BluetoothStressBus.appendLog("L2CAP disabled: channel likely unsupported by target device")
                    } else {
                        l2capBackoffMs = (l2capBackoffMs * 2).coerceAtMost(2500L)
                    }
                }
                delay(l2capBackoffMs)
            }
        }

        gattJob = scope.launch {
            while (true) {
                try {
                    val gatt: BluetoothGatt? = device.connectGatt(
                        this@BluetoothStressService,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                    try {
                        gatt?.close()
                    } catch (_: Throwable) {
                    }
                    logBurst("GATT connect+close")
                    lastGattError = ""
                    gattErrorRepeat = 0
                } catch (t: Throwable) {
                    val msg = normalizeErrorMessage(t.message ?: t.javaClass.simpleName)
                    if (msg == lastGattError) {
                        gattErrorRepeat++
                        if (gattErrorRepeat % 20 == 0) {
                            BluetoothStressBus.appendLog("GATT error repeats x$gattErrorRepeat: $msg")
                        }
                    } else {
                        lastGattError = msg
                        gattErrorRepeat = 1
                        BluetoothStressBus.appendLog("GATT error: $msg")
                    }
                }
                delay(140)
            }
        }
    }

    private fun startLowLatencyScan(adapter: BluetoothAdapter) {
        scanner = adapter.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner?.startScan(null, settings, scanCallback)
            BluetoothStressBus.appendLog("LE scan: SCAN_MODE_LOW_LATENCY enabled")
        } catch (t: Throwable) {
            BluetoothStressBus.appendLog("LE scan start failed: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun stopAll() {
        l2capJob?.cancel()
        l2capJob = null
        gattJob?.cancel()
        gattJob = null
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Throwable) {
        }
        scanner = null
        BluetoothStressBus.appendLog("STOP all processes")
        BluetoothStressBus.setRunning(false)
    }

    private fun logBurst(message: String) {
        val count = loopsCounter.incrementAndGet()
        if (count % 25L == 0L) {
            BluetoothStressBus.appendLog("$message #$count")
        }
    }

    companion object {
        const val ACTION_START = "com.droid.dolphy.bluetooth.audio.START"
        const val ACTION_STOP = "com.droid.dolphy.bluetooth.audio.STOP"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_RSSI = "extra_rssi"
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BtAudioStressScanScreen(navController: NavController) {
    val context = LocalContext.current
    val devices = remember { mutableStateMapOf<String, StressDeviceUi>() }
    var scanning by remember { mutableStateOf(false) }
    var keepScanning by remember { mutableStateOf(true) }
    val manager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val adapter = manager?.adapter
    val bleScanner = remember(adapter) { adapter?.bluetoothLeScanner }

    val permissions = remember {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    fun hasPermissions(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isLikelyAudioName(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("buds") || n.contains("airpods") || n.contains("head") ||
            n.contains("ear") || n.contains("audio") || n.contains("speaker")
    }

    fun addOrUpdateDevice(device: BluetoothDevice, rssi: Int, source: String) {
        val name = try {
            device.name
        } catch (_: SecurityException) {
            null
        } ?: "Unknown audio"
        val old = devices[device.address]
        val nextRssi = if (rssi == Int.MIN_VALUE) (old?.rssi ?: Int.MIN_VALUE) else rssi
        val mergedSource = when {
            old == null -> source
            old.source == source -> source
            old.source.contains(source) -> old.source
            else -> "${old.source}+${source}"
        }
        devices[device.address] = StressDeviceUi(
            name = if (name.isBlank()) "Unknown audio" else name,
            address = device.address,
            rssi = nextRssi,
            source = mergedSource
        )
    }

    val bleCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device ?: return
                val name = try {
                    d.name ?: result.scanRecord?.deviceName
                } catch (_: SecurityException) {
                    result.scanRecord?.deviceName
                }.orEmpty()
                val probablyAudio = isLikelyAudioName(name) ||
                    d.type == BluetoothDevice.DEVICE_TYPE_LE ||
                    d.type == BluetoothDevice.DEVICE_TYPE_DUAL
                if (probablyAudio) {
                    addOrUpdateDevice(d, result.rssi, "BLE")
                }
            }
        }
    }

    fun startDiscovery(adapter: BluetoothAdapter?) {
        if (adapter == null) return
        if (!adapter.isEnabled) {
            scanning = false
            return
        }
        if (adapter.isDiscovering) {
            scanning = true
            return
        }
        scanning = adapter.startDiscovery()
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bleScanner?.startScan(null, settings, bleCallback)
        } catch (_: Throwable) {
        }
        try {
            adapter.bondedDevices
                ?.forEach { bonded ->


                    addOrUpdateDevice(bonded, Int.MIN_VALUE, "PAIRED")
                }
        } catch (_: Throwable) {
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            startDiscovery(adapter)
        }
    }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                        val btClass =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS)
                            }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        val major = btClass?.majorDeviceClass
                        if (major == BluetoothClass.Device.Major.AUDIO_VIDEO && device != null) {
                            addOrUpdateDevice(device, rssi, "CLASSIC")
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        if (adapter?.isEnabled == true && keepScanning) {
                            scanning = adapter.startDiscovery()
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            keepScanning = false
            try {
                adapter?.cancelDiscovery()
            } catch (_: Throwable) {
            }
            try {
                bleScanner?.stopScan(bleCallback)
            } catch (_: Throwable) {
            }
            scanning = false
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(Unit) {
        if (hasPermissions() && adapter?.isEnabled == true) {
            startDiscovery(adapter)
        }
    }

    LaunchedEffect(keepScanning) {
        while (keepScanning) {
            if (hasPermissions() && adapter?.isEnabled == true && !adapter.isDiscovering) {
                startDiscovery(adapter)
            }
            kotlinx.coroutines.delay(1200)
        }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    MaterialBackground(accentColor = accentColor) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.74f)) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "BT-Audio sttest",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            DolphyIconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                ) {
                    Button(
                        onClick = {
                            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                            if (!hasPermissions()) {
                                launcher.launch(permissions)
                            } else if (adapter?.isEnabled == true) {
                                keepScanning = true
                                startDiscovery(adapter)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (scanning) stringResource(R.string.audio_scanning_devices) else stringResource(R.string.audio_start_scan))
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        items(devices.values.sortedByDescending { it.rssi }, key = { it.address }) { device ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                )
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text("MAC: ${device.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        if (device.rssi == Int.MIN_VALUE) "RSSI: n/a" else "RSSI: ${device.rssi}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text("Source: ${device.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val route = "other/bt_audio_stress_run/${Uri.encode(device.name)}/${Uri.encode(device.address)}/${device.rssi}"
                                            navController.navigate(route)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null)
                                        Spacer(Modifier.size(6.dp))
                                        Text(stringResource(R.string.ble_audio_test))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BtAudioStressRunScreen(
    navController: NavController,
    deviceName: String,
    deviceAddress: String,
    initialRssi: Int
) {
    val listState: LazyListState = rememberLazyListState()
    val context = LocalContext.current
    val logs by BluetoothStressBus.logs.collectAsState()
    val running by BluetoothStressBus.running.collectAsState()
    val targetName by BluetoothStressBus.targetName.collectAsState()
    val targetAddress by BluetoothStressBus.targetAddress.collectAsState()
    val targetRssi by BluetoothStressBus.targetRssi.collectAsState()

    val titleName = if (targetName.isBlank()) deviceName else targetName
    val titleAddress = if (targetAddress.isBlank()) deviceAddress else targetAddress
    val showRssi = if (targetRssi == Int.MIN_VALUE) initialRssi else targetRssi

    fun startService() {
        val intent = Intent(context, BluetoothStressService::class.java).apply {
            action = BluetoothStressService.ACTION_START
            putExtra(BluetoothStressService.EXTRA_NAME, deviceName)
            putExtra(BluetoothStressService.EXTRA_ADDRESS, deviceAddress)
            putExtra(BluetoothStressService.EXTRA_RSSI, initialRssi)
        }
        context.startService(intent)
    }

    fun stopService() {
        val intent = Intent(context, BluetoothStressService::class.java).apply {
            action = BluetoothStressService.ACTION_STOP
        }
        context.startService(intent)
    }

    LaunchedEffect(deviceAddress) {
        startService()
    }

    DisposableEffect(Unit) {
        onDispose {

        }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    MaterialBackground(accentColor = accentColor) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.74f)) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DolphyIconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = titleName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Text("MAC: $titleAddress", color = MaterialTheme.colorScheme.onSurface)
                            Text("RSSI: $showRssi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { if (running) stopService() else startService() }
                    ) {
                        Icon(if (running) Icons.Default.Stop else Icons.Default.Bolt, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (running) stringResource(R.string.stop) else stringResource(R.string.audio_run_again))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.audio_send_logs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    if (logs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LaunchedEffect(logs.size) {
                            if (logs.isNotEmpty()) {
                                listState.scrollToItem(logs.lastIndex)
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            itemsIndexed(items = logs, key = { index, line -> "$index-$line" }) { _, line ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))) {
                                    Text(
                                        text = line,
                                        modifier = Modifier.padding(10.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

