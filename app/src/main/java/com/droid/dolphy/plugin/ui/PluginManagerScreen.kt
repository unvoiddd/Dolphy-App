package com.droid.dolphy.plugin.ui

import com.droid.dolphy.DolphyIconButton

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.DolphySwitch
import com.droid.dolphy.M3SegmentedListItem
import com.droid.dolphy.M3SegmentedListItemSpacing
import com.droid.dolphy.M3SegmentedListSectionHeader
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.m3SegmentedItems
import com.droid.dolphy.plugin.PluginManager

@Composable
fun PluginManagerScreen(navController: NavController) {
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val plugins by PluginManager.plugins.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }

    val installedMsgTemplate = stringResource(R.string.plugin_manager_installed_msg)
    val installedToastTemplate = stringResource(R.string.plugin_manager_installed_toast)
    val errorTemplate = stringResource(R.string.plugin_manager_error)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = PluginManager.installFromUri(uri)
        result.onSuccess {
            message = installedMsgTemplate.format(it.name)
            Toast.makeText(context, installedToastTemplate.format(it.name), Toast.LENGTH_SHORT).show()
        }.onFailure {
            message = errorTemplate.format(it.message ?: "")
            Toast.makeText(context, errorTemplate.format(it.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    MaterialBackground(accentColor = accent) {
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                title = stringResource(R.string.plugin_manager_title),
                onBack = { navController.popBackStack() },
                accentColor = accent,
            )

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
                contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
            ) {
                item {
                    MaterialCard(Modifier.fillMaxWidth(), accentColor = accent, contentPadding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.plugin_manager_card_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.plugin_manager_card_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        picker.launch(
                                            arrayOf("*/*", "text/*", "application/javascript", "text/javascript"),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.plugin_manager_pick_file))
                                }
                                OutlinedButton(
                                    onClick = { navController.navigate("plugin_about") },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.plugin_manager_about))
                                }
                            }
                            if (message != null) {
                                Text(message!!, style = MaterialTheme.typography.bodySmall, color = accent)
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    M3SegmentedListSectionHeader(title = stringResource(R.string.plugin_manager_installed))
                }

                if (plugins.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.plugin_manager_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                m3SegmentedItems(plugins, key = { it.manifest.id }) { index, count, p ->
                    val kindTag = when {
                        p.manifest.isDesignLibrary -> " · design"
                        p.manifest.isLibrary -> " · library"
                        else -> ""
                    }
                    val subtitle = buildString {
                        append("${p.manifest.id} · v${p.manifest.version}$kindTag")
                        if (p.manifest.description.isNotBlank()) {
                            append("\n")
                            append(p.manifest.description)
                        }
                    }
                    M3SegmentedListItem(
                        index = index,
                        count = count,
                        headline = p.manifest.name,
                        supporting = subtitle,
                        leadingIcon = Icons.Default.Extension,
                        leadingIconTint = if (p.enabled) accent else Color.White.copy(0.35f),
                        showChevron = false,
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DolphySwitch(
                                    checked = p.enabled,
                                    onCheckedChange = { enabled ->
                                        PluginManager.setEnabled(p.manifest.id, enabled)
                                    },
                                )
                                DolphyIconButton(onClick = { PluginManager.deletePlugin(p.manifest.id) }) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

