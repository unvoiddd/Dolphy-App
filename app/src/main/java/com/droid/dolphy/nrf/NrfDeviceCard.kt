package com.droid.dolphy.nrf

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun NrfDeviceCard(
    device: NrfDevice,
    onConnect: (NrfDevice) -> Unit,
    onProfile: (NrfDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var connectionState by remember { mutableStateOf<ConnectionState?>(null) }
    val colorScheme = MaterialTheme.colorScheme

    val (iconColor, icon) = when (device.type) {
        DeviceType.BLE_DEVICE -> Pair(Color(0xFF2196F3), Icons.Filled.Bluetooth)
        DeviceType.CLASSIC_DEVICE -> Pair(Color(0xFF9C27B0), Icons.Filled.Bluetooth)
        DeviceType.BEACON -> Pair(Color(0xFF4CAF50), Icons.Filled.LocationOn)
        DeviceType.FAST_PAIR -> Pair(Color(0xFFFF9800), Icons.Filled.PhoneAndroid)
        DeviceType.DUAL_MODE -> Pair(Color(0xFFF44336), Icons.Filled.Devices)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }


                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = device.name ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = getDeviceTypeLabel(device.type),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${device.rssi} dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }


                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.height(56.dp)
                ) {
                    LinearProgressIndicator(
                        progress = getSignalStrengthPercent(device.rssi),
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp),
                        color = iconColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }


            if (expanded) {
                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = colorScheme.outlineVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    DetailRow(
                        label = "MAC адрес",
                        value = device.address,
                        icon = Icons.Filled.ContentCopy,
                        valueColor = Color.White
                    )


                    DetailRow(
                        label = "Тип устройства",
                        value = getDeviceTypeLabel(device.type),
                        valueColor = Color.White
                    )


                    DetailRow(
                        label = "Сила сигнала",
                        value = "${device.rssi} dBm (${getSignalStrength(device.rssi)})",
                        valueColor = Color.White
                    )


                    if (device.manufacturerName != null) {
                        DetailRow(
                            label = "Производитель",
                            value = device.manufacturerName,
                            valueColor = Color.White
                        )
                    }


                    if (device.batteryLevel != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color(0xFF4CAF50).copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BatteryFull,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Уровень батареи",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                                Text(
                                    text = "${device.batteryLevel}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }


                    if (device.hasHID || device.hasAudio || device.hasBattery) {
                        Column {
                            Text(
                                text = "Возможности",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (device.hasHID) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF2196F3).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "Клавиатура",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF2196F3),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (device.hasAudio) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF9C27B0).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "Аудио",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF9C27B0),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (device.hasBattery) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "Батарея",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF4CAF50),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }


                    if (device.gattServices.isNotEmpty()) {
                        Column {
                            Text(
                                text = "GATT Сервисы (${device.gattServices.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            device.gattServices.take(5).forEach { service ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp, top = 4.dp)
                                        .background(
                                            colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = service.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = service.uuid,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    if (service.characteristics.isNotEmpty()) {
                                        Text(
                                            text = "${service.characteristics.size} характеристик",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.primary,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (device.gattServices.size > 5) {
                                Text(
                                    text = "+${device.gattServices.size - 5} ещё сервисов",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                )
                            }
                        }
                    }


                    if (device.serviceUuids.isNotEmpty() && device.gattServices.isEmpty()) {
                        Column {
                            Text(
                                text = "Рекламируемые сервисы (${device.serviceUuids.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            device.serviceUuids.take(3).forEach { uuid ->
                                Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                                    Text(
                                        text = getServiceName(uuid),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = uuid,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (device.serviceUuids.size > 3) {
                                Text(
                                    text = "+${device.serviceUuids.size - 3} ещё",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                )
                            }
                        }
                    }


                    if (device.isVulnerable) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color(0xFFFF9800).copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Уязвимо",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9800)
                                )
                                Text(
                                    text = device.vulnerabilityType?.name ?: "Неизвестно",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF9800)
                                )
                            }
                        }
                    }


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        if (!device.isConnected && device.gattServices.isEmpty()) {
                            com.droid.dolphy.AccentButton(
                                onClick = {
                                    connectionState = ConnectionState.CONNECTING
                                    onProfile(device)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary
                                ),
                                enabled = connectionState == null
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Profile",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }


                        if (device.isConnected) {
                            com.droid.dolphy.AccentButton(
                                onClick = {

                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF44336)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LinkOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Disconnect",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }


                        if (device.isVulnerable) {
                            com.droid.dolphy.AccentButton(
                                onClick = {
                                    connectionState = ConnectionState.BYPASS_1

                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800)
                                ),
                                enabled = connectionState == null
                            ) {
                                when (connectionState) {
                                    ConnectionState.CONNECTING -> Text(
                                        text = "Connecting...",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ConnectionState.BYPASS_1 -> Text(
                                        text = "Bypass 1...",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ConnectionState.BYPASS_2 -> Text(
                                        text = "Bypass 2...",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ConnectionState.BYPASS_3 -> Text(
                                        text = "Bypass 3...",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ConnectionState.ERROR -> Text(
                                        text = "Error",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ConnectionState.SUCCESS -> Text(
                                        text = "Success",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    null -> {
                                        Icon(
                                            imageVector = Icons.Filled.Link,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Connect",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class ConnectionState {
    CONNECTING, BYPASS_1, BYPASS_2, BYPASS_3, ERROR, SUCCESS
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    valueColor: Color = Color.Unspecified
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (valueColor != Color.Unspecified) valueColor else colorScheme.onSurface
            )
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

