package com.droid.dolphy.plugin.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.AccentButton
import com.droid.dolphy.DolphyIconButton
import com.droid.dolphy.DolphySwitch
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.plugin.CatalogPlugin
import com.droid.dolphy.plugin.PLUGIN_CATALOG
import com.droid.dolphy.plugin.PluginCatalog
import com.droid.dolphy.plugin.PluginIcons
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.isCatalogVersionNewer
import com.droid.dolphy.plugin.model.LoadedPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PluginManagerScreen(navController: NavController) {
    val plugins by PluginManager.plugins.collectAsState()
    val safeMode by PluginManager.safeMode.collectAsState()
    val catalogStates by PluginCatalog.states.collectAsState()
    val installedById = remember(plugins) { plugins.associateBy { it.manifest.id } }
    val updateCount = PLUGIN_CATALOG.count { entry ->
        val installed = installedById[entry.id]
        installed != null && isCatalogVersionNewer(entry.version, installed.manifest.version)
    }

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
                    item("import_card") {
                        PluginImportCard(navController)
                    }
                    item("catalog_header") {
                        CatalogHeader(updateCount, installedById, safeMode)
                    }
                    items(PLUGIN_CATALOG, key = { "catalog_${it.id}" }) { entry ->
                        CatalogCard(
                            entry = entry,
                            installed = installedById[entry.id],
                            state = catalogStates[entry.id],
                            safeMode = safeMode,
                        )
                    }
                    item("installed_header") {
                        SectionHeader(stringResource(R.string.plugin_catalog_installed_section))
                    }
                    if (plugins.isEmpty()) {
                        item("no_plugins") {
                            Text(
                                stringResource(R.string.plugin_empty_center),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
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
            }
        }
    }
}

@Composable
private fun PluginImportCard(navController: NavController) {
    val context = LocalContext.current
    var showUrlDialog by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.set("plugin_preview_uri", uri.toString())
        navController.navigate("plugin_file_preview")
    }

    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = MaterialTheme.colorScheme.primary,
        contentPadding = 14.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryFixed,
                    contentColor = MaterialTheme.colorScheme.onPrimaryFixed,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, modifier = Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.plugin_import_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.plugin_import_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccentButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.plugin_import_choose_file),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AccentButton(
                    onClick = { showUrlDialog = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.plugin_import_paste_link),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showUrlDialog) {
        PluginUrlDialog(
            onDismiss = { showUrlDialog = false },
            onImported = { uri ->
                showUrlDialog = false
                navController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.set("plugin_preview_uri", uri)
                navController.navigate("plugin_file_preview")
            },
        )
    }
}

