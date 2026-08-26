package com.droid.dolphy.plugin.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.DolphyIconButton
import com.droid.dolphy.DolphySwitch
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.plugin.PluginIcons
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.model.LoadedPlugin

@Composable
fun PluginManagerScreen(navController: NavController) {
    val plugins by PluginManager.plugins.collectAsState()
    val safeMode by PluginManager.safeMode.collectAsState()

    MaterialBackground(accentColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                title = stringResource(R.string.plugin_manager_title),
                onBack = { navController.popBackStack() },
                accentColor = MaterialTheme.colorScheme.primary,
                alwaysCollapsed = true,
                actions = {
                    DolphyIconButton(onClick = { navController.navigate("plugin_security") }) {
                        Icon(Icons.Default.Settings, stringResource(R.string.plugin_settings_title))
                    }
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 120.dp),
                ) {
                    item("safe_mode") {
                        SafeModeAnimated(safeMode)
                    }
                    items(plugins, key = { it.manifest.id }) { plugin ->
                        PluginCard(plugin, safeMode)
                    }
                    item("install_hint") {
                        Text(
                            text = stringResource(R.string.plugin_open_with_hint),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (plugins.isEmpty()) {
                    Text(
                        stringResource(R.string.plugin_empty_center),
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 36.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SafeModeAnimated(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        SafeModeCard()
    }
}

@Composable
private fun SafeModeCard() {
    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = MaterialTheme.colorScheme.primary,
        contentPadding = 16.dp,
        containerType = "highest",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.HealthAndSafety, null, modifier = Modifier.size(28.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${stringResource(R.string.plugin_safe_mode_title)} (${stringResource(R.string.plugin_safe_mode_on)})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.plugin_safe_mode_enabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DolphySwitch(checked = true, onCheckedChange = { enabled -> if (!enabled) PluginManager.disableSafeMode() })
        }
    }
}

@Composable
private fun PluginCard(plugin: LoadedPlugin, safeMode: Boolean) {
    val context = LocalContext.current
    var menuExpanded by remember(plugin.manifest.id) { mutableStateOf(false) }
    val shareError = stringResource(R.string.plugin_manager_error)
    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = MaterialTheme.colorScheme.primary,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryFixed,
                contentColor = MaterialTheme.colorScheme.onPrimaryFixed,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(PluginIcons.resolve(plugin.manifest.icon), null, modifier = Modifier.size(30.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        plugin.manifest.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (plugin.pinned) {
                        Icon(Icons.Default.PushPin, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    "v${plugin.manifest.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DolphySwitch(
                checked = plugin.enabled,
                onCheckedChange = { PluginManager.setEnabled(plugin.manifest.id, it) },
                enabled = !safeMode,
            )
            Box {
                DolphyIconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (plugin.pinned) R.string.plugin_unpin else R.string.plugin_pin)) },
                        leadingIcon = { Icon(Icons.Default.PushPin, null) },
                        onClick = {
                            menuExpanded = false
                            PluginManager.togglePinned(plugin.manifest.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.plugin_share)) },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            menuExpanded = false
                            PluginManager.sharePlugin(context, plugin.manifest.id).onFailure {
                                Toast.makeText(context, shareError.format(it.message.orEmpty()), Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.plugin_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            PluginManager.deletePlugin(plugin.manifest.id)
                        },
                    )
                }
            }
        }
    }
}
