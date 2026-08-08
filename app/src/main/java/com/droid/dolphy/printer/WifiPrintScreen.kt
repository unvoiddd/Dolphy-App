package com.droid.dolphy.printer

import com.droid.dolphy.DolphyIconButton

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiPrintScreen(
    navController: NavController,
    viewModel: WifiPrintViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    var showManual by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        var name = uri.lastPathSegment ?: "document"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        viewModel.selectDocument(uri, name, mime)
    }

    MaterialBackground(accentColor = accent) {
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                title = stringResource(R.string.wifi_print_title),
                onBack = { navController.popBackStack() },
                accentColor = accent,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::startDiscovery,
                            enabled = !state.scanning,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (state.scanning) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(if (state.scanning) R.string.wifi_print_searching else R.string.wifi_print_scan))
                        }
                        OutlinedButton(
                            onClick = { showManual = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.wifi_print_add))
                        }
                    }
                }
                if (state.scanning) {
                    item {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                if (state.printers.isEmpty() && !state.scanning) {
                    item {
                        EmptyPrinterCard()
                    }
                } else {
                    items(state.printers, key = { it.id }) { printer ->
                        PrinterCard(
                            printer = printer,
                            selected = state.selectedPrinter?.id == printer.id,
                            onClick = { viewModel.selectPrinter(printer) },
                        )
                    }
                }
                state.selectedPrinter?.let { printer ->
                    item {
                        PrinterDetailsCard(
                            printer = printer,
                            capabilities = state.capabilities,
                            loading = state.loadingCapabilities,
                        )
                    }
                }
                state.capabilities?.let { capabilities ->
                    item {
                        DocumentCard(
                            document = state.document,
                            onPick = {
                                picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
                            },
                        )
                    }
                    item {
                        PrintSettingsCard(
                            capabilities = capabilities,
                            options = state.options,
                            onCopies = viewModel::setCopies,
                            onMedia = viewModel::setMedia,
                            onSides = viewModel::setSides,
                            onColor = viewModel::setColorMode,
                            onOrientation = viewModel::setOrientation,
                            onQuality = viewModel::setQuality,
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = viewModel::printDocument,
                                enabled = state.document != null && !state.printing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                            ) {
                                if (state.printing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.5.dp,
                                    )
                                } else {
                                    Icon(Icons.Default.Print, null)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(if (state.printing) R.string.wifi_print_sending else R.string.wifi_print_print))
                            }
                            OutlinedButton(
                                onClick = viewModel::printTestPage,
                                enabled = !state.printing && capabilities.formats.any { it.equals(PrintDocumentPreparer.PWG_MIME, true) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.wifi_print_test_page))
                            }
                        }
                    }
                }
                state.message?.let { message ->
                    item {
                        NoticeCard(Icons.Default.CheckCircle, message, MaterialTheme.colorScheme.primary)
                    }
                }
                state.error?.let { error ->
                    item {
                        NoticeCard(Icons.Default.Error, error, MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showManual) {
        ManualPrinterDialog(
            onDismiss = { showManual = false },
            onAdd = { host, port, path, secure ->
                viewModel.addManual(host, port, path, secure)
                showManual = false
            },
        )
    }
}

@Composable
private fun EmptyPrinterCard() {
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Wifi, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.wifi_print_no_printers), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.wifi_print_no_printers_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrinterCard(printer: IppPrinter, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Print,
                null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(printer.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    printer.uri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PrinterDetailsCard(
    printer: IppPrinter,
    capabilities: PrinterCapabilities?,
    loading: Boolean,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(Icons.Default.Router, stringResource(R.string.wifi_print_printer_info))
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.wifi_print_reading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (capabilities != null) {
                Text(capabilities.displayName, fontWeight = FontWeight.SemiBold)
                capabilities.info?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(printerStateLabel(capabilities.stateCode)) },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp)) },
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(if (printer.uri.startsWith("ipps")) "IPPS" else "IPP") },
                        leadingIcon = { Icon(Icons.Default.Security, null, Modifier.size(16.dp)) },
                    )
                }
                if (capabilities.formats.isNotEmpty()) {
                    Text(
                        capabilities.formats.joinToString(" • ") { formatLabel(it) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentCard(document: PrintDocument?, onPick: () -> Unit) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(Icons.Default.Description, stringResource(R.string.wifi_print_document))
            if (document == null) {
                Text(
                    stringResource(R.string.wifi_print_document_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (document.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.Description,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(document.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(document.mimeType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (document == null) R.string.wifi_print_choose_file else R.string.wifi_print_change_file))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrintSettingsCard(
    capabilities: PrinterCapabilities,
    options: PrintOptions,
    onCopies: (Int) -> Unit,
    onMedia: (String) -> Unit,
    onSides: (String) -> Unit,
    onColor: (String) -> Unit,
    onOrientation: (Int) -> Unit,
    onQuality: (Int) -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(Icons.Default.Settings, stringResource(R.string.wifi_print_settings))
            SettingLabel(stringResource(R.string.wifi_print_copies))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DolphyIconButton(
                    onClick = { onCopies(options.copies - 1) },
                    enabled = options.copies > capabilities.copiesRange.first,
                ) { Icon(Icons.Default.Remove, null) }
                Text(
                    options.copies.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                DolphyIconButton(
                    onClick = { onCopies(options.copies + 1) },
                    enabled = options.copies < capabilities.copiesRange.last,
                ) { Icon(Icons.Default.Add, null) }
            }
            if (capabilities.media.isNotEmpty()) {
                MediaMenu(capabilities.media, options.media, onMedia)
            }
            if (capabilities.sides.isNotEmpty()) {
                ChoiceRow(
                    label = stringResource(R.string.wifi_print_sides),
                    values = capabilities.sides,
                    selected = options.sides,
                    title = ::sidesLabel,
                    onSelect = onSides,
                )
            }
            if (capabilities.colorModes.isNotEmpty()) {
                ChoiceRow(
                    label = stringResource(R.string.wifi_print_color),
                    values = capabilities.colorModes,
                    selected = options.colorMode,
                    title = ::colorLabel,
                    onSelect = onColor,
                )
            }
            if (capabilities.orientations.isNotEmpty()) {
                ChoiceRow(
                    label = stringResource(R.string.wifi_print_orientation),
                    values = capabilities.orientations,
                    selected = options.orientationCode,
                    title = ::orientationLabel,
                    onSelect = onOrientation,
                )
            }
            if (capabilities.qualities.isNotEmpty()) {
                ChoiceRow(
                    label = stringResource(R.string.wifi_print_quality),
                    values = capabilities.qualities,
                    selected = options.qualityCode,
                    title = ::qualityLabel,
                    onSelect = onQuality,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaMenu(media: List<String>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingLabel(stringResource(R.string.wifi_print_paper))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = mediaLabel(selected.orEmpty()),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                media.distinct().take(80).forEach { value ->
                    DropdownMenuItem(
                        text = { Text(mediaLabel(value)) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    values: List<T>,
    selected: T?,
    title: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.distinct().forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(title(value)) },
                )
            }
        }
    }
}

@Composable
private fun ManualPrinterDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int, String, Boolean) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("631") }
    var path by remember { mutableStateOf("ipp/print") }
    var secure by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        icon = { Icon(Icons.Default.Router, null) },
        title = { Text(stringResource(R.string.wifi_print_manual_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.wifi_print_host)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.wifi_print_port)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.wifi_print_path)) },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.wifi_print_secure))
                    Switch(checked = secure, onCheckedChange = { secure = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(host, port.toIntOrNull() ?: 631, path, secure) },
                enabled = host.isNotBlank(),
            ) { Text(stringResource(R.string.wifi_print_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wifi_print_cancel)) } },
    )
}