@Composable
private fun PluginUrlDialog(
    onDismiss: () -> Unit,
    onImported: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val startDownload = {
        if (!downloading && url.isNotBlank()) {
            downloading = true
            error = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { PluginManager.importFromUrl(url) }
                downloading = false
                result.fold(
                    onSuccess = { uri -> onImported(uri.toString()) },
                    onFailure = { failure ->
                        error = failure.message ?: context.getString(R.string.plugin_import_url_failed)
                    },
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(Icons.Default.Link, null) },
        title = { Text(stringResource(R.string.plugin_import_url_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.plugin_import_url_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !downloading,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.plugin_import_url_hint)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { startDownload() }),
                )
                if (downloading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.plugin_import_url_downloading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            AccentButton(
                onClick = startDownload,
                enabled = !downloading && url.isNotBlank(),
            ) {
                Text(stringResource(R.string.plugin_import_url_action))
            }
        },
        dismissButton = {
            AccentButton(onClick = onDismiss, enabled = !downloading) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun CatalogHeader(
    updateCount: Int,
    installedById: Map<String, LoadedPlugin>,
    safeMode: Boolean,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.plugin_catalog_available_section),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (updateCount > 0 && !safeMode) {
                AccentButton(
                    onClick = {
                        PLUGIN_CATALOG.forEach { entry ->
                            val installed = installedById[entry.id] ?: return@forEach
                            if (isCatalogVersionNewer(entry.version, installed.manifest.version)) {
                                PluginCatalog.install(context, entry)
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.plugin_catalog_update_all))
                }
            }
        }
        Text(
            text = stringResource(R.string.plugin_catalog_subtitle),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatalogCard(
    entry: CatalogPlugin,
    installed: LoadedPlugin?,
    state: PluginCatalog.State?,
    safeMode: Boolean,
) {
    val context = LocalContext.current
    var confirm by remember(entry.id) { mutableStateOf(false) }
    val busy = state?.phase == PluginCatalog.Phase.Downloading || state?.phase == PluginCatalog.Phase.Installing
    val hasUpdate = installed != null && isCatalogVersionNewer(entry.version, installed.manifest.version)
    val permissionsLine = permissionSummary(entry.permissions)
    val sizeLabel = remember(entry.sizeBytes) { formatSize(entry.sizeBytes) }

    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = MaterialTheme.colorScheme.primary,
        contentPadding = 14.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryFixed,
                    contentColor = MaterialTheme.colorScheme.onPrimaryFixed,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(PluginIcons.resolve(entry.icon), null, modifier = Modifier.size(28.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        "${entry.author} · v${entry.version} · $sizeLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    busy -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                    installed == null -> AccentButton(
                        onClick = { confirm = true },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(stringResource(R.string.plugin_catalog_install))
                    }
                    else -> DolphySwitch(
                        checked = installed.enabled,
                        onCheckedChange = { PluginManager.setEnabled(entry.id, it) },
                        enabled = !safeMode,
                    )
                }
            }

            Text(
                text = stringResource(entry.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = permissionsLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (installed != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.plugin_catalog_installed_version, installed.manifest.version),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hasUpdate) {
                        Text(
                            text = stringResource(R.string.plugin_catalog_version_available, entry.version),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        AccentButton(
                            onClick = { confirm = true },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            enabled = !safeMode && !busy,
                        ) {
                            Text(stringResource(R.string.plugin_catalog_update))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.plugin_catalog_up_to_date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when (state?.phase) {
                PluginCatalog.Phase.Downloading -> CatalogStatus(
                    text = stringResource(R.string.plugin_catalog_downloading, state.progress),
                    color = MaterialTheme.colorScheme.primary,
                )
                PluginCatalog.Phase.Installing -> CatalogStatus(
                    text = stringResource(R.string.plugin_catalog_installing),
                    color = MaterialTheme.colorScheme.primary,
                )
                PluginCatalog.Phase.Failed -> CatalogStatus(
                    text = state.message ?: stringResource(R.string.plugin_catalog_error_generic),
                    color = MaterialTheme.colorScheme.error,
                )
                PluginCatalog.Phase.Done -> state.message?.let { message ->
                    CatalogStatus(text = message, color = MaterialTheme.colorScheme.primary)
                }
                null -> Unit
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            icon = { Icon(PluginIcons.resolve(entry.icon), null) },
            title = {
                Text(
                    stringResource(
                        if (hasUpdate) R.string.plugin_catalog_update_title else R.string.plugin_catalog_install_title,
                        entry.name,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.plugin_catalog_install_body,
                        stringResource(entry.descriptionRes),
                        entry.version,
                        sizeLabel,
                        permissionsLine,
                    ),
                )
            },
            confirmButton = {
                AccentButton(
                    onClick = {
                        confirm = false
                        PluginCatalog.install(context, entry)
                    },
                ) {
                    Text(
                        stringResource(
                            if (hasUpdate) R.string.plugin_catalog_update else R.string.plugin_catalog_install,
                        ),
                    )
                }
            },
            dismissButton = {
                AccentButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CatalogStatus(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

@Composable
private fun permissionSummary(ids: List<String>): String {
    val labels = ids.mapNotNull { id -> permissionLabelRes(id)?.let { stringResource(it) } }
    return if (labels.isEmpty()) {
        stringResource(R.string.plugin_catalog_no_permissions)
    } else {
        stringResource(R.string.plugin_catalog_permissions, labels.joinToString(" · "))
    }
}

@StringRes
private fun permissionLabelRes(id: String): Int? = when (id.lowercase()) {
    "wifi" -> R.string.plugin_capability_wifi
    "bluetooth" -> R.string.plugin_capability_bluetooth
    "ble_spam" -> R.string.plugin_capability_ble_spam
    "dex" -> R.string.plugin_capability_dex
    "download" -> R.string.plugin_capability_download
    "files" -> R.string.plugin_capability_files
    "infrared" -> R.string.plugin_capability_infrared
    "network" -> R.string.plugin_capability_network
    "usb" -> R.string.plugin_capability_usb
    "root" -> R.string.plugin_capability_root
    "shizuku" -> R.string.plugin_capability_shizuku
    "shell" -> R.string.plugin_capability_shell
    else -> null
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
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
