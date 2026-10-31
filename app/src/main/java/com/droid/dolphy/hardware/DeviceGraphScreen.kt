package com.droid.dolphy.hardware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.IntentCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.droid.dolphy.MaterialButton
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.bridge.flipper.FlipperUsbCdcTransport
import com.droid.dolphy.bridge.flipper.FlipperUsbMonitor
import com.droid.dolphy.core.model.DeviceSnapshot
import com.droid.dolphy.core.model.DeviceState
import com.droid.dolphy.core.model.HardwareDescriptor
import kotlinx.coroutines.launch

@Composable
fun DeviceGraphScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshots by HardwareRuntime.manager.registry.snapshots.collectAsState()
    val flipper by HardwareRuntime.flipper.collectAsState()
    val connecting by HardwareRuntime.connecting.collectAsState()
    val lastError by HardwareRuntime.lastError.collectAsState()

    var usbDevices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }

    val permissionAction = remember(context) { "${context.packageName}.USB_PERMISSION" }

    DisposableEffect(context) {
        HardwareRuntime.initialize(context)
        val monitor = FlipperUsbMonitor(context) { devices ->
            usbDevices = devices
            HardwareRuntime.onUsbDevicesChanged(context, devices)
        }
        monitor.start()
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != permissionAction) return
                val device: UsbDevice? = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted && device != null) {
                    scope.launch { HardwareRuntime.connectFlipper(context, device) }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(permissionAction),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            monitor.stop()
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    fun requestOrConnect(device: UsbDevice) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (usbManager.hasPermission(device)) {
            scope.launch { HardwareRuntime.connectFlipper(context, device) }
            return
        }
        val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val intent = Intent(permissionAction).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
        usbManager.requestPermission(device, pending)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTopBar(
            title = stringResource(R.string.device_graph_title),
            onBack = { navController.popBackStack() },
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.device_graph_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (flipper != null) {
                item {
                    FlipperCard(
                        connecting = connecting,
                        onDisconnect = { HardwareRuntime.disconnectFlipper() },
                    )
                }
            }

            if (snapshots.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.device_graph_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(snapshots, key = { it.descriptor.id }) { snapshot ->
                    DeviceCard(
                        snapshot = snapshot,
                        connecting = connecting,
                        onConnect = {
                            usbDevices
                                .firstOrNull { FlipperUsbCdcTransport.isFlipper(it) }
                                ?.let(::requestOrConnect)
                        },
                        onDisconnect = { HardwareRuntime.disconnectFlipper() },
                    )
                }
            }

            lastError?.let { error ->
                item {
                    Text(
                        text = stringResource(R.string.device_graph_error, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlipperCard(
    connecting: Boolean,
    onDisconnect: () -> Unit,
) {
    val info = HardwareRuntime.flipper.collectAsState().value
    MaterialCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.device_graph_flipper_connected),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            info?.firmware?.let { Text("Firmware: $it", style = MaterialTheme.typography.bodySmall) }
            info?.hardware?.let { Text("Hardware: $it", style = MaterialTheme.typography.bodySmall) }
            info?.serial?.let { Text("Serial: $it", style = MaterialTheme.typography.bodySmall) }
            MaterialButton(
                text = stringResource(R.string.device_graph_disconnect),
                onClick = onDisconnect,
                enabled = !connecting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DeviceCard(
    snapshot: DeviceSnapshot,
    connecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val descriptor = snapshot.descriptor
    MaterialCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = descriptor.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "${descriptor.transport} · ${stateLabel(snapshot.state)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = capabilitySummary(descriptor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (descriptor.transport == com.droid.dolphy.core.model.Transport.USB_SERIAL) {
                if (snapshot.state == DeviceState.READY) {
                    MaterialButton(
                        text = stringResource(R.string.device_graph_disconnect),
                        onClick = onDisconnect,
                        enabled = !connecting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    MaterialButton(
                        text = if (connecting) {
                            stringResource(R.string.device_graph_connecting)
                        } else {
                            stringResource(R.string.device_graph_connect)
                        },
                        onClick = onConnect,
                        enabled = !connecting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun stateLabel(state: DeviceState): String = when (state) {
    DeviceState.DETECTED -> stringResource(R.string.device_graph_state_detected)
    DeviceState.PROBING -> stringResource(R.string.device_graph_state_probing)
    DeviceState.READY -> stringResource(R.string.device_graph_state_ready)
    DeviceState.BUSY -> stringResource(R.string.device_graph_state_busy)
    DeviceState.DISCONNECTED -> stringResource(R.string.device_graph_state_disconnected)
    DeviceState.ERROR -> stringResource(R.string.device_graph_state_error)
    DeviceState.RECOVERING -> stringResource(R.string.device_graph_state_recovering)
}

private fun capabilitySummary(descriptor: HardwareDescriptor): String {
    val caps = descriptor.capabilities.map { it.name }
    if (caps.isEmpty()) return "—"
    val visible = caps.take(6)
    val extra = caps.size - visible.size
    return if (extra > 0) visible.joinToString(" · ") + " · +$extra" else visible.joinToString(" · ")
}
