package com.droid.dolphy.bluetooth.whisperpair

import com.droid.dolphy.DolphyIconButton

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.R

private enum class Screen { Scanner, Paired, Recordings }

data class AudioConnectionState(
    val isConnected: Boolean = false,
    val isRecording: Boolean = false,
    val isListening: Boolean = false,
    val recordingFile: String? = null,
    val message: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperPairBluetoothScreen(navController: NavController) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val devices = remember { mutableStateListOf<FastPairDevice>() }
    val exploitResults = remember { mutableStateMapOf<String, String>() }
    val audioStates = remember { mutableStateMapOf<String, AudioConnectionState>() }
    val pairedDevices = remember { mutableStateListOf<String>() }
    var currentScreen by remember { mutableStateOf(Screen.Scanner) }
    var isScanning by remember { mutableStateOf(false) }
    var showAllDevices by remember { mutableStateOf(false) }
    val introPrefs = remember { context.getSharedPreferences("WhisperPairPrefs", Context.MODE_PRIVATE) }
    var showAboutDialog by remember {
        mutableStateOf(!introPrefs.getBoolean("whisperpair_about_shown", false))
    }

    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val scanner = remember {
        Scanner(bluetoothManager?.adapter) { device ->
            mainHandler.post {
                val index = devices.indexOfFirst { it.address == device.address }
                if (index == -1) {
                    devices.add(device)
                } else {
                    devices[index] = device.copy(status = devices[index].status)
                }
            }
        }
    }
    val tester = remember { VulnerabilityTester(context) }
    val exploit = remember { FastPairExploit(context) }

    DisposableEffect(Unit) { onDispose { scanner.stopScanning() } }

    fun dismissAboutDialog() {
        introPrefs.edit().putBoolean("whisperpair_about_shown", true).apply()
        showAboutDialog = false
    }

    val accentColor = MaterialTheme.colorScheme.primary
    MaterialBackground(accentColor = accentColor) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background.copy(alpha = 0.74f)) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(padding)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DolphyIconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            text = stringResource(R.string.whisperpair_title),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    TopTabs(currentScreen = currentScreen, onSelect = { currentScreen = it })

                    when (currentScreen) {
                        Screen.Scanner -> ScannerTab(
                            context = context,
                            devices = devices,
                            exploitResults = exploitResults,
                            paddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            isScanning = isScanning,
                            showAllDevices = showAllDevices,
                            onScanToggle = { scan ->
                                if (scan) {
                                    isScanning = scanner.startScanning(showAllDevices)
                                } else {
                                    scanner.stopScanning()
                                    isScanning = false
                                }
                            },
                            onShowAllDevicesChange = {
                                showAllDevices = it
                                devices.clear()
                                if (isScanning) {
                                    isScanning = scanner.startScanning(it)
                                }
                            },
                            onTestDevice = { device ->
                                val idx = devices.indexOfFirst { it.address == device.address }
                                if (idx != -1) devices[idx] = devices[idx].copy(status = DeviceStatus.TESTING)
                                tester.testDevice(device.address) { status ->
                                    val i = devices.indexOfFirst { it.address == device.address }
                                    if (i != -1) devices[i] = devices[i].copy(status = status)
                                }
                            },
                            onExploitDevice = { device ->
                                exploitResults[device.address] = context.getString(R.string.whisperpair_connecting)
                                exploit.exploit(device.address, { progress ->
                                    exploitResults[device.address] = progress
                                }) { result ->
                                    exploitResults[device.address] = when (result) {
                                        is FastPairExploit.ExploitResult.Success -> {
                                            if (!pairedDevices.contains(device.address)) pairedDevices.add(device.address)
                                            context.getString(R.string.whisperpair_paired_success, result.brEdrAddress)
                                        }
                                        is FastPairExploit.ExploitResult.PartialSuccess -> {
                                            if (!pairedDevices.contains(device.address)) pairedDevices.add(device.address)
                                            context.getString(R.string.whisperpair_partial, result.message)
                                        }
                                        is FastPairExploit.ExploitResult.Failed -> context.getString(R.string.whisperpair_error, result.reason)
                                        is FastPairExploit.ExploitResult.AccountKeyResult -> result.message
                                        is FastPairExploit.ExploitResult.AudioConnected -> result.message
                                    }
                                }
                            }
                        )

                        Screen.Paired -> PairedTab(
                            devices = devices.filter { pairedDevices.contains(it.address) },
                            audioStates = audioStates,
                            exploitResults = exploitResults,
                            paddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        )

                        Screen.Recordings -> RecordingsTab(PaddingValues(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { dismissAboutDialog() },
            title = { Text(stringResource(R.string.whisperpair_about_title)) },
            text = { Text(stringResource(R.string.whisperpair_about_message)) },
            confirmButton = {
                TextButton(onClick = { dismissAboutDialog() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun TopTabs(currentScreen: Screen, onSelect: (Screen) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.PlayArrow,
            selected = currentScreen == Screen.Scanner,
            onClick = { onSelect(Screen.Scanner) }
        )
        TabButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Headphones,
            selected = currentScreen == Screen.Paired,
            onClick = { onSelect(Screen.Paired) }
        )
        TabButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LibraryMusic,
            selected = currentScreen == Screen.Recordings,
            onClick = { onSelect(Screen.Recordings) }
        )
    }
}

@Composable
private fun TabButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    ) { Icon(icon, null, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun ScannerTab(
    context: Context,
    devices: List<FastPairDevice>,
    exploitResults: Map<String, String>,
    paddingValues: PaddingValues,
    isScanning: Boolean,
    showAllDevices: Boolean,
    onScanToggle: (Boolean) -> Unit,
    onShowAllDevicesChange: (Boolean) -> Unit,
    onTestDevice: (FastPairDevice) -> Unit,
    onExploitDevice: (FastPairDevice) -> Unit
) {
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.RECORD_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.RECORD_AUDIO
            )
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it } && (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true) {
            onScanToggle(true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        val pulseTransition = rememberInfiniteTransition(label = "scanPulse")
        val pulse by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scanPulseValue"
        )

        Text(
            stringResource(R.string.whisperpair_scanner),
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        if (isScanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size((24f * pulse).dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    if (showAllDevices) stringResource(R.string.whisperpair_scanning_all) else stringResource(R.string.whisperpair_scanning_fast),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    if (!isScanning) {
                        val has = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                        val bluetoothEnabled = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
                        if (!has) {
                            launcher.launch(permissions)
                        } else if (bluetoothEnabled) {
                            onScanToggle(true)
                        }
                    } else {
                        onScanToggle(false)
                    }
                }
            ) {
                Icon(if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                Spacer(Modifier.size(6.dp))
                Text(if (isScanning) stringResource(R.string.whisperpair_stop) else stringResource(R.string.scan))
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onShowAllDevicesChange(!showAllDevices) }
            ) {
                Text(if (showAllDevices) stringResource(R.string.whisperpair_all_ble) else stringResource(R.string.whisperpair_fast_pair))
            }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(devices.sortedByDescending { it.rssi }, key = { it.address }) { device ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                device.displayName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (showAllDevices) {
                                Text(
                                    stringResource(R.string.whisperpair_incompatible),
                                    color = androidx.compose.ui.graphics.Color(0xFFFF4D4D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(device.address, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text("RSSI: ${device.rssi}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text(stringResource(R.string.whisperpair_connecting_ru) + ": ${device.status}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        exploitResults[device.address]?.let {
                            val isConnecting = it.contains("Connecting", ignoreCase = true)
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = if (isConnecting) 16.sp else 12.sp,
                                fontWeight = if (isConnecting) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onExploitDevice(device) }
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.whisperpair_intercept))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PairedTab(
    devices: List<FastPairDevice>,
    audioStates: Map<String, AudioConnectionState>,
    exploitResults: Map<String, String>,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
                        Text(stringResource(R.string.whisperpair_paired), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        if (devices.isEmpty()) {
            Text(stringResource(R.string.whisperpair_paired_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices, key = { it.address }) { device ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(device.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            Text(device.address, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            audioStates[device.address]?.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                            exploitResults[device.address]?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingsTab(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.whisperpair_records),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

