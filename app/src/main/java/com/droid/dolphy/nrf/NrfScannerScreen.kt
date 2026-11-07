package com.droid.dolphy.nrf

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.droid.dolphy.BluetoothPermissionGate
import com.droid.dolphy.BlurHeaderBox
import com.droid.dolphy.ConnectedButtonGroup
import com.droid.dolphy.ExpressiveBounceButton
import com.droid.dolphy.R
import com.droid.dolphy.WavyCircularProgressIndicator
import kotlinx.coroutines.launch

@Composable
private fun NrfScannerContent(
    context: Context,
    navController: NavController,
    viewModel: NrfScannerViewModel = viewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanError by viewModel.scanError.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val namedOnly by viewModel.isNamedOnly.collectAsState()
    val knownVendorsOnly by viewModel.isKnownVendorsOnly.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val isBluetoothEnabled by viewModel.isBluetoothEnabled.collectAsState()

    DisposableEffect(viewModel) { onDispose { viewModel.stopScanning(); viewModel.stopProfiling() } }
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        viewModel.initialize(context, bluetoothAdapter)
    }

    BlurHeaderBox(
        title = stringResource(R.string.nrf_scanner_title),
        onBack = { navController.popBackStack() },
        accentColor = colorScheme.primary,
    ) { headerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = headerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpressiveBounceButton(
                onClick = {
                    if (isScanning) viewModel.stopScanning() else viewModel.startScanning()
                },
                modifier = Modifier.fillMaxWidth(),
                containerColor = if (isScanning) colorScheme.errorContainer else colorScheme.primary,
                contentColor = if (isScanning) colorScheme.onErrorContainer else colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScanning) stringResource(R.string.nrf_scanner_stop) else stringResource(R.string.nrf_scanner_start),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            val filterOptions = listOf(
                stringResource(R.string.nrf_scanner_all) to "all",
                stringResource(R.string.nrf_scanner_ble) to "ble",
                stringResource(R.string.nrf_scanner_classic) to "classic",
                stringResource(R.string.nrf_scanner_beacon) to "beacon",
                stringResource(R.string.nrf_scanner_fast_pair) to "fast_pair",
            )
            val selectedValue = when (selectedFilter) {
                DeviceType.BLE_DEVICE -> "ble"
                DeviceType.CLASSIC_DEVICE -> "classic"
                DeviceType.BEACON -> "beacon"
                DeviceType.FAST_PAIR -> "fast_pair"
                else -> "all"
            }
            ConnectedButtonGroup(
                options = filterOptions,
                selectedValue = selectedValue,
                onValueSelected = { key ->
                    viewModel.setFilter(
                        when (key) {
                            "ble" -> DeviceType.BLE_DEVICE
                            "classic" -> DeviceType.CLASSIC_DEVICE
                            "beacon" -> DeviceType.BEACON
                            "fast_pair" -> DeviceType.FAST_PAIR
                            else -> null
                        },
                    )
                },
                accentColor = colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = namedOnly,
                    onClick = { viewModel.setNamedOnly(!namedOnly) },
                    label = { Text(stringResource(R.string.nrf_filter_named_only)) },
                    leadingIcon = if (namedOnly) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        null
                    }
                )
                FilterChip(
                    selected = knownVendorsOnly,
                    onClick = { viewModel.setKnownVendorsOnly(!knownVendorsOnly) },
                    label = { Text(stringResource(R.string.nrf_filter_known_vendors)) },
                    leadingIcon = if (knownVendorsOnly) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        null
                    }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        viewModel.setSortMode(
                            if (sortMode == NrfSortMode.RSSI) NrfSortMode.NAME else NrfSortMode.RSSI
                        )
                    },
                    label = {
                        Text(
                            text = if (sortMode == NrfSortMode.NAME) stringResource(R.string.nrf_sort_name)
                            else stringResource(R.string.nrf_sort_signal)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (sortMode == NrfSortMode.NAME) Icons.Filled.SortByAlpha else Icons.Filled.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isScanning) {
                    WavyCircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = stringResource(R.string.nrf_scanner_found, devices.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isScanning) stringResource(R.string.nrf_scanner_scanning) else stringResource(R.string.nrf_scanner_idle),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }

            if (scanError != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.errorContainer,
                    contentColor = colorScheme.onErrorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.bluetooth_scan_failed),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            when {
                isBluetoothEnabled == false -> NrfStateView(
                    icon = Icons.Filled.BluetoothDisabled,
                    title = stringResource(R.string.nrf_bluetooth_off_title),
                    message = stringResource(R.string.nrf_bluetooth_off_message),
                    modifier = Modifier.weight(1f)
                )
                devices.isEmpty() && isScanning -> NrfStateView(
                    icon = Icons.Filled.BluetoothSearching,
                    title = stringResource(R.string.nrf_scanner_scanning),
                    message = stringResource(R.string.nrf_scanner_description),
                    showSpinner = true,
                    modifier = Modifier.weight(1f)
                )
                devices.isEmpty() -> NrfStateView(
                    icon = Icons.Filled.Bluetooth,
                    title = stringResource(R.string.nrf_scanner_no_devices),
                    message = stringResource(R.string.nrf_scanner_tap_to_start),
                    modifier = Modifier.weight(1f)
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(devices, key = { it.address }) { device ->
                        NrfDeviceCard(
                            device = device,
                            onDisconnect = { connectedDevice ->
                                scope.launch { viewModel.disconnectDevice(connectedDevice.address) }
                            },
                            onProfile = { profileDevice ->
                                scope.launch {
                                    viewModel.profileDevice(profileDevice)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NrfStateView(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    showSpinner: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }
            if (showSpinner) {
                WavyCircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun NrfScannerScreen(context: Context, navController: NavController, viewModel: NrfScannerViewModel = viewModel()) {
    BluetoothPermissionGate(onBack = { navController.popBackStack() }) { NrfScannerContent(context, navController, viewModel) }
}
