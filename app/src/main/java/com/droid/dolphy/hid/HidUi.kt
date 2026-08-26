package com.droid.dolphy.hid

import com.droid.dolphy.DolphyIconButton

import android.Manifest
import android.content.Context
import android.provider.OpenableColumns
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.border
import com.droid.dolphy.OrangeAccent
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.random.Random
import com.droid.dolphy.R
import org.json.JSONArray
import org.json.JSONObject




@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun Combo(
    hidConnection: Connection?,
    capsLock: Boolean,
) {
    var mode by remember { mutableStateOf("keyboard") }

    val togglePlum: @Composable (Modifier) -> Unit = { modifier ->
        Plum(
            onUp = {
                mode = if (mode == "keyboard") "touchpad" else "keyboard"
            },
            label = if (mode == "keyboard") "TP" else "KB",
            imageAlt = "Change Mode",
            modifier = modifier,
        )
    }

    val settingsPlum: @Composable (Modifier) -> Unit = { modifier ->
        Plum(
            onUp = {   },
            label = "SET",
            imageAlt = "Settings",
            modifier = modifier,
        )
    }

    if (mode == "keyboard") {
        Keyboard(hidConnection, togglePlum, settingsPlum, capsLock)
    } else {
        Touchpad(hidConnection, togglePlum, settingsPlum)
    }
}

enum class HidProfile { Mobile }

private data class MobileDuckyScriptItem(
    val id: Long,
    val title: String,
    val commands: List<DuckyCommand>,
    val running: Boolean = false,
)

