package com.droid.dolphy.nfc.ui

import com.droid.dolphy.DolphyIconButton

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.TextGray
import com.droid.dolphy.TintedFrameAnimation
import com.droid.dolphy.nfc.EmulatedNfcTag
import com.droid.dolphy.nfc.NfcTagEmulationStore
import com.droid.dolphy.nfc.NfcViewModel
import com.droid.dolphy.nfc.RootNfcHelper
import com.droid.dolphy.nfc.db.NfcScanEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NfcMasterKeyScreen(navController: NavController, viewModel: NfcViewModel) {
    val isRunning by viewModel.masterKeyRunning.collectAsState()
    val currentKey by viewModel.currentKey.collectAsState()
    val logs by viewModel.masterKeyLogs.collectAsState()
    val accent = MaterialTheme.colorScheme.primary
    val hasRoot = remember { RootNfcHelper.hasRoot() }

    val infinite = rememberInfiniteTransition(label = "nfc_master_key")
    val scale by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "master_key_scale",
    )

    DisposableEffect(Unit) {
        onDispose { viewModel.stopMasterKey() }
    }

    MaterialBackground(accentColor = accent) {
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                transparent = true,
                title = stringResource(R.string.nfc_master_key_title),
                onBack = { navController.popBackStack() },
                accentColor = accent,
                showRootBadge = true,
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(12.dp))
                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 14.dp,
                ) {
                    Text(
                        text = stringResource(R.string.nfc_master_key_root_only_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (hasRoot) {
                            accent.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ) {
                        Text(
                            text = if (hasRoot) {
                                stringResource(R.string.nfc_master_key_status_root)
                            } else {
                                stringResource(R.string.nfc_master_key_status_no_root)
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (hasRoot) accent else MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale),
                    tint = accent,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (isRunning && currentKey.isNotEmpty()) currentKey else "——————",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                    ),
                    color = accent,
                )
                if (isRunning) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.nfc_master_key_scanning),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))
                MaterialCard(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .heightIn(max = 160.dp),
                    contentPadding = 12.dp,
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(logs.take(12)) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        if (logs.isEmpty()) {
                            item {
                                Text(
                                    text = "…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                com.droid.dolphy.MaterialButton(
                    text = if (isRunning) {
                        stringResource(R.string.nfc_master_key_stop)
                    } else {
                        stringResource(R.string.nfc_master_key_start)
                    },
                    onClick = {
                        if (isRunning) viewModel.stopMasterKey()
                        else if (hasRoot) viewModel.startMasterKey()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 120.dp),
                    accentColor = if (isRunning) MaterialTheme.colorScheme.error else accent,
                    enabled = hasRoot || isRunning,
                    isActive = isRunning,
                )
            }
        }
    }
}

