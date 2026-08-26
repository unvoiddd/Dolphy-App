package com.droid.dolphy.ble.smarthome

import com.droid.dolphy.DolphyIconButton

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.droid.dolphy.DolphyCircularProgressIndicator
import com.droid.dolphy.DolphySlider
import com.droid.dolphy.M3SegmentedList
import com.droid.dolphy.M3SegmentedListItem
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private data class SmartHomeScanItem(
    val address: String,
    val name: String,
    val vendor: String,
    val rssi: Int
)

@Composable
fun BleSmartHomeScanScreen(navController: NavController) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val accent = MaterialTheme.colorScheme.primary

    var hasPerm by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var noDevicesFound by remember { mutableStateOf(false) }
    var scanSessionId by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<SmartHomeScanItem>>(emptyList()) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        hasPerm = grants.values.all { it }
    }

    fun ensurePerms() {
        val required = buildList {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = required.filter {
            ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permLauncher.launch(missing.toTypedArray())
        } else {
            hasPerm = true
        }
    }

    LaunchedEffect(Unit) { ensurePerms() }

    val bluetoothAdapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    val scanner = remember { bluetoothAdapter.bluetoothLeScanner }

    val callback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val addr = device.address ?: return
                val name = result.scanRecord?.deviceName ?: device.name ?: "BLE device"
                val vendor = BleVendorDb.getVendorLabel(context, addr)
                val rssi = result.rssi
                items = (items.filterNot { it.address == addr } + SmartHomeScanItem(addr, name, vendor, rssi))
                    .sortedByDescending { it.rssi }
            }
        }
    }

    fun startScan() {
        if (!hasPerm) return
        if (bluetoothAdapter.isEnabled != true) return
        runCatching { scanner.stopScan(callback) }
        items = emptyList()
        noDevicesFound = false
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        isScanning = true
        scanSessionId++
        scanner.startScan(null, settings, callback)
    }

    fun stopScan() {
        runCatching { scanner.stopScan(callback) }
        isScanning = false
    }

    DisposableEffect(Unit) {
        startScan()
        onDispose { stopScan() }
    }

    LaunchedEffect(scanSessionId) {
        if (!isScanning) return@LaunchedEffect
        delay(8_000)
        if (isScanning) {
            stopScan()
            noDevicesFound = items.isEmpty()
        }
    }

    Box(Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                SectionTopBar(
                    title = stringResource(R.string.ble_smart_home),
                    onBack = { navController.popBackStack() },
                    transparent = true,
                    actions = {
                        DolphyIconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isScanning) stopScan() else startScan()
                        }) {
                            if (isScanning) DolphyCircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Lightbulb, contentDescription = "Scan")
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))

                if (!hasPerm) {
                    Text(stringResource(R.string.ble_smarthome_need_perms), color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(12.dp))
                    com.droid.dolphy.AccentButton(onClick = { ensurePerms() }) { Text(stringResource(R.string.ble_allow)) }
                    return@Column
                }
                if (bluetoothAdapter.isEnabled != true) {
                    Text("Bluetooth выключен. Включите Bluetooth.", color = MaterialTheme.colorScheme.onBackground)
                    return@Column
                }

                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (isScanning) {
                            val pulse = rememberInfiniteTransition(label = "scanPulse")
                            val scale by pulse.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.2f,
                                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                                label = "scanPulseScale"
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size((34f * scale).dp)
                                        .background(accent.copy(alpha = 0.16f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(stringResource(R.string.us_scanning), color = MaterialTheme.colorScheme.onBackground)
                            }
                        } else if (noDevicesFound) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.ir_not_found), color = MaterialTheme.colorScheme.onBackground)
                                DolphyIconButton(
                                    onClick = { startScan() },
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(accent.copy(alpha = 0.18f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.ble_rescan), tint = accent)
                                }
                            }
                        }
                    }
                } else {
                    M3SegmentedList(
                        items = items,
                        modifier = Modifier.fillMaxWidth(),
                    ) { index, count, item ->
                        M3SegmentedListItem(
                            index = index,
                            count = count,
                            headline = item.vendor.takeIf { it != "Unknown" } ?: "Smart Light",
                            supporting = "${item.address} • ${item.rssi} dBm",
                            leadingIcon = Icons.Default.Lightbulb,
                            leadingIconTint = accent,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                navController.navigate("ble_smart_home_device/${item.address}")
                            },
                        )
                    }
                    if (isScanning) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            DolphyCircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BleSmartHomeDeviceScreen(navController: NavController, address: String) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current

    val adapter = remember { (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    val device = remember(address) { runCatching { adapter.getRemoteDevice(address) }.getOrNull() }

    var connected by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Connecting…") }

    var powerOn by remember { mutableStateOf(true) }
    var brightness by remember { mutableStateOf(1f) }
    var color by remember { mutableStateOf(Color(0xFFFF6600)) }

    val manager = remember { SmartLightManager(context.applicationContext) }

    DisposableEffect(address) {
        if (device == null) {
            connecting = false
            status = context.getString(R.string.ble_smarthome_device_not_found)
            onDispose { }
        } else {
            manager.connect(device)
                .useAutoConnect(false)
                .timeout(10_000)
                .fail { _, _ ->
                    connecting = false
                    connected = false
                    status = context.getString(R.string.ble_smarthome_connect_failed)
                }
                .done {
                    connecting = false
                    connected = true
                    status = "Connected"
                }
                .enqueue()
            onDispose {
                runCatching { manager.disconnect().enqueue() }
            }
        }
    }

    fun sendCurrentColor() {
        val rgb = color.toArgb()
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        manager.setColor(r, g, b)
        manager.setBrightness((brightness * 255f).toInt())
    }

    MaterialBackground(accentColor = accent) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            SectionTopBar(
                title = stringResource(R.string.ble_smart_light),
                onBack = { navController.popBackStack() },
                transparent = true
            )

            Spacer(Modifier.height(12.dp))

            MaterialCard(Modifier.fillMaxWidth(), accentColor = accent, cornerRadius = 18.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(device?.name ?: address, fontWeight = FontWeight.SemiBold)
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (connecting) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DolphyCircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.ble_connecting))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            MaterialCard(Modifier.fillMaxWidth(), accentColor = accent, cornerRadius = 18.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Color wheel", fontWeight = FontWeight.SemiBold)
                    ColorWheel(
                        color = color,
                        onColorChange = {
                            color = it
                            if (connected) sendCurrentColor()
                        }
                    )

                    Text(stringResource(R.string.ble_brightness), fontWeight = FontWeight.SemiBold)
                    DolphySlider(
                        value = brightness,
                        onValueChange = {
                            brightness = it
                            if (connected) manager.setBrightness((brightness * 255f).toInt())
                        },
                        valueRange = 0.05f..1f
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        com.droid.dolphy.AccentButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                powerOn = true
                                manager.power(true)
                                sendCurrentColor()
                            },
                            enabled = connected,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) { Text(stringResource(R.string.ble_on)) }
                        com.droid.dolphy.AccentButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                powerOn = false
                                manager.power(false)
                            },
                            enabled = connected,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text(stringResource(R.string.ble_off)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorWheel(
    color: Color,
    onColorChange: (Color) -> Unit
) {


    val size = 220.dp
    var center by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var radiusPx by remember { mutableStateOf(1f) }

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
            .padding(12.dp)
            .clickable { }
            .pointerInput(Unit) {
                fun handle(x: Float, y: Float) {
                    val dx = x - center.x
                    val dy = y - center.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist <= radiusPx) {
                        val sat = (dist / radiusPx).coerceIn(0f, 1f)
                        val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                        val hue = ((angle / (2f * Math.PI.toFloat())) * 360f + 360f) % 360f
                        onColorChange(Color.hsv(hue, sat, 1f))
                    }
                }
                detectDragGestures(
                    onDragStart = { pos -> handle(pos.x, pos.y) },
                    onDrag = { change, _ -> handle(change.position.x, change.position.y) }
                )
            }
            .onSizeChanged {
                val w = it.width.toFloat()
                val h = it.height.toFloat()
                center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)
                radiusPx = min(w, h) / 2f
            }
    ) {
        for (i in 0 until 360 step 2) {
            val start = i.toFloat()
            val end = (i + 2).toFloat()
            drawArc(
                color = Color.hsv(start, 1f, 1f),
                startAngle = start,
                sweepAngle = end - start,
                useCenter = true
            )
        }

        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radiusPx
            ),
            radius = radiusPx
        )


        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        val theta = (hsv[0] / 180f) * Math.PI.toFloat()
        val sat = hsv[1].coerceIn(0f, 1f)
        val px = center.x + cos(theta) * (sat * radiusPx)
        val py = center.y + sin(theta) * (sat * radiusPx)
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 10f, center = androidx.compose.ui.geometry.Offset(px, py))
        drawCircle(Color.White, radius = 6f, center = androidx.compose.ui.geometry.Offset(px, py))
    }
}