@Composable
fun EviStatusIcon(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val frames = if (isActive) {
        intArrayOf(R.drawable.evi_smile1_18x21, R.drawable.evi_smile2_18x21)
    } else {
        intArrayOf(R.drawable.evi_waiting1_18x21, R.drawable.evi_waiting2_18x21)
    }
    val intervalMs = if (isActive) 40L else 1000L
    var frameIndex by remember(isActive) { mutableIntStateOf(0) }

    LaunchedEffect(isActive) {
        while (true) {
            delay(intervalMs)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    androidx.compose.foundation.Image(
        painter = painterResource(id = frames[frameIndex]),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint.copy(alpha = 0.65f), BlendMode.Modulate)
    )
}




@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun HidMainScreen(
    hidConnection: Connection?,
    capsLock: Boolean,
    sensitivityMove: Float = 1.5f,
    sensitivityScroll: Float = 0.4f,
    accentColor: Color = Color.Unspecified,
    isBadHidMode: Boolean = false,
    onModeChange: (Boolean) -> Unit = {}
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    if (hidConnection == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.hid_no_connection))
        }
        return
    }

    if (isBadHidMode) {
        ScreenshotSpamScreen(
            hidConnection = hidConnection,
            onBack = { onModeChange(false) },
            accentColor = currentAccent
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val accentColor = MaterialTheme.colorScheme.primary
                com.droid.dolphy.AccentButton(
                    onClick = { onModeChange(true) },
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("BH", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("BAD HID", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.hid_android_ios_mode), style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            MobileProfileScreen(
                hidConnection = hidConnection,
                sensitivityMove = sensitivityMove,
                sensitivityScroll = sensitivityScroll
            )
        }
    }
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
private fun MobileProfileScreen(
    hidConnection: Connection,
    sensitivityMove: Float = 1.5f,
    sensitivityScroll: Float = 0.4f
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
                Touchpad(
                    hidConnection = hidConnection,
                    togglePlum = { },
                    settingsPlum = { },
                    sensitivityMove = sensitivityMove,
                    sensitivityScroll = sensitivityScroll
                )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            ControlIconButton(
                icon = Icons.Default.CameraAlt,
                contentDescription = stringResource(R.string.cd_screenshot),
                onClick = {
                    scope.launch {
                        try {
                            hidConnection.mediaDown("POWER")
                            hidConnection.mediaDown("VOLUMEDOWN")
                            delay(200)
                            hidConnection.mediaUp("VOLUMEDOWN")
                            hidConnection.mediaUp("POWER")
                        } catch (e: Exception) {
                        }
                    }
                }
            )
            ControlIconButton(
                icon = Icons.AutoMirrored.Filled.VolumeDown,
                contentDescription = stringResource(R.string.cd_volume_down),
                onClick = {
                    scope.launch {
                        try {
                            hidConnection.mediaDown("VOLUMEDOWN")
                            delay(50)
                            hidConnection.mediaUp("VOLUMEDOWN")
                        } catch (e: Exception) {
                        }
                    }
                }
            )
            ControlIconButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.cd_volume_up),
                onClick = {
                    scope.launch {
                        try {
                            hidConnection.mediaDown("VOLUMEUP")
                            delay(50)
                            hidConnection.mediaUp("VOLUMEUP")
                        } catch (e: Exception) {
                        }
                    }
                }
            )
            ControlIconButton(
                icon = Icons.Default.PowerSettingsNew,
                contentDescription = stringResource(R.string.cd_power),
                onClick = {
                    scope.launch {
                        try {
                            hidConnection.mediaDown("POWER")
                            delay(200)
                            hidConnection.mediaUp("POWER")
                        } catch (e: Exception) {
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenshotSpamScreen(
    hidConnection: Connection,
    onBack: () -> Unit,
    accentColor: Color = Color.Unspecified
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scriptsPrefs = remember(context) { context.getSharedPreferences("mobile_bad_hid_scripts", Context.MODE_PRIVATE) }
    var screenshotSpamActive by remember { mutableStateOf(false) }
    var volumeSpamActive by remember { mutableStateOf(false) }
    var backSpamActive by remember { mutableStateOf(false) }
    var homeSpamActive by remember { mutableStateOf(false) }
    var phantomClicksActive by remember { mutableStateOf(false) }

    var targetUrl by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    var screenshotJob by remember { mutableStateOf<Job?>(null) }
    var volumeJob by remember { mutableStateOf<Job?>(null) }
    var backJob by remember { mutableStateOf<Job?>(null) }
    var homeJob by remember { mutableStateOf<Job?>(null) }
    var phantomJob by remember { mutableStateOf<Job?>(null) }

    val scripts = remember { mutableStateListOf<MobileDuckyScriptItem>() }
    val scriptJobs = remember { mutableStateMapOf<Long, Job>() }

    fun updateScript(itemId: Long, transform: (MobileDuckyScriptItem) -> MobileDuckyScriptItem) {
        val idx = scripts.indexOfFirst { it.id == itemId }
        if (idx >= 0) scripts[idx] = transform(scripts[idx])
    }

    LaunchedEffect(Unit) {
        scripts.clear()
        scripts.addAll(loadMobilePersistedScripts(scriptsPrefs))
        scripts.addAll(loadPreloadedScripts(context))
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            val title = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.removeSuffix(".txt")?.ifBlank { null } ?: "Script ${scripts.size + 1}"

            if (content.isBlank()) {
                Toast.makeText(context, context.getString(R.string.hid_ducky_empty), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val commands = DuckyUtils.parse(content)
            if (commands.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.hid_ducky_parse_fail), Toast.LENGTH_SHORT).show()
                return@launch
            }
            scripts.add(
                MobileDuckyScriptItem(
                    id = System.currentTimeMillis(),
                    title = title,
                    commands = commands
                )
            )
            persistMobileScripts(scriptsPrefs, scripts)
        }
    }

    suspend fun sendScreenshot() {
        try {
            hidConnection.mediaDown("POWER")
            hidConnection.mediaDown("VOLUMEDOWN")
            delay(500)
            hidConnection.mediaUp("VOLUMEDOWN")
            hidConnection.mediaUp("POWER")
        } catch (e: Exception) { }
    }

    fun centerCursor() {
        scope.launch {
            try {
                hidConnection.mouseMove(-10000, -10000)
                delay(300)
                hidConnection.mouseMove(2000, 2000)
            } catch (e: SecurityException) {
            }
        }
    }

    LaunchedEffect(screenshotSpamActive) {
        if (screenshotSpamActive) {
            screenshotJob = scope.launch {
                while (true) {
                    sendScreenshot()
                    delay(1200)
                }
            }
        } else {
            screenshotJob?.cancel()
            screenshotJob = null
        }
    }

    LaunchedEffect(volumeSpamActive) {
        if (volumeSpamActive) {
            volumeJob = scope.launch {
                while (true) {
                    try {
                        hidConnection.mediaDown("VOLUMEDOWN")
                        delay(50)
                        hidConnection.mediaUp("VOLUMEDOWN")
                        delay(150)
                        hidConnection.mediaDown("VOLUMEUP")
                        delay(50)
                        hidConnection.mediaUp("VOLUMEUP")
                        delay(150)
                    } catch (e: Exception) { }
                }
            }
        } else {
            volumeJob?.cancel()
            volumeJob = null
        }
    }

    LaunchedEffect(backSpamActive) {
        if (backSpamActive) {
            backJob = scope.launch {
                while (true) {
                    try {
                        hidConnection.keyDown("ESC")
                        delay(50)
                        hidConnection.keyUp("ESC")
                    } catch (e: Exception) { }
                    delay(250)
                }
            }
        } else {
            backJob?.cancel()
            backJob = null
        }
    }

    LaunchedEffect(homeSpamActive) {
        if (homeSpamActive) {
            homeJob = scope.launch {
                while (true) {
                    try {
                        hidConnection.keyDown("HOME")
                        delay(50)
                        hidConnection.keyUp("HOME")
                    } catch (e: Exception) { }
                    delay(250)
                }
            }
        } else {
            homeJob?.cancel()
            homeJob = null
        }
    }

    LaunchedEffect(phantomClicksActive) {
        if (phantomClicksActive) {
            phantomJob = scope.launch {
                while (true) {
                    try {
                        val dx = Random.nextInt(-15, 16)
                        val dy = Random.nextInt(-15, 16)
                        hidConnection.mouseMove(dx, dy)
                        delay(Random.nextLong(50, 150))
                        hidConnection.mouseDown(0)
                        delay(50)
                        hidConnection.mouseUp(0)
                    } catch (e: Exception) { }
                    delay(Random.nextLong(200, 800))
                }
            }
        } else {
            phantomJob?.cancel()
            phantomJob = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            screenshotJob?.cancel()
            volumeJob?.cancel()
            backJob?.cancel()
            homeJob?.cancel()
            phantomJob?.cancel()
            scriptJobs.values.forEach { it.cancel() }
            scriptJobs.clear()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    runCatching {
                        hidConnection.releaseAllKeyboard()
                        hidConnection.releaseAllMouseButtons()
                    }
                    importLauncher.launch(arrayOf("text/plain", "text/*"))
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.hid_import_duckyscript))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            val anyActive = screenshotSpamActive || volumeSpamActive || backSpamActive || homeSpamActive || phantomClicksActive || scripts.any { it.running }

            if (anyActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            RoundedCornerShape(28.dp)
                        ),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EviStatusIcon(
                            isActive = anyActive,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "ATTACK IN PROGRESS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(
                "Payloads",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, top = if (anyActive) 0.dp else 16.dp)
            )

            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                BadHidActionCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.hid_screenshot_spam),
                    icon = Icons.Default.CameraAlt,
                    isActive = screenshotSpamActive,
                    onClick = { screenshotSpamActive = !screenshotSpamActive },
                    accentColor = currentAccent
                )
                BadHidActionCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.hid_volume_spam),
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    isActive = volumeSpamActive,
                    onClick = { volumeSpamActive = !volumeSpamActive },
                    accentColor = currentAccent
                )
            }

            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                BadHidActionCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.hid_back_spam),
                    icon = Icons.Default.SkipPrevious,
                    isActive = backSpamActive,
                    onClick = { backSpamActive = !backSpamActive },
                    accentColor = currentAccent
                )
                BadHidActionCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.hid_home_spam),
                    icon = Icons.Default.PowerSettingsNew,
                    isActive = homeSpamActive,
                    onClick = { homeSpamActive = !homeSpamActive },
                    accentColor = currentAccent
                )
            }

            BadHidActionCard(
                modifier = Modifier.fillMaxWidth(),
                title = "Phantom Clicks",
                icon = Icons.Default.Mouse,
                isActive = phantomClicksActive,
                onClick = { phantomClicksActive = !phantomClicksActive },
                accentColor = currentAccent
            )

            Text(
                "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL to open") },
                        placeholder = { Text("https://...") },
                        leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    com.droid.dolphy.AccentButton(
                        onClick = {
                            scope.launch {
                                try {

                                    hidConnection.modifierDown("MOD_LGUI")
                                    delay(50)
                                    hidConnection.keyDown("b")
                                    delay(50)
                                    hidConnection.keyUp("b")
                                    delay(50)
                                    hidConnection.modifierUp("MOD_LGUI")

                                    delay(1500)

                                    val finalQuery = if (targetUrl.contains(".") && !targetUrl.contains(" ")) {
                                        if (targetUrl.startsWith("http")) targetUrl else "https://$targetUrl"
                                    } else {
                                        "https://www.google.com/search?q=${targetUrl.replace(" ", "+")}"
                                    }

                                    DuckyUtils.typeText(hidConnection, finalQuery)
                                    DuckyUtils.tapKey(hidConnection, "ENTER")
                                } catch (e: Exception) {
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        enabled = targetUrl.isNotBlank()
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Link")
                    }
                }
            }

            com.droid.dolphy.AccentButton(
                onClick = { centerCursor() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.Mouse, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Center Cursor")
            }

            if (scripts.isNotEmpty()) {
                Text(
                    "Ducky Scripts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
                scripts.forEach { script ->
                    BadHidActionCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = script.title,
                        icon = Icons.Default.PlayArrow,
                        isActive = script.running,
                        onClick = {
                            val runningJob = scriptJobs[script.id]
                            if (runningJob != null) {
                                runningJob.cancel()
                                scriptJobs.remove(script.id)
                                updateScript(script.id) { it.copy(running = false) }
                            } else {
                                updateScript(script.id) { it.copy(running = true) }
                                val job = scope.launch {
                                    try {
                                        while (true) {
                                            DuckyUtils.execute(hidConnection, script.commands)
                                            delay(100)
                                        }
                                    } finally {
                                        updateScript(script.id) { it.copy(running = false) }
                                        scriptJobs.remove(script.id)
                                    }
                                }
                                scriptJobs[script.id] = job
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun BadHidActionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    accentColor: Color = Color.Unspecified
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        modifier = modifier.height(110.dp).border(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else currentAccent.copy(alpha = 0.4f),
            RoundedCornerShape(28.dp)
        ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Stop else icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                color = if (isActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RowScope.ControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    DolphyIconButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun persistMobileScripts(prefs: android.content.SharedPreferences, scripts: List<MobileDuckyScriptItem>) {
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

private fun loadMobilePersistedScripts(prefs: android.content.SharedPreferences): List<MobileDuckyScriptItem> {
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
                        MobileDuckyScriptItem(
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

private fun loadPreloadedScripts(context: Context): List<MobileDuckyScriptItem> {
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
                        val title = fileName.removeSuffix(".txt")
                        add(
                            MobileDuckyScriptItem(
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

@Composable
private fun PcKeyboardScreen(
    hidConnection: Connection,
    capsLock: Boolean,
    onSwitchToTouchpad: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
        ) {
            com.droid.dolphy.AccentButton(
                onClick = onSwitchToTouchpad,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(stringResource(R.string.hid_touchpad))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(90f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Keyboard(
                        hidConnection = hidConnection,
                        togglePlum = { _ -> },
                        settingsPlum = { _ -> },
                        capsLock = capsLock,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PcTouchpadScreen(
    hidConnection: Connection,
    sensitivityMove: Float = 1.5f,
    sensitivityScroll: Float = 0.4f,
    onSwitchToKeyboard: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
        ) {
            com.droid.dolphy.AccentButton(
                onClick = onSwitchToKeyboard,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(stringResource(R.string.hid_keyboard))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Touchpad(
            hidConnection = hidConnection,
            togglePlum = { _ -> },
            settingsPlum = { _ -> },
            sensitivityMove = sensitivityMove,
            sensitivityScroll = sensitivityScroll
        )
    }
}

enum class Layer { DEFAULT, FUNCTION }





@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun Keyboard(
    hidConnection: Connection?,
    togglePlum: @Composable (Modifier) -> Unit,
    settingsPlum: @Composable (Modifier) -> Unit,
    capsLock: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    var layer by remember { mutableStateOf(Layer.DEFAULT) }

    Column(modifier = modifier) {
        val keyWidth = 55.dp
        val keyHeight = 50.dp
        fun key(multiplier: Float = 1f) = Modifier.width(keyWidth * multiplier).height(keyHeight)

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            PlumSymbol(hidConnection, "ESC", label = "Esc", modifier = key())
            Spacer(modifier = Modifier.width(16.dp))

            if (layer != Layer.FUNCTION) {
                listOf("F1", "F2", "F3", "F4").forEach { PlumSymbol(hidConnection, it, label = it, modifier = key()) }
                Spacer(modifier = Modifier.width(10.dp))
                listOf("F5", "F6", "F7", "F8").forEach { PlumSymbol(hidConnection, it, label = it, modifier = key()) }
                Spacer(modifier = Modifier.width(10.dp))
                listOf("F9", "F10", "F11", "F12").forEach { PlumSymbol(hidConnection, it, label = it, modifier = key()) }
            } else {
                PlumMedia(hidConnection, "VOLUMEMUTE", modifier = key(), imageVector = Icons.AutoMirrored.Filled.VolumeMute)
                PlumMedia(hidConnection, "PLAYPAUSE", modifier = key(), imageVector = Icons.Default.PlayArrow)
                PlumMedia(hidConnection, "TRACKPREVIOUS", modifier = key(), imageVector = Icons.Default.SkipPrevious)
                PlumMedia(hidConnection, "TRACKNEXT", modifier = key(), imageVector = Icons.Default.SkipNext)
                PlumMedia(hidConnection, "VOLUMEUP", modifier = key(), imageVector = Icons.AutoMirrored.Filled.VolumeUp)
                PlumMedia(hidConnection, "VOLUMEDOWN", modifier = key(), imageVector = Icons.AutoMirrored.Filled.VolumeDown)
            }

            Spacer(modifier = Modifier.width(10.dp))
            Plum(
                modifier = key(),
                onDown = { },
                onUp = { layer = if (layer == Layer.DEFAULT) Layer.FUNCTION else Layer.DEFAULT },
                label = "Fn",
                activeDot = true,
                active = layer == Layer.FUNCTION
            )
            togglePlum(key())
            settingsPlum(key())
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            PlumSymbol(hidConnection, "GRAVE", label = "~\n`", modifier = key())
            listOf(
                "1" to "!\n1",
                "2" to "@\n2",
                "3" to "#\n3",
                "4" to "$\n4",
                "5" to "%\n5",
                "6" to "^\n6",
                "7" to "&\n7",
                "8" to "*\n8",
                "9" to "(\n9",
                "0" to ")\n0",
            ).forEach { (value, label) ->
                PlumSymbol(hidConnection, value, label = label, modifier = key())
            }
            PlumSymbol(hidConnection, "MINUS", label = "_\n-", modifier = key())
            PlumSymbol(hidConnection, "EQUAL", label = "+\n=", modifier = key())
            PlumSymbol(hidConnection, "BACKSPACE", label = "Backspace", modifier = key(2.2f))
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            PlumSymbol(hidConnection, "TAB", label = "Tab", modifier = key(1.8f))
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P").forEach {
                PlumSymbol(hidConnection, it, label = it, modifier = key())
            }
            PlumSymbol(hidConnection, "LEFTBRACE", label = "{\n[", modifier = key())
            PlumSymbol(hidConnection, "RIGHTBRACE", label = "}\n]", modifier = key())
            PlumSymbol(hidConnection, "BACKSLASH", label = "|\n\\", modifier = key(1.4f))
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            PlumSymbol(
                hidConnection = hidConnection,
                key = "CAPSLOCK",
                label = "Caps Lock",
                modifier = key(2.2f),
                activeDot = true,
                active = capsLock
            )
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L").forEach {
                PlumSymbol(hidConnection, it, label = it, modifier = key())
            }
            PlumSymbol(hidConnection, "SEMICOLON", label = ":\n;", modifier = key())
            PlumSymbol(hidConnection, "APOSTROPHE", label = "\"\n'", modifier = key())
            PlumSymbol(hidConnection, "ENTER", label = "Return", modifier = key(2.2f))
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            PlumModifier(hidConnection, "MOD_LSHIFT", label = "Shift", modifier = key(2.5f))
            listOf("Z", "X", "C", "V", "B", "N", "M").forEach {
                PlumSymbol(hidConnection, it, label = it, modifier = key())
            }
            PlumSymbol(hidConnection, "COMMA", label = "<\n,", modifier = key())
            PlumSymbol(hidConnection, "DOT", label = ">\n.", modifier = key())
            PlumSymbol(hidConnection, "SLASH", label = "?\n/", modifier = key())
            PlumModifier(hidConnection, "MOD_RSHIFT", label = "Shift", modifier = key(2.5f))
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            PlumModifier(hidConnection, "MOD_LCTRL", label = "Ctrl", modifier = key(1.5f))
            PlumModifier(hidConnection, "MOD_LMETA", label = "Win", modifier = key(1.2f))
            PlumModifier(hidConnection, "MOD_LALT", label = "Alt", modifier = key(1.3f))
            PlumSymbol(hidConnection, "SPACE", label = "Space", modifier = key(6.2f))
            PlumModifier(hidConnection, "MOD_RALT", label = "Alt Gr", modifier = key(1.5f))
            PlumModifier(hidConnection, "MOD_RMETA", label = "Win", modifier = key(1.2f))
            PlumSymbol(hidConnection, "APPLICATION", label = "≣", modifier = key(1.2f))
            PlumModifier(hidConnection, "MOD_RCTRL", label = "Ctrl", modifier = key(1.5f))
        }
    }
}



@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun Touchpad(
    hidConnection: Connection?,
    togglePlum: @Composable (Modifier) -> Unit = {},
    settingsPlum: @Composable (Modifier) -> Unit = {},
    sensitivityMove: Float = 1.5f,
    sensitivityScroll: Float = 0.4f
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Axon(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            onTap = { hidConnection?.mouseClick(0) },
            onTapDragStart = { hidConnection?.mouseDown(0) },
            onTapDragStop = { hidConnection?.mouseUp(0) },
            onSlide = { deltaX: Int, deltaY: Int ->
                hidConnection?.mouseMove(deltaX, deltaY)
            },
            onScroll = { deltaWheel: Int, horizontal: Boolean ->
                hidConnection?.mouseWheel(deltaWheel, horizontal)
            },
            enabled = hidConnection != null,
            sensitivityMove = sensitivityMove,
            sensitivityScroll = sensitivityScroll
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Axon(
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onTapDragStart: () -> Unit = {},
    onTapDragStop: () -> Unit = {},
    onSlide: (deltaX: Int, deltaY: Int) -> Unit = { _: Int, _: Int -> },
    onStretch: (deltaX: Float, deltaY: Float) -> Unit = { _: Float, _: Float -> },
    onScroll: (delta: Int, horizontal: Boolean) -> Unit = { _, _ -> },
    enabled: Boolean = true,
    sensitivityMove: Float = 1.5f,
    sensitivityScroll: Float = 0.4f,
    content: @Composable (BoxScope.() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current

    var activePointerId by remember { mutableIntStateOf(-1) }
    var secondaryPointerId by remember { mutableIntStateOf(-1) }
    var firstX by remember { mutableFloatStateOf(0f) }
    var firstY by remember { mutableFloatStateOf(0f) }
    var lastX by remember { mutableFloatStateOf(0f) }
    var lastY by remember { mutableFloatStateOf(0f) }
    var lastSecondaryX by remember { mutableFloatStateOf(0f) }
    var lastSecondaryY by remember { mutableFloatStateOf(0f) }
    var leftDownAt by remember { mutableLongStateOf(0L) }
    var tapAt by remember { mutableLongStateOf(0L) }
    var tapDragging by remember { mutableStateOf(false) }
    var deltaCarryX by remember { mutableFloatStateOf(0f) }
    var deltaCarryY by remember { mutableFloatStateOf(0f) }
    var scrollCarryX by remember { mutableFloatStateOf(0f) }
    var scrollCarryY by remember { mutableFloatStateOf(0f) }

    var size by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(shape = RoundedCornerShape(28.dp))
            .background(
                color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
            )
            .drawBehind {
                if (enabled) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.05f),
                        size = size
                    )
                }
            }
            .onSizeChanged {
                size = Size(it.width.toFloat(), it.height.toFloat())
            }
            .pointerInteropFilter {
                when (it.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        if (it.pointerCount == 1) {
                            activePointerId = it.getPointerId(it.actionIndex)
                            leftDownAt = Date().time
                            firstX = it.getX(it.actionIndex)
                            firstY = it.getY(it.actionIndex)
                            lastX = it.getX(it.actionIndex)
                            lastY = it.getY(it.actionIndex)
                            deltaCarryX = 0f
                            deltaCarryY = 0f
                            if ((Date().time - tapAt) in (21..149)) {
                                tapDragging = true
                                onTapDragStart()
                            }
                        } else if (it.pointerCount == 2) {

                            secondaryPointerId = it.getPointerId(it.actionIndex)
                            val idx1 = it.findPointerIndex(activePointerId)
                            val idx2 = it.findPointerIndex(secondaryPointerId)
                            if (idx1 >= 0 && idx2 >= 0) {
                                lastX = it.getX(idx1)
                                lastY = it.getY(idx1)
                                lastSecondaryX = it.getX(idx2)
                                lastSecondaryY = it.getY(idx2)
                            }
                            scrollCarryX = 0f
                            scrollCarryY = 0f
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        val pointerId = it.getPointerId(it.actionIndex)
                        if (pointerId == activePointerId) {
                            onStretch(0f, 0f)
                            if ((Date().time - leftDownAt) in (21..149)) {
                                leftDownAt = 0
                                tapAt = Date().time
                                onTap()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (tapDragging) {
                                tapDragging = false
                                onTapDragStop()
                            }
                            activePointerId = -1
                            secondaryPointerId = -1
                        } else if (pointerId == secondaryPointerId) {
                            secondaryPointerId = -1
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (it.pointerCount == 1 && activePointerId != -1) {
                            val index = it.findPointerIndex(activePointerId)
                            if (index >= 0) {
                                val x = it.getX(index)
                                val y = it.getY(index)
                                val stretchX = (x - firstX).toInt()
                                val stretchY = (y - firstY).toInt()
                                val normXRaw = stretchX / size.width
                                val normYRaw = stretchY.toFloat() / size.height
                                val normX = if (normXRaw < -1f) -1f else if (normXRaw > 1f) 1f else normXRaw
                                val normY = if (normYRaw < -1f) -1f else if (normYRaw > 1f) 1f else normYRaw
                                onStretch(normX, normY)
                                val rawDeltaX = (x - lastX) * sensitivityMove + deltaCarryX
                                val rawDeltaY = (y - lastY) * sensitivityMove + deltaCarryY
                                val deltaX = rawDeltaX.toInt()
                                val deltaY = rawDeltaY.toInt()
                                deltaCarryX = rawDeltaX - deltaX
                                deltaCarryY = rawDeltaY - deltaY
                                if (deltaX != 0 || deltaY != 0) {
                                    onSlide(deltaX, deltaY)
                                }
                                lastX = x
                                lastY = y
                            }
                        } else if (it.pointerCount == 2 && activePointerId != -1 && secondaryPointerId != -1) {
                            val idx1 = it.findPointerIndex(activePointerId)
                            val idx2 = it.findPointerIndex(secondaryPointerId)
                            if (idx1 >= 0 && idx2 >= 0) {
                                val x1 = it.getX(idx1)
                                val y1 = it.getY(idx1)
                                val x2 = it.getX(idx2)
                                val y2 = it.getY(idx2)

                                val avgX = (x1 + x2) / 2f
                                val avgY = (y1 + y2) / 2f
                                val lastAvgX = (lastX + lastSecondaryX) / 2f
                                val lastAvgY = (lastY + lastSecondaryY) / 2f

                                val rawScrX = (avgX - lastAvgX) * sensitivityScroll + scrollCarryX
                                val rawScrY = (avgY - lastAvgY) * sensitivityScroll + scrollCarryY

                                val scrollX = rawScrX.toInt()
                                val scrollY = rawScrY.toInt()

                                scrollCarryX = rawScrX - scrollX
                                scrollCarryY = rawScrY - scrollY

                                if (scrollY != 0) onScroll(-scrollY, false)
                                if (scrollX != 0) onScroll(scrollX, true)

                                lastX = x1
                                lastY = y1
                                lastSecondaryX = x2
                                lastSecondaryY = y2
                            }
                        }
                    }
                }
                true
            },
        contentAlignment = Alignment.Center
    ) {
        if (content != null)
            content()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Plum(
    modifier: Modifier = Modifier,
    onDown: () -> Unit = {},
    onUp: () -> Unit = {},
    enabled: Boolean = true,
    @DrawableRes imageId: Int = 0,
    imageAlt: String = "",
    imageVector: ImageVector? = null,
    label: String = "",
    activeDot: Boolean = false,
    active: Boolean = false,
    content: @Composable (RowScope.() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current

    var pressed by remember { mutableStateOf(false) }
    var activePointerId by remember { mutableIntStateOf(-1) }

    Box(
        modifier = modifier
            .padding(2.dp)
    ) {
        com.droid.dolphy.AccentButton(
            onClick = {},
            modifier = Modifier
                .matchParentSize()
                .pointerInteropFilter {
                    var consume = true
                    when (it.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                            if (!enabled) {
                                consume = false
                            } else if (activePointerId == -1) {
                                activePointerId = it.getPointerId(it.actionIndex)
                                pressed = true
                                onDown()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                            val pointerId = it.getPointerId(it.actionIndex)
                            if (pointerId == activePointerId) {
                                activePointerId = -1
                                pressed = false
                                onUp()
                            }
                        }
                    }
                    consume
                },
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active) {
                    MaterialTheme.colorScheme.primary
                } else if (pressed) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
            ),
            contentPadding = PaddingValues(0.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 0.dp
            )
        ) {
            if (imageVector != null) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = imageAlt,
                    modifier = Modifier.size(22.dp),
                    tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            if (imageId != 0 && imageVector == null) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = imageId),
                    contentDescription = imageAlt,
                    modifier = Modifier.size(18.dp),
                    colorFilter = ColorFilter.tint(color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer)
                )
            }
            if (label.isNotEmpty() && imageVector == null) {
                Text(
                    label,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (label.length == 1) 22.sp else 12.sp,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            if (content != null) content()
        }
        if (activeDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp, top = 4.dp)
                    .size(5.dp)
                    .clip(shape = RoundedCornerShape(5.dp))
                    .background(
                        color = with(MaterialTheme.colorScheme) {
                            if (active) primary else onPrimary.copy(alpha = 0.19f)
                        })
            )
        }
    }
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun PlumSymbol(
    hidConnection: Connection?,
    key: String,
    modifier: Modifier = Modifier,
    onDown: () -> Unit = {},
    onUp: () -> Unit = {},
    imageId: Int = 0,
    imageAlt: String = "",
    label: String = "",
    activeDot: Boolean = false,
    active: Boolean = false,
    content: @Composable (RowScope.() -> Unit)? = null
) {
    Plum(
        onDown = {
            hidConnection?.keyDown(key)
            onDown()
        },
        onUp = {
            hidConnection?.keyUp(key)
            onUp()
        },
        enabled = hidConnection != null,
        modifier = modifier,
        imageId = imageId,
        imageAlt = imageAlt,
        label = label.ifEmpty { key },
        activeDot = activeDot,
        active = active,
        content = content
    )
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun PlumModifier(
    hidConnection: Connection?,
    key: String,
    modifier: Modifier = Modifier,
    onDown: () -> Unit = {},
    onUp: () -> Unit = {},
    imageId: Int = 0,
    imageAlt: String = "",
    label: String = "",
    content: @Composable (RowScope.() -> Unit)? = null
) {
    Plum(
        onDown = {
            hidConnection?.modifierDown(key)
            onDown()
        },
        onUp = {
            hidConnection?.modifierUp(key)
            onUp()
        },
        enabled = hidConnection != null,
        modifier = modifier,
        imageId = imageId,
        imageAlt = imageAlt,
        label = label.ifEmpty { key },
        content = content
    )
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun PlumMedia(
    hidConnection: Connection?,
    key: String,
    modifier: Modifier = Modifier,
    imageId: Int = 0,
    imageAlt: String = "",
    imageVector: ImageVector? = null,
    label: String = "",
    content: @Composable (RowScope.() -> Unit)? = null
) {
    Plum(
        onDown = { hidConnection?.mediaDown(key) },
        onUp = { hidConnection?.mediaUp(key) },
        enabled = hidConnection != null,
        modifier = modifier,
        imageId = imageId,
        imageAlt = imageAlt,
        imageVector = imageVector,
        label = label.ifEmpty { key },
        content = content
    )
}