@Composable
private fun AppCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NoticeCard(icon: ImageVector, message: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Text(message, modifier = Modifier.weight(1f))
        }
    }
}

private fun printerStateLabel(code: Int?): String = when (code) {
    3 -> "Ready"
    4 -> "Printing"
    5 -> "Attention"
    else -> "Connected"
}

private fun formatLabel(value: String): String = when (value.lowercase()) {
    "application/pdf" -> "PDF"
    "image/jpeg" -> "JPEG"
    "image/png" -> "PNG"
    "image/pwg-raster" -> "PWG Raster"
    else -> value.substringAfter('/')
}

private fun mediaLabel(value: String): String = value
    .removePrefix("iso_")
    .removePrefix("na_")
    .replace('_', ' ')
    .replaceFirstChar { it.uppercase() }

private fun sidesLabel(value: String): String = when (value) {
    "one-sided" -> "1 side"
    "two-sided-long-edge" -> "2 sides · long"
    "two-sided-short-edge" -> "2 sides · short"
    else -> value
}

private fun colorLabel(value: String): String = when (value) {
    "color" -> "Color"
    "monochrome" -> "B/W"
    "auto" -> "Auto"
    else -> value
}

private fun orientationLabel(value: Int): String = when (value) {
    3 -> "Portrait"
    4 -> "Landscape"
    5 -> "Reverse landscape"
    6 -> "Reverse portrait"
    else -> "Auto"
}

private fun qualityLabel(value: Int): String = when (value) {
    3 -> "Draft"
    4 -> "Normal"
    5 -> "High"
    else -> value.toString()
}

