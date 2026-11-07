package com.droid.dolphy

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn

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
private fun BondSpamContent(
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

    BlurHeaderBox(
        title = stringResource(R.string.bond_spam_title),
        onBack = onBack,
        accentColor = accentColor,
        actions = {
            DolphyIconButton(
                onClick = {
                    vibrate(context)
                    viewModel.startDeviceScan()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh),
                    tint = accentColor
                )
            }
        }
    ) { headerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(headerPadding))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val deviceListHeight = (maxHeight - 300.dp).coerceIn(140.dp, 360.dp)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(deviceListHeight),
                        shape = RoundedCornerShape(28.dp),
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
                                val unknownName = stringResource(R.string.nrf_unknown)
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)
                                ) {
                                    m3SegmentedItems(devices, key = { it.address }) { index, count, device ->
                                        val isSelected = selectedDevice?.address == device.address
                                        val deviceName = (try { device.name } catch (_: SecurityException) { null })?.takeIf { it.isNotBlank() }
                                            ?: unknownName

                                        M3SegmentedListItem(
                                            index = index,
                                            count = count,
                                            headline = deviceName,
                                            supporting = device.address,
                                            leadingIcon = Icons.Default.Bluetooth,
                                            selected = isSelected,
                                            showChevron = false,
                                            trailingContent = if (isSelected) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryFixed,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            } else null,
                                            onClick = {
                                                vibrate(context)
                                                viewModel.selectDevice(device)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }


                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val targetName = (try { selectedDevice?.name } catch (_: SecurityException) { null })?.takeIf { it.isNotBlank() }
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
                                enabled = isSpamming || selectedDevice != null,
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
private fun BleSpamFullContent(
    viewModel: SpamViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary

    BlurHeaderBox(
        title = stringResource(R.string.ble_spam_screen_title),
        onBack = onBack,
        accentColor = accentColor
    ) { headerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(headerPadding))
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

@Composable
fun BondSpamScreen(viewModel: SpamViewModel, onBack: () -> Unit) {
    BluetoothPermissionGate(onBack = onBack) {
        LaunchedEffect(Unit) { viewModel.onPermissionsGranted() }
        BondSpamContent(viewModel, onBack)
    }
}

@Composable
fun BleSpamFullScreen(viewModel: SpamViewModel, navController: NavController, onBack: () -> Unit) {
    BluetoothPermissionGate(advertise = true, onBack = onBack) {
        LaunchedEffect(Unit) { viewModel.onPermissionsGranted() }
        BleSpamFullContent(viewModel, navController, onBack)
    }
}
