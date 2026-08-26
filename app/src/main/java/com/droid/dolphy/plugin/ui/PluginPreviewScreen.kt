package com.droid.dolphy.plugin.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.droid.dolphy.AccentButton
import com.droid.dolphy.DolphyToast
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.plugin.PluginIcons
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.model.PluginPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginPreviewScreen(uriString: String?, onBack: () -> Unit) {
    val uri = remember(uriString) { uriString?.let(Uri::parse) }
    val previewResult by produceState<Result<PluginPreview>?>(initialValue = null, uri) {
        value = if (uri == null) {
            Result.failure(IllegalArgumentException("missing_uri"))
        } else {
            withContext(Dispatchers.IO) { PluginManager.previewFromUri(uri) }
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    var installing by remember(uriString) { mutableStateOf(false) }
    var installed by remember(uriString) { mutableStateOf(false) }
    var installFailure by remember(uriString) { mutableStateOf<String?>(null) }
    val installSuccess = stringResource(R.string.plugin_preview_install_success)
    val installError = stringResource(R.string.plugin_preview_install_error)
    val preview = previewResult?.getOrNull()
    LaunchedEffect(preview?.installed) {
        if (preview?.installed == true) installed = true
    }

    ModalBottomSheet(
        onDismissRequest = onBack,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text(
                text = stringResource(R.string.plugin_preview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Default.Close, stringResource(R.string.cd_close))
            }
        }

        when {
            previewResult == null -> {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            preview == null -> {
                PluginPreviewError(
                    message = previewResult?.exceptionOrNull()?.message.orEmpty(),
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                ) {
                    item { PluginHero(preview, installed) }
                    item { PluginCapabilitySummary(preview.capabilities) }
                    installFailure?.let { failure ->
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ) {
                                Text(failure, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item {
                        AccentButton(
                            onClick = {
                                if (uri == null || installing) return@AccentButton
                                installing = true
                                installFailure = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { PluginManager.installFromUri(uri) }
                                    installing = false
                                    result.onSuccess {
                                        installed = true
                                        DolphyToast.show(installSuccess)
                                        onBack()
                                    }.onFailure {
                                        installFailure = installError.format(it.message.orEmpty())
                                    }
                                }
                            },
                            enabled = !installing,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            if (installing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryFixed,
                                )
                            } else {
                                Icon(
                                    if (installed) Icons.Default.CheckCircle else Icons.Default.Code,
                                    null,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    stringResource(
                                        if (installed) R.string.plugin_preview_update else R.string.plugin_preview_install,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    item {
                        PluginPreviewSection(
                            title = stringResource(R.string.plugin_preview_about),
                            icon = Icons.Default.Description,
                        ) {
                            Text(
                                preview.manifest.description.ifBlank { stringResource(R.string.plugin_preview_no_description) },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        PluginPreviewSection(
                            title = stringResource(R.string.plugin_preview_details),
                            icon = Icons.Default.Person,
                        ) {
                            PreviewDetail(stringResource(R.string.plugin_preview_author), preview.manifest.author.ifBlank { "—" })
                            PreviewDetail(stringResource(R.string.plugin_preview_identifier), preview.manifest.id)
                            PreviewDetail(stringResource(R.string.plugin_preview_file), preview.fileName)
                            PreviewDetail(stringResource(R.string.plugin_preview_size), formatFileSize(preview.sizeBytes))
                        }
                    }
                    item {
                        PluginPreviewSection(
                            title = stringResource(R.string.plugin_preview_security),
                            icon = Icons.Default.Security,
                            emphasized = true,
                        ) {
                            Text(
                                stringResource(R.string.plugin_preview_security_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginHero(preview: PluginPreview, installed: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryFixed,
            contentColor = MaterialTheme.colorScheme.onPrimaryFixed,
            tonalElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    PluginIcons.resolve(preview.manifest.icon),
                    null,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Text(
            preview.manifest.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (preview.manifest.author.isNotBlank()) {
            Text(
                preview.manifest.author,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PreviewChip("v${preview.manifest.version}")
            PreviewChip(preview.runtime.replaceFirstChar { it.uppercase() })
            if (installed) PreviewChip(stringResource(R.string.plugin_preview_installed))
        }
    }
}

@Composable
private fun PluginCapabilitySummary(capabilities: List<String>) {
    val labels = capabilities.map { capabilityLabel(it) }
    PluginPreviewSection(
        title = stringResource(R.string.plugin_preview_permissions),
        icon = Icons.Default.Security,
        emphasized = true,
    ) {
        Text(
            if (labels.isEmpty()) stringResource(R.string.plugin_preview_permissions_none) else labels.joinToString("  •  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun capabilityLabel(id: String): String = stringResource(
    when (id) {
        "dex" -> R.string.plugin_capability_dex
        "download" -> R.string.plugin_capability_download
        "network" -> R.string.plugin_capability_network
        "files" -> R.string.plugin_capability_files
        "ble_spam" -> R.string.plugin_capability_ble_spam
        "bluetooth" -> R.string.plugin_capability_bluetooth
        "wifi" -> R.string.plugin_capability_wifi
        "infrared" -> R.string.plugin_capability_infrared
        "usb" -> R.string.plugin_capability_usb
        "root" -> R.string.plugin_capability_root
        "shizuku" -> R.string.plugin_capability_shizuku
        "shell" -> R.string.plugin_capability_shell
        else -> R.string.plugin_capability_hooks
    },
)

@Composable
private fun PreviewChip(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = null,
    )
}

@Composable
private fun PluginPreviewSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    emphasized: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = MaterialTheme.colorScheme.primary,
        contentPadding = 14.dp,
        containerType = if (emphasized) "highest" else "surface",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun PreviewDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1.4f), textAlign = TextAlign.End, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PluginPreviewError(message: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Description, null, modifier = Modifier.size(42.dp))
                }
            }
            Text(stringResource(R.string.plugin_preview_invalid), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
