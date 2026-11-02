package com.droid.dolphy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.plugin.PluginBluetoothHooks
import org.json.JSONObject

@Composable
fun BondSpamScreen(
    viewModel: SpamViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanningDevices by viewModel.isScanningDevices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val isSpamming by viewModel.isSpamming.collectAsState()
    val sliderValue by viewModel.sliderValue.collectAsState()

    LaunchedEffect(Unit) {
        if (!isScanningDevices) {
            viewModel.startDeviceScan()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accentColor) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                SectionTopBar(
                    title = stringResource(R.string.bond_spam_title),
                    onBack = onBack,
                    actions = {
                        DolphyIconButton(
                            onClick = {
                                vibrate(context)
                                viewModel.startDeviceScan()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = accentColor
                            )
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.bond_spam_devices),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isScanningDevices) {
                                    DolphyCircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

                            val devices = remember(discoveredDevices) {
                                discoveredDevices.distinctBy { it.address }
                            }

                            if (devices.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.bond_spam_no_devices),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(devices) { device ->
                                        val isSelected = selectedDevice?.address == device.address
                                        val deviceName = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() }
                                            ?: "Unknown Device"

                                        Card(
                                            onClick = {
                                                vibrate(context)
                                                viewModel.selectDevice(device)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainer
                                                }
                                            ),
                                            border = if (isSelected) {
                                                BorderStroke(1.5.dp, accentColor)
                                            } else null
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Bluetooth,
                                                    contentDescription = null,
                                                    tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = deviceName,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = device.address,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = accentColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }


                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val targetName = runCatching { selectedDevice?.name }.getOrNull()?.takeIf { it.isNotBlank() }
                                    ?: selectedDevice?.address
                                val targetText = if (targetName != null) {
                                    "${stringResource(R.string.bond_spam_target)} $targetName"
                                } else {
                                    stringResource(R.string.bond_spam_select_device)
                                }

                                Text(
                                    text = targetText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedDevice != null) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "${stringResource(R.string.ble_label_spam_interval)}: ${sliderValue.toInt()} ms",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                DolphySlider(
                                    value = sliderValue,
                                    onValueChange = {
                                        vibrate(context)
                                        viewModel.onSliderValueChanged(it)
                                    },
                                    valueRange = 0f..2000f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentColor,
                                        activeTrackColor = accentColor,
                                        inactiveTrackColor = accentColor.copy(alpha = 0.3f)
                                    )
                                )
                            }

                            AnimatedBleButton(
                                text = if (isSpamming) stringResource(R.string.stop) else stringResource(R.string.start),
                                onClick = {
                                    if (isSpamming) {
                                        viewModel.stopSpam()
                                    } else {
                                        if (selectedDevice != null) {
                                            viewModel.startSpam()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                isActive = isSpamming,
                                accentColor = accentColor,
                                fullyRounded = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BleSpamFullScreen(
    viewModel: SpamViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accentColor) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                SectionTopBar(
                    title = stringResource(R.string.ble_spam_screen_title),
                    onBack = onBack
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    BleSpamScreen(viewModel) { section ->
                        val decision = PluginBluetoothHooks.action(
                            "bluetooth.ble.section.open",
                            JSONObject().put("section", section.route),
                        )
                        if (!decision.cancelled && !decision.handled) {
                            val changed = runCatching { JSONObject(decision.payloadJson) }.getOrNull()
                            val route = changed?.optString("section")?.ifBlank { section.route } ?: section.route
                            navController.navigate("ble_section/$route")
                        }
                    }
                }
            }
        }
    }
}
