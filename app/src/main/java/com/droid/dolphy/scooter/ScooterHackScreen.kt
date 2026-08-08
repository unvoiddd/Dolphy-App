package com.droid.dolphy.scooter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.droid.dolphy.M3SegmentedListItemContainer
import com.droid.dolphy.M3SegmentedListItemSpacing
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.WavyCircularProgressIndicator

@Composable
fun ScooterHackScreen(
    navController: NavController,
    viewModel: ScooterHackViewModel = viewModel(),
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) {
            viewModel.startScan(context)
        } else {
            Toast.makeText(context, context.getString(R.string.scooter_perm_denied), Toast.LENGTH_SHORT).show()
        }
    }

    fun ensureScan() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewModel.startScan(context)
        } else {
            permissionLauncher.launch(perms)
        }
    }

    LaunchedEffect(Unit) {
        ensureScan()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScan()
        }
    }

    MaterialBackground(accentColor = accent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            SectionTopBar(
                title = stringResource(R.string.scooter_hack_title),
                onBack = { navController.popBackStack() },
                accentColor = accent,
            )

            Spacer(Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isScanning) {
                            WavyCircularProgressIndicator(
                                modifier = Modifier.size(72.dp),
                                color = accent,
                                strokeWidth = 5.dp,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.scooter_scanning),
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.scooter_empty),
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
                    contentPadding = PaddingValues(bottom = 100.dp, top = 4.dp),
                ) {
                    itemsIndexed(devices, key = { _, d -> d.address }) { index, scooter ->
                        ScooterDeviceCard(
                            scooter = scooter,
                            index = index,
                            count = devices.size,
                            accent = accent,
                            onToggleConnect = { viewModel.toggleConnection(context, scooter) },
                            onSendPayload = { payload, label -> viewModel.sendTuningPayload(payload, label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScooterDeviceCard(
    scooter: ScooterDevice,
    index: Int,
    count: Int,
    accent: Color,
    onToggleConnect: () -> Unit,
    onSendPayload: (ByteArray, String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val connected = scooter.isConnected

    M3SegmentedListItemContainer(
        index = index,
        count = count,
        selected = connected,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ElectricScooter,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scooter.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = "${scooter.address} · ${scooter.rssi} dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (!scooter.statusMessage.isNullOrBlank()) {
                        Text(
                            text = scooter.statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (connected) accent else colorScheme.onSurfaceVariant,
                        )
                    }
                }

                BatteryBadge(percent = scooter.batteryPercent, accent = accent)

                Spacer(Modifier.width(10.dp))

                FilledIconButton(
                    onClick = onToggleConnect,
                    enabled = !scooter.isConnecting,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accent,
                        contentColor = Color(0xFFFF1744),
                        disabledContainerColor = accent.copy(alpha = 0.45f),
                        disabledContentColor = Color(0xFFFF1744).copy(alpha = 0.6f),
                    ),
                ) {
                    Icon(
                        imageVector = if (connected) Icons.Filled.FlashOff else Icons.Filled.Bolt,
                        contentDescription = if (connected) {
                            stringResource(R.string.scooter_disconnect)
                        } else {
                            stringResource(R.string.scooter_connect)
                        },
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            if (connected) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = Icons.Filled.Build
                    ActionChip(icon = icon, label = "Max 2G", accent = accent) { onSendPayload(ScooterProtocol.PAYLOAD_MAX_2G, "Max 2G") }
                    ActionChip(icon = icon, label = "F2", accent = accent) { onSendPayload(ScooterProtocol.PAYLOAD_F2, "F2") }
                    ActionChip(icon = icon, label = "Gen 1", accent = accent) { onSendPayload(ScooterProtocol.PAYLOAD_GEN_1, "Gen 1") }
                    ActionChip(icon = icon, label = "Gen 2", accent = accent) { onSendPayload(ScooterProtocol.PAYLOAD_GEN_2, "Gen 2") }
                    ActionChip(icon = icon, label = "Gen 3", accent = accent) { onSendPayload(ScooterProtocol.PAYLOAD_GEN_3, "Gen 3") }
                }
            }
        }
    }
}

@Composable
private fun BatteryBadge(percent: Int?, accent: Color) {
    val colorScheme = MaterialTheme.colorScheme
    val icon = if ((percent ?: 0) > 20) Icons.Filled.BatteryFull else Icons.Filled.BatteryStd
    val tint = when {
        percent == null -> colorScheme.onSurfaceVariant
        percent <= 15 -> Color(0xFFFF5252)
        percent <= 35 -> Color(0xFFFFB300)
        else -> Color(0xFF69F0AE)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = if (percent != null) "$percent%" else "—",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = accent.copy(alpha = 0.18f),
                contentColor = accent,
            ),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

