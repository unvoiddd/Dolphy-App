package com.droid.dolphy.hid

import com.droid.dolphy.DolphyIconButton

import android.annotation.SuppressLint
import android.provider.OpenableColumns
import android.widget.Toast
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.droid.dolphy.OrangeAccent
import com.droid.dolphy.MaterialCard
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.droid.dolphy.R
import androidx.annotation.StringRes
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import com.droid.dolphy.trackBadHidRun

private enum class PcSection(@StringRes val titleRes: Int, val icon: ImageVector) {
    Mouse(R.string.hid_pc_mouse, Icons.Default.Mouse),
    Keyboard(R.string.hid_pc_keyboard, Icons.Default.Keyboard),
    Controls(R.string.hid_pc_controls, Icons.Default.Tune),
    BadHid(R.string.hid_pc_bad_hid, Icons.Default.Terminal),
}

private data class DuckyScriptItem(
    val id: Long,
    val title: String,
    val commands: List<DuckyCommand>,
    val running: Boolean = false,
    val progress: Float = 0f,
)

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcHidWorkspaceScreen(
    hidConnection: Connection,
    capsLock: Boolean,
    onBack: () -> Unit,
    sensitivityMove: Float = 1.5f,
    sensitivityScroll: Float = 0.4f
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val badHidPrefs = remember(context) {
        context.getSharedPreferences("bad_hid_scripts", Context.MODE_PRIVATE)
    }

    val parseFailMsg = stringResource(R.string.hid_ducky_parse_fail)
    val importErrorMsg = stringResource(R.string.hid_ducky_import_error)
    val winTitle = stringResource(R.string.hid_flipper_win)
    val macTitle = stringResource(R.string.hid_flipper_mac)
    val winMissingMsg = stringResource(R.string.hid_script_flipper_win_missing)
    val macMissingMsg = stringResource(R.string.hid_script_flipper_mac_missing)
    val notepadHintMsg = stringResource(R.string.hid_notepad_text_hint)
    val notepadFailMsg = stringResource(R.string.hid_notepad_send_failed)

    var section by remember { mutableStateOf(PcSection.Mouse) }
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)

    val scripts = remember { mutableStateListOf<DuckyScriptItem>() }
    val scriptJobs = remember { mutableStateMapOf<Long, Job>() }
    val selectedScriptIds = remember { mutableStateListOf<Long>() }
    var badHidEditMode by remember { mutableStateOf(false) }
    var flipperWinRunning by remember { mutableStateOf(false) }
    var flipperWinProgress by remember { mutableStateOf(0f) }
    var flipperWinJob by remember { mutableStateOf<Job?>(null) }
    var flipperMacRunning by remember { mutableStateOf(false) }
    var flipperMacProgress by remember { mutableStateOf(0f) }
    var flipperMacJob by remember { mutableStateOf<Job?>(null) }
    var noteText by remember { mutableStateOf("Hello from Dolphy!") }
    var targetUrl by remember { mutableStateOf("") }
    var notepadRunning by remember { mutableStateOf(false) }
    var notepadJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        scripts.clear()
        val persisted = loadPersistedScripts(badHidPrefs)
        if (persisted.isNotEmpty()) {
            scripts.addAll(persisted)
        } else {

            scripts.addAll(loadBundledScripts(context))
            persistScripts(badHidPrefs, scripts)
        }
    }

    fun updateScript(itemId: Long, transform: (DuckyScriptItem) -> DuckyScriptItem) {
        val idx = scripts.indexOfFirst { it.id == itemId }
        if (idx >= 0) scripts[idx] = transform(scripts[idx])
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val loaded = withContext(Dispatchers.IO) {
                    val content = runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    }.getOrDefault("")
                    val title = runCatching {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull()?.removeSuffix(".txt") ?: "Script ${scripts.size + 1}"
                    if (content.isBlank()) {
                        null
                    } else {
                        val parsed = DuckyUtils.parse(content)
                        if (parsed.isEmpty()) null else DuckyScriptItem(
                            id = System.currentTimeMillis(),
                            title = title,
                            commands = parsed
                        )
                    }
                }
                if (loaded != null) {
                    scripts.add(loaded)
                    persistScripts(badHidPrefs, scripts)
                    selectedScriptIds.clear()
                    badHidEditMode = false
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    Toast.makeText(context, parseFailMsg, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(context, importErrorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.hid_pc_modes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(12.dp))
                PcSection.entries.forEach { item ->
                    com.droid.dolphy.AccentButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            section = item
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (section == item) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            contentColor = if (section == item) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    ) {
                        Icon(item.icon, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(item.titleRes))
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.hid_pc_keyboard) + ": " + stringResource(section.titleRes)) },
                    navigationIcon = {
                        DolphyIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    actions = {
                        DolphyIconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.cd_menu))
                        }
                    }
                )
            },
            floatingActionButton = {
                if (section == PcSection.BadHid) {
                    FloatingActionButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        try {
                            hidConnection.releaseAllKeyboard()
                            hidConnection.releaseAllMouseButtons()
                        } catch (e: SecurityException) {
                        }
                        importLauncher.launch(arrayOf("text/plain", "text/*"))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.hid_import_duckyscript))
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp)
            ) {
                when (section) {
                    PcSection.Mouse -> PcMouseSection(
                        hidConnection = hidConnection,
                        sensitivityMove = sensitivityMove,
                        sensitivityScroll = sensitivityScroll
                    )
                    PcSection.Keyboard -> PcKeyboardSection(hidConnection, capsLock)
                    PcSection.Controls -> PcConsumerControlsSection(hidConnection)
                    PcSection.BadHid -> BadHidSection(
                        scripts = scripts,
                        editMode = badHidEditMode,
                        selectedScriptIds = selectedScriptIds.toSet(),
                        flipperWinRunning = flipperWinRunning,
                        flipperWinProgress = flipperWinProgress,
                        flipperMacRunning = flipperMacRunning,
                        flipperMacProgress = flipperMacProgress,
                        noteText = noteText,
                        targetUrl = targetUrl,
                        notepadRunning = notepadRunning,
                        onToggleEditMode = {
                            badHidEditMode = !badHidEditMode
                            if (!badHidEditMode) selectedScriptIds.clear()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onToggleScriptSelection = { id ->
                            if (selectedScriptIds.contains(id)) {
                                selectedScriptIds.remove(id)
                            } else {
                                selectedScriptIds.add(id)
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDeleteSelected = {
                            if (selectedScriptIds.isEmpty()) return@BadHidSection
                            val toDelete = selectedScriptIds.toSet()
                            toDelete.forEach { id ->
                                scriptJobs.remove(id)?.cancel()
                            }
                            scripts.removeAll { it.id in toDelete }
                            persistScripts(badHidPrefs, scripts)
                            selectedScriptIds.clear()
                            badHidEditMode = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onNoteTextChange = { noteText = it },
                        onTargetUrlChange = { targetUrl = it },
                        onOpenUrl = {
                            val text = sanitizeNotepadText(targetUrl).trim()
                            if (text.isBlank()) {
                                Toast.makeText(context, notepadHintMsg, Toast.LENGTH_SHORT).show()
                                return@BadHidSection
                            }
                            val finalUrl = if (text.contains(".") && !text.contains(" ")) {
                                if (text.startsWith("http")) text else "https://$text"
                            } else {
                                "https://www.google.com/search?q=${text.replace(" ", "+")}"
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                try {

                                    hidConnection.modifierDown("MOD_LMETA")
                                    delay(50)
                                    hidConnection.keyDown("R")
                                    delay(50)
                                    hidConnection.keyUp("R")
                                    delay(50)
                                    hidConnection.modifierUp("MOD_LMETA")
                                    delay(800)
                                    DuckyUtils.typeText(hidConnection, finalUrl)
                                    DuckyUtils.tapKey(hidConnection, "ENTER")
                                } catch (e: Exception) {}
                            }
                        },
                        onFlipperWin = {
                            if (flipperWinRunning) {
                                flipperWinJob?.cancel()
                                flipperWinJob = null
                                flipperWinRunning = false
                                flipperWinProgress = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                flipperWinRunning = true
                                flipperWinProgress = 0f
                                flipperWinJob = scope.launch {
                                    try {
                                        val commands = loadBundledDuckyScript(context, "Flipper/demo_windows.txt")
                                        if (commands.isEmpty()) {
                                            Toast.makeText(context, winMissingMsg, Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        runScript(
                                            hidConnection = hidConnection,
                                            context = context,
                                            script = DuckyScriptItem(
                                                id = -101L,
                                                title = winTitle,
                                                commands = commands
                                            ),
                                            onProgress = { p -> flipperWinProgress = p }
                                        )
                                    } finally {
                                        flipperWinRunning = false
                                        flipperWinProgress = 0f
                                        flipperWinJob = null
                                    }
                                }
                            }
                        },
                        onFlipperMac = {
                            if (flipperMacRunning) {
                                flipperMacJob?.cancel()
                                flipperMacJob = null
                                flipperMacRunning = false
                                flipperMacProgress = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                flipperMacRunning = true
                                flipperMacProgress = 0f
                                flipperMacJob = scope.launch {
                                    try {
                                        val commands = loadBundledDuckyScript(context, "Flipper/demo_macos.txt")
                                        if (commands.isEmpty()) {
                                            Toast.makeText(context, macMissingMsg, Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        runScript(
                                            hidConnection = hidConnection,
                                            context = context,
                                            script = DuckyScriptItem(
                                                id = -102L,
                                                title = macTitle,
                                                commands = commands
                                            ),
                                            onProgress = { p -> flipperMacProgress = p }
                                        )
                                    } finally {
                                        flipperMacRunning = false
                                        flipperMacProgress = 0f
                                        flipperMacJob = null
                                    }
                                }
                            }
                        },
                        onSendToNotepad = {
                            if (notepadRunning) {
                                notepadJob?.cancel()
                                notepadJob = null
                                notepadRunning = false
                            } else {
                                val text = sanitizeNotepadText(noteText)
                                if (text.isBlank()) {
                                    Toast.makeText(context, notepadHintMsg, Toast.LENGTH_SHORT).show()
                                    return@BadHidSection
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                notepadRunning = true
                                notepadJob = scope.launch {
                                    runCatching { sendToNotepad(hidConnection, text) }
                                        .onFailure {
                                            Toast.makeText(context, notepadFailMsg, Toast.LENGTH_SHORT).show()
                                        }
                                    notepadRunning = false
                                    notepadJob = null
                                }
                            }
                        },
                        onScriptClick = { script ->
                            val running = scriptJobs[script.id]
                            if (running != null) {
                                running.cancel()
                                scriptJobs.remove(script.id)
                                updateScript(script.id) { it.copy(running = false, progress = 0f) }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val job = scope.launch {
                                    runScript(
                                        hidConnection = hidConnection,
                                        context = context,
                                        script = script,
                                        onProgress = { progress ->
                                            updateScript(script.id) { it.copy(running = true, progress = progress) }
                                        }
                                    )
                                    updateScript(script.id) { it.copy(running = false, progress = 0f) }
                                    scriptJobs.remove(script.id)
                                }
                                scriptJobs[script.id] = job
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PcMouseSection(
    hidConnection: Connection,
    sensitivityMove: Float = 1.5f,
    sensitivityScroll: Float = 0.4f
) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.hid_touchpad),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Axon(
                modifier = Modifier.fillMaxSize(),
                onTap = {
                    try {
                        hidConnection.mouseClick(0)
                    } catch (e: SecurityException) {
                    }
                },
                onTapDragStart = {
                    try {
                        hidConnection.mouseDown(0)
                    } catch (e: SecurityException) {
                    }
                },
                onTapDragStop = {
                    try {
                        hidConnection.mouseUp(0)
                    } catch (e: SecurityException) {
                    }
                },
                onSlide = { dx, dy ->
                    try {
                        hidConnection.mouseMove(dx, dy)
                    } catch (e: SecurityException) {
                    }
                },
                onScroll = { delta, horizontal ->
                    try {
                        hidConnection.mouseWheel(delta, horizontal)
                    } catch (e: SecurityException) {
                    }
                },
                enabled = true,
                sensitivityMove = sensitivityMove,
                sensitivityScroll = sensitivityScroll
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Plum(
                modifier = Modifier.weight(1f),
                onDown = {
                    try {
                        hidConnection.mouseDown(0)
                    } catch (e: SecurityException) {
                    }
                },
                onUp = {
                    try {
                        hidConnection.mouseUp(0)
                    } catch (e: SecurityException) {
                    }
                },
                label = stringResource(R.string.hid_mouse_button_left)
            )
            Plum(
                modifier = Modifier.weight(1f),
                onDown = {
                    try {
                        hidConnection.mouseDown(2)
                    } catch (e: SecurityException) {
                    }
                },
                onUp = {
                    try {
                        hidConnection.mouseUp(2)
                    } catch (e: SecurityException) {
                    }
                },
                label = stringResource(R.string.hid_mouse_button_middle)
            )
            Plum(
                modifier = Modifier.weight(1f),
                onDown = {
                    try {
                        hidConnection.mouseDown(1)
                    } catch (e: SecurityException) {
                    }
                },
                onUp = {
                    try {
                        hidConnection.mouseUp(1)
                    } catch (e: SecurityException) {
                    }
                },
                label = stringResource(R.string.hid_mouse_button_right)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Plum(
                modifier = Modifier.weight(1f),
                onDown = {
                    try {
                        hidConnection.mouseDown(3)
                    } catch (e: SecurityException) {
                    }
                },
                onUp = {
                    try {
                        hidConnection.mouseUp(3)
                    } catch (e: SecurityException) {
                    }
                },
                label = stringResource(R.string.hid_mouse_button_back)
            )
            Plum(
                modifier = Modifier.weight(1f),
                onDown = {
                    try {
                        hidConnection.mouseDown(4)
                    } catch (e: SecurityException) {
                    }
                },
                onUp = {
                    try {
                        hidConnection.mouseUp(4)
                    } catch (e: SecurityException) {
                    }
                },
                label = stringResource(R.string.hid_mouse_button_forward)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PcIconControlButton(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = stringResource(R.string.hid_mute),
                onClick = {
                    scope.launch { sendMediaTap(hidConnection, "VOLUMEMUTE") }
                }
            )
            PcIconControlButton(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.VolumeDown,
                contentDescription = stringResource(R.string.hid_volume_down_action),
                onClick = {
                    scope.launch { sendMediaTap(hidConnection, "VOLUMEDOWN") }
                }
            )
            PcIconControlButton(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.hid_volume_up_action),
                onClick = {
                    scope.launch { sendMediaTap(hidConnection, "VOLUMEUP") }
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PcIconControlButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.hid_reboot),
                onClick = {
                    scope.launch { runWinRunCommand(hidConnection, "shutdown /r /t 0") }
                }
            )
            PcIconControlButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.PowerSettingsNew,
                contentDescription = stringResource(R.string.hid_shutdown),
                onClick = {
                    scope.launch { runWinRunCommand(hidConnection, "shutdown /s /t 0") }
                }
            )
        }
    }
}

@Composable
private fun PcIconControlButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    com.droid.dolphy.AccentButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun PcKeyboardSection(hidConnection: Connection, capsLock: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val keyboardLong = maxHeight
            val keyboardShort = maxWidth
            val scale = keyboardLong / 855.dp
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .requiredWidth(keyboardLong)
                        .requiredHeight(keyboardShort)
                        .graphicsLayer {
                            rotationZ = 90f
                            transformOrigin = TransformOrigin.Center
                            clip = false
                        }
                ) {
                    Keyboard(
                        hidConnection = hidConnection,
                        togglePlum = { },
                        settingsPlum = { },
                        capsLock = capsLock,
                        scale = scale,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

@Composable
private fun BadHidSection(
    scripts: List<DuckyScriptItem>,
    editMode: Boolean,
    selectedScriptIds: Set<Long>,
    flipperWinRunning: Boolean,
    flipperWinProgress: Float,
    flipperMacRunning: Boolean,
    flipperMacProgress: Float,
    noteText: String,
    targetUrl: String,
    notepadRunning: Boolean,
    onToggleEditMode: () -> Unit,
    onToggleScriptSelection: (Long) -> Unit,
    onDeleteSelected: () -> Unit,
    onNoteTextChange: (String) -> Unit,
    onTargetUrlChange: (String) -> Unit,
    onOpenUrl: () -> Unit,
    onFlipperWin: () -> Unit,
    onFlipperMac: () -> Unit,
    onSendToNotepad: () -> Unit,
    onScriptClick: (DuckyScriptItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val anyActive = flipperWinRunning || flipperMacRunning || notepadRunning || scripts.any { it.running }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.hid_pc_bad_hid),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                EviStatusIcon(
                    isActive = anyActive,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(8.dp))
                if (editMode && selectedScriptIds.isNotEmpty()) {
                    DolphyIconButton(onClick = onDeleteSelected) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.hid_delete_selected),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                DolphyIconButton(onClick = onToggleEditMode) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = if (editMode) stringResource(R.string.hid_clear_selection) else stringResource(R.string.hid_select_scripts)
                    )
                }
            }
        }
        ActionProgressCard(
            onClick = onFlipperWin,
            title = stringResource(R.string.hid_flipper_win),
            running = flipperWinRunning,
            progress = flipperWinProgress,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            idleIcon = Icons.Default.Terminal
        )
        ActionProgressCard(
            onClick = onFlipperMac,
            title = stringResource(R.string.hid_flipper_mac),
            running = flipperMacRunning,
            progress = flipperMacProgress,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            idleIcon = Icons.Default.Terminal
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = targetUrl,
                onValueChange = onTargetUrlChange,
                modifier = Modifier.weight(1f),
                label = { Text("URL to open") },
                placeholder = { Text("google.com") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Language, null) }
            )
            com.droid.dolphy.AccentButton(
                onClick = onOpenUrl,
                enabled = targetUrl.isNotBlank(),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(Icons.Default.Link, null)
                Spacer(Modifier.width(8.dp))
                Text("Open")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = noteText,
                onValueChange = onNoteTextChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.hid_notepad_label)) },
                singleLine = true
            )
            com.droid.dolphy.AccentButton(
                onClick = onSendToNotepad,
                enabled = noteText.isNotBlank(),
                modifier = Modifier.height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (notepadRunning) stringResource(R.string.stop) else stringResource(R.string.hid_notepad_output))
            }
        }

        HorizontalDivider()

        scripts.forEach { script ->
            val progress by animateFloatAsState(targetValue = script.progress, label = "script-progress")
            if (editMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedScriptIds.contains(script.id),
                        onCheckedChange = { onToggleScriptSelection(script.id) }
                    )
                    ActionProgressCard(
                        onClick = { onToggleScriptSelection(script.id) },
                        title = script.title,
                        running = script.running,
                        progress = progress,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        idleIcon = Icons.Default.PlayArrow
                    )
                }
            } else {
                ActionProgressCard(
                    onClick = { onScriptClick(script) },
                    title = script.title,
                    running = script.running,
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    idleIcon = Icons.Default.PlayArrow
                )
            }
        }

        if (scripts.isEmpty()) {
            Text(
                text = stringResource(R.string.hid_import_ducky_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun ActionProgressCard(
    onClick: () -> Unit,
    title: String,
    running: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    idleIcon: ImageVector,
) {
    val animatedProgress by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "outline-progress")
    Card(
        modifier = modifier
            .progressOutline(animatedProgress, MaterialTheme.colorScheme.primary)
            .height(72.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
            Icon(
                imageVector = if (running) Icons.Default.Square else idleIcon,
                contentDescription = null,
                tint = contentColor
            )
        }
    }
}

private fun Modifier.progressOutline(
    progress: Float,
    color: androidx.compose.ui.graphics.Color,
    strokeWidthDp: Float = 3f,
): Modifier = this.drawBehind {
    val clamped = progress.coerceIn(0f, 1f)
    if (clamped <= 0f) return@drawBehind
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@drawBehind

    val stroke = strokeWidthDp * density
    var remain = (2f * (w + h)) * clamped

    fun drawSegment(from: Offset, to: Offset) {
        drawLine(
            color = color,
            start = from,
            end = to,
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }

    val top = minOf(remain, w)
    if (top > 0f) {
        drawSegment(Offset(0f, 0f), Offset(top, 0f))
        remain -= top
    }
    val right = minOf(remain, h)
    if (right > 0f) {
        drawSegment(Offset(w, 0f), Offset(w, right))
        remain -= right
    }
    val bottom = minOf(remain, w)
    if (bottom > 0f) {
        drawSegment(Offset(w, h), Offset(w - bottom, h))
        remain -= bottom
    }
    val left = minOf(remain, h)
    if (left > 0f) {
        drawSegment(Offset(0f, h), Offset(0f, h - left))
    }
}

private suspend fun sendMediaTap(connection: Connection, key: String) {
    try {
        connection.mediaDown(key)
        delay(55)
        connection.mediaUp(key)
        delay(30)
    } catch (e: SecurityException) {
    }
}

private suspend fun runWinRunCommand(connection: Connection, command: String) {
    DuckyUtils.tapModifiersAndKey(connection, modifiers = listOf("MOD_LMETA"), key = "R")
    delay(360)
    DuckyUtils.typeText(connection, command)
    DuckyUtils.tapKey(connection, "ENTER")
}

private suspend fun sendToNotepad(connection: Connection, text: String) {
    DuckyUtils.tapModifiersAndKey(connection, modifiers = listOf("MOD_LMETA"), key = "R")
    delay(420)
    DuckyUtils.typeText(connection, "notepad")
    DuckyUtils.tapKey(connection, "ENTER")
    delay(900)
    DuckyUtils.typeText(connection, text)
}

private suspend fun loadBundledDuckyScript(context: Context, assetPath: String): List<DuckyCommand> {
    val raw = withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    if (raw.isBlank()) return emptyList()
    return DuckyUtils.parse(raw)
}

private suspend fun runScript(
    hidConnection: Connection,
    context: Context,
    script: DuckyScriptItem,
    onProgress: (Float) -> Unit,
) {
    if (script.commands.isEmpty()) return
    trackBadHidRun(context)
    DuckyUtils.execute(hidConnection, script.commands, onProgress)
}

private fun sanitizeNotepadText(input: String): String {
    val allowed = StringBuilder()
    input.forEach { ch ->
        if (
            ch in 'a'..'z' ||
            ch in 'A'..'Z' ||
            ch in '0'..'9' ||
            ch == ' ' || ch == '\n' ||
            ch == '-' || ch == '_' || ch == '=' || ch == '+' ||
            ch == '.' || ch == ',' || ch == '/' || ch == ':' || ch == ';' ||
            ch == '\\' || ch == '"' || ch == '\'' || ch == '(' || ch == ')' ||
            ch == '!' || ch == '?' || ch == '@' || ch == '#' || ch == '$' ||
            ch == '%' || ch == '^' || ch == '&' || ch == '*'
        ) {
            allowed.append(ch)
        }
    }
    return allowed.toString().trim()
}

private fun loadBundledScripts(context: Context): List<DuckyScriptItem> {
    return runCatching {
        val assetManager = context.assets
        val scriptFiles = assetManager.list("bad_usb_scripts") ?: return@runCatching emptyList()

        buildList {
            scriptFiles.forEach { fileName ->
                if (fileName.endsWith(".txt")) {
                    val content = assetManager.open("bad_usb_scripts/$fileName")
                        .bufferedReader()
                        .use { it.readText() }

                    val commands = DuckyUtils.parse(content)
                    if (commands.isNotEmpty()) {
                        val title = fileName.removeSuffix(".txt").replace('_', ' ')
                        add(
                            DuckyScriptItem(
                                id = title.hashCode().toLong(),
                                title = title,
                                commands = commands
                            )
                        )
                    }
                }
            }
        }
    }.getOrDefault(emptyList())
}

private fun persistScripts(prefs: android.content.SharedPreferences, scripts: List<DuckyScriptItem>) {
    val root = JSONArray()
    scripts.forEach { item ->
        val itemObj = JSONObject()
            .put("id", item.id)
            .put("title", item.title)
        val commands = JSONArray()
        item.commands.forEach { cmd ->
            val cmdObj = JSONObject()
            when (cmd) {
                is DuckyCommand.Delay -> {
                    cmdObj.put("type", "delay")
                    cmdObj.put("ms", cmd.ms)
                }
                is DuckyCommand.TypeText -> {
                    cmdObj.put("type", "text")
                    cmdObj.put("text", cmd.text)
                    cmdObj.put("submit", cmd.submit)
                }
                is DuckyCommand.Press -> {
                    cmdObj.put("type", "press")
                    cmdObj.put("key", cmd.key ?: "")
                    val mods = JSONArray()
                    cmd.modifiers.forEach { mods.put(it) }
                    cmdObj.put("mods", mods)
                }
            }
            commands.put(cmdObj)
        }
        itemObj.put("commands", commands)
        root.put(itemObj)
    }
    prefs.edit().putString("scripts_json", root.toString()).apply()
}

private fun loadPersistedScripts(prefs: android.content.SharedPreferences): List<DuckyScriptItem> {
    val raw = prefs.getString("scripts_json", null) ?: return emptyList()
    return runCatching {
        val root = JSONArray(raw)
        buildList {
            for (i in 0 until root.length()) {
                val obj = root.optJSONObject(i) ?: continue
                val commandsJson = obj.optJSONArray("commands") ?: continue
                val commands = mutableListOf<DuckyCommand>()
                for (j in 0 until commandsJson.length()) {
                    val c = commandsJson.optJSONObject(j) ?: continue
                    when (c.optString("type")) {
                        "delay" -> commands += DuckyCommand.Delay(c.optLong("ms", 0L))
                        "text" -> commands += DuckyCommand.TypeText(
                            text = c.optString("text"),
                            submit = c.optBoolean("submit", false)
                        )
                        "press" -> {
                            val mods = mutableListOf<String>()
                            val modsJson = c.optJSONArray("mods")
                            if (modsJson != null) {
                                for (k in 0 until modsJson.length()) {
                                    mods += modsJson.optString(k)
                                }
                            }
                            val key = c.optString("key").ifBlank { null }
                            commands += DuckyCommand.Press(modifiers = mods, key = key)
                        }
                    }
                }
                if (commands.isNotEmpty()) {
                    add(
                        DuckyScriptItem(
                            id = obj.optLong("id", System.currentTimeMillis() + i),
                            title = obj.optString("title", "Script ${i + 1}"),
                            commands = commands
                        )
                    )
                }
            }
        }
    }.getOrDefault(emptyList())
}

