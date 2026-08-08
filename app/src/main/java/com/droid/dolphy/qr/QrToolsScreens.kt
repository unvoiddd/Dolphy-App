package com.droid.dolphy.qr

import com.droid.dolphy.DolphyIconButton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.droid.dolphy.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.border
import com.droid.dolphy.M3SegmentedListItem
import com.droid.dolphy.M3SegmentedListItemSpacing
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.OrangeAccent
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.m3SegmentedItems
import androidx.activity.result.contract.ActivityResultContracts

@Serializable
data class SavedQrCode(
    val id: String,
    val name: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

class QrToolsViewModel(context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("DolphyPrefs", Context.MODE_PRIVATE)
    private val _savedQrs = mutableStateOf<List<SavedQrCode>>(emptyList())
    val savedQrs: State<List<SavedQrCode>> = _savedQrs

    init {
        loadQrs()
    }

    private fun loadQrs() {
        val json = prefs.getString("saved_qrs", null)
        if (json != null) {
            try {
                _savedQrs.value = Json.decodeFromString(json)
            } catch (e: Exception) {
                _savedQrs.value = emptyList()
            }
        }
    }

    fun addQr(name: String, content: String): String {
        val id = java.util.UUID.randomUUID().toString()
        val newQr = SavedQrCode(
            id = id,
            name = name,
            content = content
        )
        _savedQrs.value = _savedQrs.value + newQr
        saveQrs()
        return id
    }

    fun deleteQr(id: String) {
        _savedQrs.value = _savedQrs.value.filter { it.id != id }
        saveQrs()
    }

    private fun saveQrs() {
        val json = Json.encodeToString(_savedQrs.value)
        prefs.edit().putString("saved_qrs", json).apply()
    }
}

@Composable
fun QrToolsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: QrToolsViewModel = viewModel { QrToolsViewModel(context) }
    val accentColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accentColor) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionTopBar(
                    title = stringResource(R.string.qr_tools),
                    onBack = { navController.popBackStack() }
                )

                val tools = listOf(
                    Triple(
                        Icons.Default.Audiotrack,
                        stringResource(R.string.qr_audio_spoofer),
                        stringResource(R.string.qr_audio_spoofer_desc),
                    ) to "other/qr_audio_spoofer",
                    Triple(
                        Icons.Default.QrCode,
                        stringResource(R.string.qr_generator_title),
                        stringResource(R.string.qr_generator_desc),
                    ) to "other/qr_generator_main",
                )
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
                    modifier = Modifier.fillMaxSize()
                ) {
                    m3SegmentedItems(tools) { index, count, entry ->
                        val (meta, route) = entry
                        M3SegmentedListItem(
                            index = index,
                            count = count,
                            headline = meta.second,
                            supporting = meta.third,
                            leadingIcon = meta.first,
                            leadingIconTint = accentColor,
                            onClick = { navController.navigate(route) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QrGeneratorMainScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: QrToolsViewModel = viewModel { QrToolsViewModel(context) }
    val savedQrs by viewModel.savedQrs
    var showAddDialog by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accentColor) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionTopBar(
                    title = stringResource(R.string.qr_generator_main_title),
                    onBack = { navController.popBackStack() },
                    actions = {
                        DolphyIconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add QR", tint = accentColor)
                        }
                    }
                )

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (savedQrs.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.qr_no_saved_qrs), color = Color.Gray)
                            }
                        }
                    } else {
                        m3SegmentedItems(savedQrs, key = { it.id }) { index, count, qr ->
                            M3SegmentedListItem(
                                index = index,
                                count = count,
                                headline = qr.name,
                                supporting = qr.content,
                                leadingIcon = Icons.Default.QrCode,
                                leadingIconTint = accentColor,
                                trailingContent = {
                                    DolphyIconButton(onClick = { viewModel.deleteQr(qr.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                                    }
                                },
                                onClick = { navController.navigate("other/qr_detail/${qr.id}") },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.qr_new_qr), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.qr_name_label)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(stringResource(R.string.qr_link_or_text)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank() && content.isNotBlank()) {
                        val newId = viewModel.addQr(name, content)
                        showAddDialog = false
                navController.navigate("other/qr_detail/$newId")
                    }
                }) {
                    Text(stringResource(R.string.nfc_next), color = accentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun QrToolFullCard(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    val accentColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun QrSavedCard(qr: SavedQrCode, onDelete: () -> Unit, onClick: () -> Unit) {
    val accentColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = remember(qr.content) {
                    QrGenerator.generateQrCode(qr.content, 128)
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = qr.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = qr.content,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            DolphyIconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
@Composable
fun QrDetailScreen(qrId: String, navController: NavController) {
    val context = LocalContext.current
    val viewModel: QrToolsViewModel = viewModel { QrToolsViewModel(context) }
    val qr = viewModel.savedQrs.value.find { it.id == qrId }
    val accentColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accentColor) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionTopBar(
                    title = qr?.name ?: "QR Code",
                    onBack = { navController.popBackStack() }
                )

                qr?.let {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(16.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val bitmap = remember(qr.content) {
                                QrGenerator.generateQrCode(qr.content, 512)
                            }
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier
                                        .fillMaxSize(0.8f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = qr.content,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        val clipboardManager = LocalClipboardManager.current
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(qr.content))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Content", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrAudioSpooferScreen(
    navController: NavController,
    viewModel: QrAudioSpooferViewModel = viewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val isStarted by viewModel.isStarted.collectAsState()
    val serverIp by viewModel.serverIp.collectAsState()
    val pageType by viewModel.pageType.collectAsState()
    val customAudios by viewModel.customAudioList.collectAsState()

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.addCustomAudio(it) }
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val scrollState = rememberScrollState()

    val qrUrl = if (serverIp != null) {
        val s = serverIp!!
        if (s.startsWith("http://")) s else "http://$s"
    } else if (isStarted) {
        "Запуск локального сервера…"
    } else {
        "Waiting..."
    }

    val qrBitmap = remember(qrUrl, isStarted) {
        if (isStarted && serverIp != null && qrUrl.startsWith("http")) {
            QrGenerator.generateQrCode(qrUrl, 512)
        } else null
    }

    var expanded by remember { mutableStateOf(false) }
    val customHtmlName by viewModel.customHtmlName.collectAsState()
    val pages = listOf(
        "dolphy" to "Dolphy (Default)",
        "wifi" to "Free WiFi",
        "error" to "Error (Chrome style)",
        "vpn" to "VPN Service",
        "custom_html" to if (customHtmlName != null) "Импорт html ($customHtmlName)" else "Импорт html",
    )
    val isCustomHtml = pageType == "custom_html"

    val htmlPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importHtml(it) }
    }

    val showFirstTimeInfo by viewModel.showFirstTimeInfo.collectAsState()

    if (showFirstTimeInfo) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissFirstTimeInfo() },
            title = { Text(stringResource(R.string.audio_spoof_info_title), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(R.string.audio_spoof_info_text), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissFirstTimeInfo() }) {
                    Text(stringResource(R.string.got_it), color = accentColor)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qr_audio_spoofer), color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    DolphyIconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    if (!isStarted) {
                        DolphyIconButton(onClick = { viewModel.startServer() }) {
                            Icon(Icons.Default.PlayArrow, "Start", tint = accentColor)
                        }
                    } else {
                        DolphyIconButton(onClick = { viewModel.stopServer() }) {
                            Icon(Icons.Default.Stop, "Stop", tint = Color.Red)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    qrBitmap != null -> Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .fillMaxSize(0.8f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentScale = ContentScale.Fit
                    )
                    isStarted -> CircularProgressIndicator(color = accentColor)
                    else -> Text(
                        text = stringResource(R.string.qr_start_server_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(qrUrl, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                if (isStarted && serverIp != null) {
                    val clipboardManager = LocalClipboardManager.current
                    val context = LocalContext.current
                    DolphyIconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(qrUrl))
                            Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy URL",
                            tint = accentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))


            Text("SELECT PAGE TYPE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Card(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            pages.find { it.first == pageType }?.second ?: "Select Page",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surface)
                ) {
                    pages.forEach { (type, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                if (type == "custom_html") {

                                    if (customHtmlName == null) {
                                        htmlPickerLauncher.launch("*/*")
                                    } else {
                                        viewModel.setPageType(type)
                                    }
                                } else {
                                    viewModel.setPageType(type)
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (isCustomHtml) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Импортированный стиль: только логи. Аудио-управление скрыто. " +
                        "В логах: заходы на сайт и весь текст, введённый на странице.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { htmlPickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Заменить HTML")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!isCustomHtml) {

            val deviceCount by viewModel.deviceCount.collectAsState()
            Text("SOUNDBOARD", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Text("Подключено: $deviceCount устройств", color = accentColor, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundButton("", Icons.Default.Message, accentColor) { viewModel.sendCommand("PLAY:iphone_message.mp3") }
                SoundButton("", Icons.Default.Call, accentColor) { viewModel.sendCommand("PLAY:ios_call.mp3") }
            }

            customAudios.chunked(2).forEach { pair ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundButton("", null, accentColor, "🎵") { viewModel.sendCommand("PLAY:${pair[0]}") }
                    if (pair.size > 1) {
                        SoundButton("", null, accentColor, "🎵") { viewModel.sendCommand("PLAY:${pair[1]}") }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundButton("", null, accentColor, "🐷") { viewModel.sendCommand("PLAY:vizg_svini.mp3") }
                SoundButton("", Icons.Default.Add, MaterialTheme.colorScheme.onSurfaceVariant) {
                    audioPickerLauncher.launch("audio/*")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))


            var ttsText by remember { mutableStateOf("") }
            Text("TEXT TO SPEECH", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = ttsText,
                    onValueChange = { ttsText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter text to say...", fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = accentColor,
                        focusedIndicatorColor = accentColor,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (ttsText.isNotBlank()) {
                            viewModel.sendCommand("TTS:$ttsText")
                            ttsText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Speak", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            }


            Text(
                if (isCustomHtml) "ЛОГИ САЙТА" else "TERMINAL LOGS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .padding(12.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs) { log ->
                        Text(
                            text = "> $log",
                            color = accentColor,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun RowScope.SoundButton(label: String, icon: ImageVector?, color: Color, emoji: String? = null, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.height(80.dp).weight(1f),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (emoji != null) {
                Text(emoji, fontSize = 32.sp)
            } else if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(36.dp))
            }
            if (label.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

