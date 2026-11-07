package com.droid.dolphy.nrf

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droid.dolphy.AccentButton
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.rememberPressShrink
import com.droid.dolphy.R
import kotlinx.coroutines.delay

@Composable
fun NrfDeviceCard(
    device: NrfDevice,
    onDisconnect: (NrfDevice) -> Unit,
    onProfile: (NrfDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val displayVendor = device.vendor ?: device.manufacturerName
    val displayName = deviceDisplayName(device)
    val lastSeenText = rememberLastSeenText(device.lastSeen)

    MaterialCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = { expanded = !expanded },
        containerType = "surface",
        contentPadding = 14.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BrandAvatar(device = device, vendor = displayVendor)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (displayVendor != null) {
                            VendorChip(vendor = displayVendor)
                        }
                        Text(
                            text = getDeviceTypeLabel(device.type),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        if (device.isConnectable == true) {
                            ConnectableChip()
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lastSeenText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SignalStrengthMeter(rssi = device.rssi)
                    Text(
                        text = "${device.rssi} dBm",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (expanded) {
                ExpandedDetails(
                    device = device,
                    displayName = displayName,
                    displayVendor = displayVendor,
                    lastSeenText = lastSeenText,
                    onDisconnect = onDisconnect,
                    onProfile = onProfile
                )
            }
        }
    }
}

@Composable
private fun ExpandedDetails(
    device: NrfDevice,
    displayName: String,
    displayVendor: String?,
    lastSeenText: String,
    onDisconnect: (NrfDevice) -> Unit,
    onProfile: (NrfDevice) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 0.5.dp,
            color = colorScheme.outlineVariant
        )

        DetailRow(
            label = stringResource(R.string.nrf_mac),
            value = device.address,
            monospace = true
        )
        DetailRow(
            label = stringResource(R.string.nrf_type),
            value = getDeviceTypeLabel(device.type)
        )
        DetailRow(
            label = stringResource(R.string.nrf_signal),
            value = "${device.rssi} dBm · ${signalQualityLabel(device.rssi)}"
        )
        device.txPower?.takeIf { it != 127 && it != Int.MIN_VALUE }?.let { txPower ->
            DetailRow(
                label = stringResource(R.string.nrf_tx_power),
                value = "$txPower dBm"
            )
        }
        if (displayVendor != null) {
            DetailRow(
                label = stringResource(R.string.nrf_vendor),
                value = displayVendor
            )
        }
        DetailRow(
            label = stringResource(R.string.nrf_last_seen),
            value = "$lastSeenText · ${stringResource(R.string.nrf_scans, device.scanCount)}"
        )

        device.batteryLevel?.let { level ->
            BatteryBanner(level = level)
        }

        if (device.hasHID || device.hasAudio || device.hasBattery) {
            CapabilitySection(device = device)
        }

        if (device.gattServices.isNotEmpty()) {
            GattServicesSection(device = device)
        } else if (device.serviceUuids.isNotEmpty()) {
            AdvertisedUuidsSection(device = device)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!device.isConnected) {
                val (interaction, scale) = rememberPressShrink(pressedScale = 0.96f)
                AccentButton(
                    onClick = { onProfile(device) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .scale(scale),
                    shape = RoundedCornerShape(22.dp),
                    interactionSource = interaction,
                    enabled = !device.isProfiling
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.nrf_profile),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (device.isConnected) {
                val (interaction, scale) = rememberPressShrink(pressedScale = 0.96f)
                Button(
                    onClick = { onDisconnect(device) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .scale(scale),
                    shape = RoundedCornerShape(22.dp),
                    interactionSource = interaction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.nrf_disconnect),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


private val BrandPalette = listOf(
    0xFF1565C0 to 0xFF90CAF9,

    0xFFC62828 to 0xFFEF9A9A,

    0xFF2E7D32 to 0xFFA5D6A7,

    0xFF6A1B9A to 0xFFCE93D8,

    0xFF00695C to 0xFF80CBC4,

    0xFFE65100 to 0xFFFFB74D,

    0xFF283593 to 0xFF9FA8DA,

    0xFFAD1457 to 0xFFF48FB1,

)

private fun brandAccentColor(brand: String, darkTheme: Boolean): Color {
    var hash = 0
    for (ch in brand) hash = hash * 31 + ch.code
    val (light, dark) = BrandPalette[(hash and Int.MAX_VALUE) % BrandPalette.size]
    return Color(if (darkTheme) dark else light)
}

private fun brandInitial(brand: String): String =
    brand.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

@Composable
private fun BrandAvatar(device: NrfDevice, vendor: String?, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    if (vendor != null) {
        val darkTheme = isSystemInDarkTheme()
        val accent = remember(vendor, darkTheme) { brandAccentColor(vendor, darkTheme) }
        Box(
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = brandInitial(vendor),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = accent
            )
        }
    } else {
        val icon = when (device.type) {
            DeviceType.BLE_DEVICE, DeviceType.CLASSIC_DEVICE, DeviceType.DUAL_MODE -> Icons.Filled.Bluetooth
            DeviceType.BEACON -> Icons.Filled.LocationOn
            DeviceType.FAST_PAIR -> Icons.Filled.PhoneAndroid
        }
        Box(
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}


@Composable
private fun VendorChip(vendor: String, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = colorScheme.secondaryContainer,
        contentColor = colorScheme.onSecondaryContainer
    ) {
        Text(
            text = vendor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .widthIn(max = 160.dp)
        )
    }
}

@Composable
private fun ConnectableChip(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = colorScheme.primaryContainer,
        contentColor = colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = stringResource(R.string.nrf_connectable),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}


@Composable
private fun SignalStrengthMeter(rssi: Int, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val bars = when {
        rssi >= -50 -> 4
        rssi >= -60 -> 3
        rssi >= -70 -> 2
        else -> 1
    }
    val filledColor = when (bars) {
        4, 3 -> colorScheme.primary
        2 -> colorScheme.tertiary
        else -> colorScheme.error
    }
    val barHeights = listOf(5.dp, 9.dp, 13.dp, 17.dp)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        barHeights.forEachIndexed { index, barHeight ->
            val active = index < bars
            val barColor by animateColorAsState(
                targetValue = if (active) filledColor else colorScheme.outlineVariant,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "signal_bar_$index"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}


@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            color = colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun BatteryBanner(level: Int, modifier: Modifier = Modifier) {
    val darkTheme = isSystemInDarkTheme()
    val healthyColor = Color(if (darkTheme) 0xFFA5D6A7 else 0xFF1B5E20)
    val accent = if (level >= 30) healthyColor else MaterialTheme.colorScheme.error
    val icon = if (level >= 30) Icons.Filled.BatteryFull else Icons.Filled.BatteryAlert

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.nrf_battery_level),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = "$level%",
                style = MaterialTheme.typography.labelMedium,
                color = accent
            )
        }
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(accent.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((level / 100f).coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapabilitySection(device: NrfDevice, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.nrf_capabilities),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (device.hasHID) {
                CapabilityChip(
                    icon = Icons.Filled.Keyboard,
                    label = stringResource(R.string.nrf_keyboard),
                    container = colorScheme.secondaryContainer,
                    content = colorScheme.onSecondaryContainer
                )
            }
            if (device.hasAudio) {
                CapabilityChip(
                    icon = Icons.Filled.Headphones,
                    label = stringResource(R.string.nrf_audio),
                    container = colorScheme.tertiaryContainer,
                    content = colorScheme.onTertiaryContainer
                )
            }
            if (device.hasBattery) {
                CapabilityChip(
                    icon = Icons.Filled.BatteryFull,
                    label = stringResource(R.string.nrf_battery),
                    container = colorScheme.primaryContainer,
                    content = colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun CapabilityChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GattServicesSection(device: NrfDevice, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.nrf_gatt_count, device.gattServices.size),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
        )
        device.gattServices.take(5).forEach { service ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.surfaceContainerHighest, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = service.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = service.uuid,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (service.characteristics.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.nrf_characteristics_count, service.characteristics.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary
                    )
                }
            }
        }
        if (device.gattServices.size > 5) {
            Text(
                text = stringResource(R.string.nrf_more_services, device.gattServices.size - 5),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.primary
            )
        }
    }
}

@Composable
private fun AdvertisedUuidsSection(device: NrfDevice, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.nrf_advertised_count, device.serviceUuids.size),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
        )
        device.serviceUuids.take(3).forEach { uuid ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.surfaceContainerHighest, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = getServiceName(uuid),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = uuid,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (device.serviceUuids.size > 3) {
            Text(
                text = stringResource(R.string.nrf_more_services, device.serviceUuids.size - 3),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.primary
            )
        }
    }
}


@Composable
private fun deviceDisplayName(device: NrfDevice): String =
    device.name?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
        ?: stringResource(R.string.nrf_unknown_device)

@Composable
private fun signalQualityLabel(rssi: Int): String = stringResource(
    when {
        rssi >= -50 -> R.string.nrf_signal_excellent
        rssi >= -60 -> R.string.nrf_signal_good
        rssi >= -70 -> R.string.nrf_signal_fair
        rssi >= -80 -> R.string.nrf_signal_weak
        else -> R.string.nrf_signal_very_weak
    }
)

@Composable
private fun rememberLastSeenText(lastSeen: Long): String {
    var nowMs by remember(lastSeen) { mutableLongStateOf(lastSeen) }
    LaunchedEffect(lastSeen) {
        while (true) {
            delay(1_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val ageSeconds = (((nowMs - lastSeen).coerceAtLeast(0L)) / 1000L).toInt()
    return when {
        ageSeconds < 5 -> stringResource(R.string.nrf_last_seen_now)
        ageSeconds < 60 -> stringResource(R.string.nrf_last_seen_seconds, ageSeconds)
        ageSeconds < 3600 -> stringResource(R.string.nrf_last_seen_minutes, ageSeconds / 60)
        else -> stringResource(R.string.nrf_last_seen_hours, ageSeconds / 3600)
    }
}