@Composable
fun NfcWaitScreen(navController: NavController, viewModel: NfcViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val accent = MaterialTheme.colorScheme.primary
    val adapter = remember(activity) { activity?.let { NfcAdapter.getDefaultAdapter(it) } }
    var nfcReady by remember {
        mutableStateOf(adapter != null && adapter.isEnabled)
    }
    var rootMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val mode = withContext(kotlinx.coroutines.Dispatchers.IO) {
            RootNfcHelper.prepareForRead(context)
        }
        rootMode = mode == "root"
        nfcReady = adapter != null && (adapter.isEnabled || RootNfcHelper.isNfcEnabled(context))
    }

    DisposableEffect(activity, nfcReady) {
        if(activity != null && nfcReady) {
            enableNfcForegroundDispatch(activity, enabled = true)
        }
        onDispose {
            if(activity != null) {
                enableNfcForegroundDispatch(activity, enabled = false)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                NfcTopBar(
                    title = stringResource(R.string.nfc_read_title),
                    onBack = { navController.popBackStack() },
            onHistory = { navController.navigate("other/nfc_history") },
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if(nfcReady) {
                            TintedFrameAnimation(
                                frames = listOf(
                                    R.drawable.mm_nfc_14_frame_01,
                                    R.drawable.mm_nfc_14_frame_02,
                                    R.drawable.mm_nfc_14_frame_03,
                                    R.drawable.mm_nfc_14_frame_04,
                                ),
                                modifier = Modifier.size(width = 53.dp, height = 53.dp),
                                frameDelayMs = 333L,
                                tintColor = accent,
                                contentDescription = "NFC reading animation",
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.passport_bad2_46x49),
                                contentDescription = "NFC unavailable",
                                modifier = Modifier.size(width = 138.dp, height = 147.dp),
                                colorFilter = ColorFilter.tint(
                                    color = accent,
                                    blendMode = BlendMode.Modulate
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if(nfcReady) "Приложите метку к телефону" else "NFC недоступен",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if(nfcReady) {
                                if (rootMode) "Ожидание NFC… (root)" else "Ожидание NFC..."
                            } else {
                                "Включите NFC в настройках телефона"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NfcHistoryScreen(navController: NavController, viewModel: NfcViewModel) {
    val accent = MaterialTheme.colorScheme.primary
    val history by viewModel.history.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                NfcTopBar(
                    title = stringResource(R.string.nfc_history),
                    onBack = { navController.popBackStack() },
                    onHistory = null,
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp),
                ) {
                    items(history) { item ->
                        HistoryRow(item = item) {
                        navController.navigate("other/nfc_result/${item.id}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NfcResultScreen(navController: NavController, viewModel: NfcViewModel, scanId: Long) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val accent = MaterialTheme.colorScheme.primary
    val scanFlow = remember(scanId) { viewModel.scanDetails(scanId) }
    val scan by scanFlow.collectAsState(initial = null)
    var showHex by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var emulationName by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                NfcTopBar(
                    title = stringResource(R.string.nfc_info_title),
                    onBack = { navController.popBackStack() },
            onHistory = { navController.navigate("other/nfc_history") },
                )

                Spacer(modifier = Modifier.height(12.dp))

                if(scan == null) {
                    Text(stringResource(R.string.nfc_loading), color = MaterialTheme.colorScheme.onBackground)
                    return@MaterialBackground
                }
                val link = extractFirstUrl(scan!!.ndefContent)
                val canCopyForEmulation = isEmulationSupported(scan!!)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp),
                ) {
                    item {
                        SectionCard(title = stringResource(R.string.nfc_section_main)) {
                            KeyValue("UID (hex)", scan!!.uidHex)
                            KeyValue("Технологии", scan!!.techListCsv)
                            KeyValue("Writable", scan!!.writable?.let { if(it) "Да" else "Нет" } ?: "—")
                            KeyValue("Размер памяти", scan!!.maxSizeBytes?.let { "$it bytes" } ?: "—")
                        }
                    }

                    if(!scan!!.ndefRecordType.isNullOrBlank()) {
                        item {
                            SectionCard(title = stringResource(R.string.nfc_section_ndef)) {
                                KeyValue("Тип записи", scan!!.ndefRecordType ?: "—")
                                KeyValue("Содержимое", scan!!.ndefContent ?: "—")
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showHex = !showHex }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = if(showHex) "Скрыть HEX" else "Показать HEX",
                                        color = accent,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if(showHex) {
                                    KeyValue("HEX", scan!!.ndefHex ?: "—")
                                }
                            }
                        }
                    }

                    item {
                        SectionCard(title = stringResource(R.string.nfc_section_analysis)) {
                            KeyValue("NTAG", scan!!.ntagType ?: "—")
                            KeyValue("Read-only", scan!!.isReadOnly?.let { if(it) "Да" else "Нет" } ?: "—")
                            KeyValue(
                                "Password protection",
                                when {
                                    scan!!.passwordProtectionSupported != true -> "—"
                                    scan!!.passwordProtectionEnabled == true -> "Включено"
                                    scan!!.passwordProtectionEnabled == false -> "Выключено"
                                    else -> "Неизвестно"
                                },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            KeyValue("Вердикт", scan!!.analysisVerdict)
                        }
                    }

                    if(scan!!.emvDetected) {
                        item {
                            SectionCard(title = stringResource(R.string.nfc_section_emv)) {
                                val lines = scan!!.emvInfo.orEmpty()
                                    .lineSequence()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .toList()
                                if(lines.isEmpty()) {
                                    KeyValue("Тип", "EMV карта")
                                } else {
                                    lines.forEach { line ->
                                        val idx = line.indexOf(':')
                                        if(idx > 0 && idx < line.lastIndex) {
                                            val key = line.substring(0, idx).trim()
                                            val value = line.substring(idx + 1).trim()
                                            KeyValue(key, value)
                                        } else {
                                            Text(
                                                text = line,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!link.isNullOrBlank()) {
                        item {
                            SectionCard(title = stringResource(R.string.nfc_section_link)) {
                                Text(
                                    text = link,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { uriHandler.openUri(link) }
                                        .padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    item {
                        if (canCopyForEmulation) {
                            MaterialCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        emulationName = "Копия ${scan!!.type.ifBlank { "метки" }}"
                                        showCopyDialog = true
                                    },
                                accentColor = accent,
                                cornerRadius = 12.dp,
                            ) {
                                Text(
                                    text = "Копировать метку для эмуляции",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Эту метку эмулировать не получится",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCopyDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCopyDialog = false },
            title = { Text(stringResource(R.string.nfc_copy_for_emulation)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.nfc_enter_name))
                    androidx.compose.material3.OutlinedTextField(
                        value = emulationName,
                        onValueChange = { emulationName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                com.droid.dolphy.AccentButton(
                    onClick = {
                        val name = emulationName.trim().ifBlank { "Копия метки" }
                        val url = buildEmulationUrl(scan)
                        val tags = NfcTagEmulationStore.loadTags(context)
                        val copy = EmulatedNfcTag(
                            id = System.currentTimeMillis(),
                            name = name,
                            url = url
                        )
                        NfcTagEmulationStore.saveTags(context, listOf(copy) + tags)
                        showCopyDialog = false
                        showSavedDialog = true
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                com.droid.dolphy.AccentButton(onClick = { showCopyDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showSavedDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSavedDialog = false },
            title = { Text(stringResource(R.string.nfc_saved_title)) },
            text = { Text(stringResource(R.string.nfc_saved_message)) },
            confirmButton = {
                com.droid.dolphy.AccentButton(
                    onClick = {
                        showSavedDialog = false
                navController.navigate("other/nfc_emulator_list")
                    }
                ) { Text(stringResource(R.string.nfc_go)) }
            },
            dismissButton = {
                com.droid.dolphy.AccentButton(onClick = { showSavedDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}

@Composable
private fun NfcTopBar(
    title: String,
    onBack: () -> Unit,
    onHistory: (() -> Unit)?,
) {
    SectionTopBar(
        transparent = true,
        title = title,
        onBack = onBack,
        actions = {
            if(onHistory != null) {
                DolphyIconButton(onClick = onHistory) {
                    Icon(Icons.Outlined.History, contentDescription = "История")
                }
            }
        },
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = accent,
        cornerRadius = 12.dp,
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = key, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun HistoryRow(item: NfcScanEntity, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    MaterialCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        accentColor = accent,
        cornerRadius = 12.dp,
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.type, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(df.format(Date(item.scannedAtMillis)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("UID: ${item.uidHex}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.analysisVerdict, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun enableNfcForegroundDispatch(activity: Activity, enabled: Boolean) {
    val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
    if(!enabled) {
        runCatching { adapter.disableForegroundDispatch(activity) }
        return
    }

    val intent = Intent(activity, activity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val pending = PendingIntent.getActivity(
        activity,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    val filters = arrayOf(
        IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
        IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
    )
    filters.forEach { it.addCategory(Intent.CATEGORY_DEFAULT) }

    runCatching {
        adapter.enableForegroundDispatch(activity, pending, filters, null)
    }
}

private fun extractFirstUrl(content: String?): String? {
    if(content.isNullOrBlank()) return null
    return Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        .find(content)
        ?.value
}

private fun isEmulationSupported(scan: NfcScanEntity): Boolean {
    val allText = listOf(
        scan.type,
        scan.techListCsv,
        scan.analysisVerdict,
        scan.emvInfo.orEmpty(),
        scan.ntagType.orEmpty(),
    ).joinToString(" ").lowercase(Locale.getDefault())

    val markers = listOf(
        "nfc type 4 tag",
        "javacard",
        "globalplatform",
        "desfire ev1",
        "desfire ev2",
        "desfire ev3",
        "smartmx",
        "emv contactless",
        "desfire",
        "isodep",
    )
    return scan.emvDetected || markers.any { allText.contains(it) }
}

private fun buildEmulationUrl(scan: NfcScanEntity?): String {
    val fromTag = extractFirstUrl(scan?.ndefContent)
    if(!fromTag.isNullOrBlank()) return fromTag
    val uid = scan?.uidHex?.replace(" ", "")?.lowercase(Locale.getDefault()).orEmpty().ifBlank { "unknown" }
    return "https://dolphy.tag/$uid"
}

