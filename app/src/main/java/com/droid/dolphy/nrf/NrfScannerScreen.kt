package com.droid.dolphy.nrf

import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.droid.dolphy.ConnectedButtonGroup
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import kotlinx.coroutines.launch

@Composable
fun NrfScannerScreen(
    context: Context,
    navController: NavController,
    viewModel: NrfScannerViewModel = viewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        viewModel.initialize(context, bluetoothAdapter)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = colorScheme.primary) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionTopBar(
                    title = stringResource(R.string.nrf_scanner_title),
                    onBack = { navController.popBackStack() }
                )


        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (isScanning) {
                        viewModel.stopScanning()
                    } else {
                        viewModel.startScanning()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp)
                )
                Text(
                    text = if (isScanning) stringResource(R.string.nrf_scanner_stop) else stringResource(R.string.nrf_scanner_start),
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
                null -> "all"
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
        }

        Divider(thickness = 0.5.dp, color = colorScheme.outlineVariant)


        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (isScanning) stringResource(R.string.nrf_scanner_scanning) else stringResource(R.string.nrf_scanner_no_devices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    if (!isScanning) {
                        Text(
                            text = stringResource(R.string.nrf_scanner_tap_to_start),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
            ) {
                items(devices, key = { it.address }) { device ->
                    NrfDeviceCard(
                        device = device,
                        onConnect = { connectedDevice ->
                            scope.launch {
                            }
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
}}

