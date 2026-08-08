package com.droid.dolphy.hid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.ExpressiveBounceButton
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialButton
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.RootUtils
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.trackBadHidRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun BadUsbScreen(navController: NavController) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    val controller = remember { RootHidController() }

    var hasRoot by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(RootHidController.UsbLinkStatus.SETUP) }
    var script by remember {
        mutableStateOf(
            """
            REM Dolphy BAD USB example
            DELAY 500
            GUI r
            DELAY 400
            STRING notepad
            ENTER
            DELAY 600
            STRING Hello from Dolphy BAD USB
            ENTER
            """.trimIndent(),
        )
    }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        status = RootHidController.UsbLinkStatus.SETUP
        hasRoot = withContext(Dispatchers.IO) { RootUtils.isRooted() }
        if (!hasRoot) {
            status = RootHidController.UsbLinkStatus.NO_ROOT
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            controller.checkDevices()
            controller.wakeAsMouse()
        }
        status = withContext(Dispatchers.IO) { controller.probeLinkStatus(true) }

        while (isActive) {
            delay(1500)
            if (running) continue
            val next = withContext(Dispatchers.IO) {
                if (!RootUtils.isRooted()) RootHidController.UsbLinkStatus.NO_ROOT
                else {
                    if (!controller.hasHidNodes()) {
                        controller.setupHidGadget()
                        controller.wakeAsMouse()
                    }
                    controller.probeLinkStatus(true)
                }
            }
            status = next
            hasRoot = next != RootHidController.UsbLinkStatus.NO_ROOT
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                controller.releaseAllKeyboard()
                controller.releaseAllMouseButtons()
            } catch (_: Exception) {
            }
        }
    }

    MaterialBackground(accentColor = accent) {
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                title = stringResource(R.string.bad_usb_title),
                onBack = { navController.popBackStack() },
                accentColor = accent,
                showRootBadge = true,
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                BadUsbStatusBar(status = status, accent = accent)

                Spacer(Modifier.height(12.dp))

                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 14.dp,
                ) {
                    Text(
                        text = stringResource(R.string.bad_usb_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.bad_usb_script_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = script,
                    onValueChange = { if (!running) script = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 360.dp),
                    enabled = !running && hasRoot,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        cursorColor = accent,
                    ),
                )

                if (running) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0.05f, 1f) },
                            modifier = Modifier.size(28.dp),
                            color = accent,
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = stringResource(R.string.bad_usb_running),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                message?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))

                val canRun = hasRoot &&
                    status == RootHidController.UsbLinkStatus.CONNECTED &&
                    !running &&
                    script.isNotBlank()

                MaterialButton(
                    text = stringResource(R.string.bad_usb_run),
                    onClick = {
                        if (!canRun && status != RootHidController.UsbLinkStatus.CONNECTED) {
                            if (!(hasRoot && controller.hasHidNodes() && script.isNotBlank() && !running)) {
                                message = when (status) {
                                    RootHidController.UsbLinkStatus.NO_ROOT ->
                                        context.getString(R.string.bad_usb_status_root)
                                    RootHidController.UsbLinkStatus.DISCONNECTED,
                                    RootHidController.UsbLinkStatus.SETUP,
                                    ->
                                        context.getString(R.string.bad_usb_status_disconnected)
                                    else -> context.getString(R.string.bad_usb_empty)
                                }
                                return@MaterialButton
                            }
                        }
                        if (script.isBlank()) {
                            message = context.getString(R.string.bad_usb_empty)
                            return@MaterialButton
                        }
                        scope.launch {
                            running = true
                            progress = 0f
                            message = null
                            val ok = withContext(Dispatchers.IO) {
                                try {
                                    if (!controller.hasHidNodes()) {
                                        if (!controller.setupHidGadget()) return@withContext false
                                    }
                                    controller.wakeAsMouse()
                                    val commands = DuckyUtils.parse(script)
                                    if (commands.isEmpty()) return@withContext false
                                    DuckyUtils.execute(controller, commands) { p ->
                                        progress = p
                                    }
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            running = false
                            progress = if (ok) 1f else 0f
                            message = context.getString(
                                if (ok) R.string.bad_usb_done else R.string.bad_usb_fail,
                            )
                            if (ok) trackBadHidRun(context)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    accentColor = accent,
                    enabled = hasRoot && !running && script.isNotBlank(),
                )

                if (hasRoot && status == RootHidController.UsbLinkStatus.DISCONNECTED && !running) {
                    Spacer(Modifier.height(8.dp))
                    ExpressiveBounceButton(
                        onClick = {
                            scope.launch {
                                status = RootHidController.UsbLinkStatus.SETUP
                                withContext(Dispatchers.IO) {
                                    controller.setupHidGadget()
                                    controller.wakeAsMouse()
                                }
                                status = withContext(Dispatchers.IO) {
                                    controller.probeLinkStatus(true)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.bad_usb_status_setup),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun BadUsbStatusBar(
    status: RootHidController.UsbLinkStatus,
    accent: Color,
) {
    val (labelRes, icon, bg, fg) = when (status) {
        RootHidController.UsbLinkStatus.NO_ROOT -> Quad(
            R.string.bad_usb_status_root,
            Icons.Default.Security,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        RootHidController.UsbLinkStatus.SETUP -> Quad(
            R.string.bad_usb_status_setup,
            Icons.Default.SettingsInputHdmi,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RootHidController.UsbLinkStatus.DISCONNECTED -> Quad(
            R.string.bad_usb_status_disconnected,
            Icons.Default.LinkOff,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RootHidController.UsbLinkStatus.CONNECTED -> Quad(
            R.string.bad_usb_status_connected,
            Icons.Default.Mouse,
            accent.copy(alpha = 0.18f),
            accent,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = bg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

