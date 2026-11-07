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
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

    BlurHeaderBox(
        title = stringResource(R.string.bt_jammer_title),
        onBack = onBack,
        accentColor = accentColor
    ) { headerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerPadding + 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "scan_hero") {
                JammerScanHeader(
                    isScanning = isScanning,
                    deviceCount = devices.size,
                    accentColor = accentColor
                )
            }

            if (devices.isNotEmpty()) {
                item(key = "section_header") {
                    M3SegmentedListSectionHeader(
                        title = stringResource(R.string.bt_jammer_select_device)
                    )
                }
                m3SegmentedItems(devices, key = { it.address }) { index, count, device ->
                    M3SegmentedListItem(
                        index = index,
                        count = count,
                        headline = device.name,
                        supporting = device.address,
                        leadingIcon = Icons.Default.Bluetooth,
                        onClick = { selectedDevice = device }
                    )
                }
            }

            if (devices.isEmpty() && !isScanning) {
                item(key = "empty") {
                    JammerEmptyState()
                }
            }
        }
    }
}

@Composable
private fun JammerScanHeader(
    isScanning: Boolean,
    deviceCount: Int,
    accentColor: Color
) {
    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        containerType = "primary",
        cornerRadius = 28.dp,
        contentPadding = 20.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                if (isScanning) {
                    WavyCircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = accentColor,
                        trackColor = Color.Transparent
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(accentColor.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isScanning) Icons.Default.BluetoothSearching else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isScanning) {
                        stringResource(R.string.bt_jammer_scanning)
                    } else {
                        stringResource(R.string.bt_jammer_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (deviceCount > 0) {
                        stringResource(R.string.bt_jammer_found, deviceCount)
                    } else {
                        stringResource(R.string.bt_jammer_tap_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun JammerEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.bt_jammer_no_devices),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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

    BlurHeaderBox(
        title = stringResource(R.string.bt_jammer_jamming),
        onBack = {
            if (isAttacking) engine.stopJamming()
            onBack()
        },
        accentColor = accentColor
    ) { headerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(top = headerPadding + 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            MaterialCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = accentColor,
                cornerRadius = 32.dp,
                contentPadding = 28.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    JammerStatusPill(active = isAttacking, accentColor = accentColor)

                    Spacer(Modifier.height(20.dp))

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                        if (isAttacking) {
                            JammerPulseRings(accentColor = accentColor)
                        }
                        WavyCircularProgressIndicator(
                            modifier = Modifier.size(132.dp),
                            color = accentColor,
                            trackColor = Color.Transparent,
                            fillColor = accentColor.copy(alpha = 0.25f)
                        )
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .background(accentColor.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.BluetoothDisabled,
                                null,
                                modifier = Modifier.size(42.dp),
                                tint = accentColor
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(
                        stringResource(R.string.bt_jammer_target),
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        device.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        device.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                Text(
                    text = logText.ifEmpty { stringResource(R.string.bt_jammer_ready) },
                    color = if (isAttacking) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun JammerStatusPill(active: Boolean, accentColor: Color) {
    val containerColor = if (active) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val dotColor = if (active) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
    val infinite = rememberInfiniteTransition(label = "pill")
    val dotAlpha by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Surface(
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayerAlpha(if (active) dotAlpha else 1f)
                    .background(dotColor, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(if (active) R.string.bt_jammer_jamming else R.string.bt_jammer_ready),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = dotColor
            )
        }
    }
}

@Composable
private fun JammerPulseRings(accentColor: Color) {
    val infinite = rememberInfiniteTransition(label = "rings")
    for (i in 0 until 2) {
        val progress by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(1800, delayMillis = i * 900, easing = LinearEasing),
                RepeatMode.Restart
            ),
            label = "ring$i"
        )
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(0.6f + progress * 0.4f)
                .graphicsLayerAlpha(1f - progress)
                .background(accentColor.copy(alpha = 0.12f), CircleShape)
        )
    }
}

private fun Modifier.graphicsLayerAlpha(value: Float): Modifier =
    this.then(Modifier.alpha(value))

