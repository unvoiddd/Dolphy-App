package com.droid.dolphy

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

data class JammerDevice(val name: String, val address: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothJammerScanScreen(onBack: () -> Unit, accentColor: Color) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<JammerDevice>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<JammerDevice?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    fun checkPermissions(): Boolean {
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
        if (permissions.all { ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            return true
        }
        permissionLauncher.launch(permissions)
        return false
    }

    if (selectedDevice != null) {
        BluetoothJammerAttackScreen(
            device = selectedDevice!!,
            onBack = { selectedDevice = null },
            accentColor = accentColor
        )
        return
    }

    DisposableEffect(Unit) {
        var receiver: BroadcastReceiver? = null
        var adapter: BluetoothAdapter? = null

        if (checkPermissions()) {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            adapter = manager.adapter
            if (adapter?.isEnabled == true) {
                isScanning = true
                receiver = object : BroadcastReceiver() {
                    @SuppressLint("MissingPermission")
                    override fun onReceive(ctx: Context, intent: Intent) {
                        if (intent.action == BluetoothDevice.ACTION_FOUND) {
                            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                            device?.let {
                                val info = JammerDevice(it.name ?: "Unknown", it.address)
                                if (devices.none { d -> d.address == info.address }) {
                                    devices = devices + info
                                }
                            }
                        } else if (intent.action == BluetoothAdapter.ACTION_DISCOVERY_FINISHED) {
                            isScanning = false
                        }
                    }
                }
                context.registerReceiver(receiver, IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                })
                adapter.startDiscovery()
            }
        }
        onDispose {
            receiver?.let { context.unregisterReceiver(it) }
            adapter?.cancelDiscovery()
        }
    }

    MaterialBackground(accentColor = accentColor) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                SectionTopBar(
                    title = stringResource(R.string.bt_jammer_title),
                    onBack = onBack,
                    accentColor = accentColor
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.bt_jammer_select_device),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )

                if (devices.isEmpty() && !isScanning) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.bt_jammer_no_devices), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(devices, key = { it.address }) { device ->
                        MaterialCard(
                            modifier = Modifier.fillMaxWidth().clickable { selectedDevice = device },
                            accentColor = accentColor,
                            cornerRadius = 24.dp,
                            contentPadding = 16.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(48.dp).background(accentColor.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Bluetooth, null, tint = accentColor)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothJammerAttackScreen(
    device: JammerDevice,
    onBack: () -> Unit,
    accentColor: Color
) {
    val context = LocalContext.current
    val engine = remember { BluetoothJammerEngine(context) }
    var isAttacking by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    BackHandler {
        if (isAttacking) {
            engine.stopJamming()
        }
        onBack()
    }

    fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logText += "[$time] $msg\n"
        scope.launch {
            scrollState.animateScrollTo(Int.MAX_VALUE)
        }
    }


    LaunchedEffect(Unit) {
        isAttacking = true
        appendLog(context.getString(R.string.bt_jammer_starting, device.name))
        engine.startJamming(device.address) { msg ->
            appendLog(msg)
        }
    }

    MaterialBackground(accentColor = accentColor) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                SectionTopBar(
                    title = stringResource(R.string.bt_jammer_jamming),
                    onBack = {
                        if (isAttacking) engine.stopJamming()
                        onBack()
                    },
                    accentColor = accentColor
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accentColor,
                    cornerRadius = 28.dp,
                    contentPadding = 24.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.bt_jammer_target), style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                        Text(device.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        Text(device.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(Modifier.height(24.dp))

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(accentColor.copy(alpha = 0.15f), CircleShape)
                            )

                            WavyCircularProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                color = accentColor,
                                trackColor = Color.Transparent,
                                fillColor = accentColor.copy(alpha = 0.3f)
                            )

                            Icon(
                                Icons.Default.BluetoothDisabled,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = accentColor
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))


                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.bt_jammer_activity_log), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    if (isAttacking) {
                        ExpressiveCircularProgressIndicator(modifier = Modifier.size(16.dp), color = accentColor)
                    }
                }

                Spacer(Modifier.height(8.dp))


                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    Text(
                        text = logText.ifEmpty { stringResource(R.string.bt_jammer_ready) },
                        color = if (isAttacking) accentColor else Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

