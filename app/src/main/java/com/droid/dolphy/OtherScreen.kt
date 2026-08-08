package com.droid.dolphy

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.hid.HidKeyboardActivity
import com.droid.dolphy.plugin.PluginIcons
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.PluginRegistry
import com.droid.dolphy.plugin.model.OtherCardContribution
import com.droid.dolphy.plugin.model.OtherSections

private data class OtherItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color,
    val route: String,
    val isPlugin: Boolean = false,
    val pluginId: String? = null,
    val screenId: String? = null,
    
    val requiresRoot: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherScreen(navController: NavController, spamViewModel: SpamViewModel) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary

    val revision by PluginRegistry.revision.collectAsState()
    val pluginCards by PluginRegistry.otherCards.collectAsState()

    val bySection = remember(revision, pluginCards) { PluginRegistry.otherBySection() }

    fun pluginItems(section: String): List<OtherItem> {
        return bySection[section].orEmpty().map { c ->
            OtherItem(
                icon = PluginIcons.resolve(c.icon),
                title = c.title,
                description = c.description,
                color = c.iconTintArgb?.let { Color(it.toInt()) } ?: Color(0xFF7C4DFF),
                route = "plugin/${c.pluginId}/${c.screenId}",
                isPlugin = true,
                pluginId = c.pluginId,
                screenId = c.screenId,
            )
        }
    }

    MaterialBackground(accentColor = accent) {
        Scaffold(containerColor = Color.Transparent) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp),
            ) {
                item {
                    SectionBlock(
                        title = OtherSections.INFRARED,
                        items = listOf(
                            OtherItem(Icons.Default.Computer, stringResource(R.string.ir_flipper_remotes), stringResource(R.string.other_ir_flipper_desc) + " + Телевизоры", Color(0xFF009688), "other/ir_flipper_home"),
                            OtherItem(Icons.Default.Warning, stringResource(R.string.ir_storm), stringResource(R.string.other_ir_storm_desc), Color(0xFFFF6B6B), "other/ir_storm"),
                            OtherItem(Icons.Default.WifiTethering, stringResource(R.string.ir_jammer), stringResource(R.string.other_ir_jammer_desc), Color(0xFFFF9800), "other/ir_jammer"),
                            OtherItem(Icons.Default.Tv, stringResource(R.string.ir_universal_remotes), stringResource(R.string.other_ir_universal_desc), Color(0xFF4CAF50), "other/universal_remotes_home"),
                        ) + pluginItems(OtherSections.INFRARED),
                        accent = accent,
                        haptics = haptics,
                        onClick = { item -> navigateOther(item, navController, context) },
                    )
                }

                item {
                    SectionBlock(
                        title = OtherSections.BLUETOOTH,
                        items = listOf(
                            OtherItem(Icons.Default.BluetoothAudio, stringResource(R.string.audio_scanner_title), stringResource(R.string.audio_scanner_desc), Color(0xFFE91E63), "other/audio_scanner"),
                            OtherItem(Icons.Default.ElectricScooter, stringResource(R.string.scooter_hack_title), stringResource(R.string.scooter_hack_card_desc), Color(0xFF00BFA5), "other/scooter_hack"),
                            OtherItem(Icons.Default.Bluetooth, "NRF Scanner", stringResource(R.string.nrf_scanner_description), Color(0xFF2196F3), "other/nrf_scanner"),
                            OtherItem(Icons.Default.Chat, "Dolphy Chat", stringResource(R.string.other_dolphy_chat_desc), Color(0xFF2196F3), "other/dolphy_chat_global"),
                            OtherItem(Icons.Default.Keyboard, stringResource(R.string.other_hid), stringResource(R.string.other_hid_desc), Color(0xFF9C27B0), "hid"),
                            OtherItem(Icons.Default.BluetoothDisabled, "Bluetooth Jammer", "L2CAP flood attack", Color(0xFFFF1744), "other/bluetooth_jammer"),
                        ) + pluginItems(OtherSections.BLUETOOTH),
                        accent = accent,
                        haptics = haptics,
                        onClick = { item -> navigateOther(item, navController, context) },
                    )
                }

                item {
                    SectionBlock(
                        title = OtherSections.OTHER,
                        items = listOf(
                            OtherItem(Icons.Outlined.Nfc, stringResource(R.string.other_nfc), stringResource(R.string.other_nfc_desc), Color(0xFF00BCD4), "other/nfc_tools"),
                            OtherItem(Icons.Default.Terminal, stringResource(R.string.other_bad_usb), stringResource(R.string.other_bad_usb_desc), Color(0xFFE65100), "other/bad_usb", requiresRoot = true),
                            OtherItem(Icons.Default.QrCodeScanner, stringResource(R.string.other_qr_tools), stringResource(R.string.other_qr_tools_desc), Color(0xFF8BC34A), "other/qr_tools"),
                            OtherItem(Icons.Filled.WifiOff, "WI-FI Attacks", stringResource(R.string.network_hub_card_description), Color(0xFFD32F2F), "other/network_diagnostic_hub"),
                            OtherItem(Icons.Default.Cast, stringResource(R.string.smarttv_cast_title), stringResource(R.string.smarttv_cast_card_description), Color(0xFF4285F4), "other/smarttv_cast"),
                            OtherItem(Icons.Default.Router, stringResource(R.string.lan_tools_title), stringResource(R.string.lan_tools_subtitle), Color(0xFFFF5722), "other/lan_scanner"),
                        ) + pluginItems(OtherSections.OTHER),
                        accent = accent,
                        haptics = haptics,
                        onClick = { item -> navigateOther(item, navController, context) },
                    )
                }


                item {
                    val pluginSectionCards = pluginItems(OtherSections.PLUGINS)
                    if (pluginSectionCards.isNotEmpty()) {
                        SectionBlock(
                            title = OtherSections.PLUGINS,
                            items = pluginSectionCards,
                            accent = accent,
                            haptics = haptics,
                            onClick = { item -> navigateOther(item, navController, context) },
                        )
                    }
                }


                bySection.keys
                    .filter { !OtherSections.isBuiltin(it) }
                    .sorted()
                    .forEach { section ->
                        item {
                            SectionBlock(
                                title = section,
                                items = pluginItems(section),
                                accent = accent,
                                haptics = haptics,
                                onClick = { item -> navigateOther(item, navController, context) },
                            )
                        }
                    }
            }
        }
    }
}

private fun navigateOther(item: OtherItem, navController: NavController, context: android.content.Context) {
    when {
        item.route == "hid" -> context.startActivity(Intent(context, HidKeyboardActivity::class.java))
        item.isPlugin && item.pluginId != null ->
            navController.navigate("plugin/${item.pluginId}/${item.screenId ?: "main"}")
        else -> navController.navigate(item.route)
    }
}

@Composable
private fun SectionBlock(
    title: String,
    items: List<OtherItem>,
    accent: Color,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onClick: (OtherItem) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        M3SegmentedListSectionHeader(title = title)
        M3SegmentedList(items = items) { index, count, item ->
            M3SegmentedListItem(
                index = index,
                count = count,
                headline = item.title,
                supporting = item.description.takeIf { it.isNotBlank() },
                leadingIcon = item.icon,
                leadingIconTint = item.color,
                showChevron = !item.requiresRoot,
                trailingContent = if (item.requiresRoot) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RootBadge(accentColor = accent)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick(item)
                },
            )
        }
    }
}

@Composable
private fun OtherFunctionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconTint,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

