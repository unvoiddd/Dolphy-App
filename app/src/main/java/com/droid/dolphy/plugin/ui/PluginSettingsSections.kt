package com.droid.dolphy.plugin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.DolphySlider
import com.droid.dolphy.DolphySwitch
import com.droid.dolphy.M3SegmentedListItemSpacing
import com.droid.dolphy.M3SegmentedListSectionHeader
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.SettingsItem
import com.droid.dolphy.getSegmentedShape
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.PluginRegistry
import com.droid.dolphy.plugin.PluginIcons
import com.droid.dolphy.plugin.model.SettingsItemContribution

@Composable
fun PluginSettingsSections(navController: NavController) {
    val sections by PluginRegistry.settingsSections.collectAsState()
    val revision by PluginRegistry.revision.collectAsState()
    sections.sortedBy { it.order }.forEach { section ->
        Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
            M3SegmentedListSectionHeader(section.title.uppercase())
            section.items.forEachIndexed { index, item ->
                when (item) {
                    is SettingsItemContribution.Header -> Text(
                        item.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
                    )
                    is SettingsItemContribution.SwitchItem -> {
                        var checked by remember(item.pluginId, item.key, revision) {
                            mutableStateOf(
                                PluginManager.getPluginSetting(item.pluginId, item.key, item.defaultValue.toString()).toBooleanStrictOrNull()
                                    ?: item.defaultValue,
                            )
                        }
                        MaterialCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = MaterialTheme.colorScheme.primary,
                            shape = getSegmentedShape(index, section.items.size),
                            contentPadding = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PluginSettingText(item.title, item.subtitle, Modifier.weight(1f))
                                DolphySwitch(checked = checked, onCheckedChange = {
                                    checked = it
                                    PluginManager.setPluginSetting(item.pluginId, item.key, it.toString())
                                })
                            }
                        }
                    }
                    is SettingsItemContribution.SliderItem -> {
                        val safeMax = item.max.coerceAtLeast(item.min + 0.0001f)
                        var value by remember(item.pluginId, item.key, revision) {
                            mutableFloatStateOf(
                                PluginManager.getPluginSetting(item.pluginId, item.key, item.defaultValue.toString())
                                    .toFloatOrNull()?.coerceIn(item.min, safeMax) ?: item.defaultValue.coerceIn(item.min, safeMax),
                            )
                        }
                        MaterialCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = MaterialTheme.colorScheme.primary,
                            shape = getSegmentedShape(index, section.items.size),
                            contentPadding = 12.dp,
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PluginSettingText(item.title, item.subtitle, Modifier.weight(1f))
                                    Text(value.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                DolphySlider(
                                    value = value,
                                    onValueChange = { value = it },
                                    onValueChangeFinished = { PluginManager.setPluginSetting(item.pluginId, item.key, value.toString()) },
                                    valueRange = item.min..safeMax,
                                    steps = item.steps.coerceAtLeast(0),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                    }
                    is SettingsItemContribution.NavItem -> PluginSettingsNavigationCard(
                        item.title,
                        item.subtitle,
                        item.icon,
                        index,
                        section.items.size,
                    ) { navController.navigate("plugin/${item.pluginId}/${item.screenId}") }
                    is SettingsItemContribution.CardItem -> PluginSettingsNavigationCard(
                        item.title,
                        item.subtitle,
                        item.icon,
                        index,
                        section.items.size,
                        enabled = item.screenId != null,
                    ) { item.screenId?.let { navController.navigate("plugin/${item.pluginId}/$it") } }
                }
            }
        }
    }
}

@Composable
private fun PluginSettingText(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PluginSettingsNavigationCard(
    title: String,
    subtitle: String,
    icon: String,
    index: Int,
    count: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    MaterialCard(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        accentColor = MaterialTheme.colorScheme.primary,
        shape = getSegmentedShape(index, count),
        contentPadding = 0.dp,
    ) {
        SettingsItem(onClick = onClick) {
            Icon(PluginIcons.resolve(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            PluginSettingText(title, subtitle, Modifier.weight(1f))
            if (enabled) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
