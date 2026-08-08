package com.droid.dolphy.network

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.M3SegmentedListItem
import com.droid.dolphy.M3SegmentedListItemSpacing
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialButton
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.m3SegmentedItems
import kotlinx.coroutines.launch

private data class LanToolEntry(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val route: String,
)

@Composable
fun LanToolsScreen(navController: NavController) {
    val accent = MaterialTheme.colorScheme.primary
    val tools = listOf(
        LanToolEntry(
            Icons.Default.Router,
            stringResource(R.string.lan_tools_item_scanner),
            stringResource(R.string.lan_tools_item_scanner_desc),
            "other/lan_scanner_run",
        ),
        LanToolEntry(
            Icons.Default.Videocam,
            stringResource(R.string.lan_tools_item_cameras),
            stringResource(R.string.lan_tools_item_cameras_desc),
            "other/lan_camera_scan",
        ),
        LanToolEntry(
            Icons.Default.Print,
            stringResource(R.string.wifi_print_title),
            stringResource(R.string.wifi_print_hero_desc),
            "other/wifi_print",
        ),
    )
    MaterialBackground(accentColor = accent) {
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                title = stringResource(R.string.lan_tools_title),
                onBack = { navController.popBackStack() },
                accentColor = accent,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.lan_tools_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
                    )
                }
                m3SegmentedItems(tools) { index, count, tool ->
                    M3SegmentedListItem(
                        index = index,
                        count = count,
                        headline = tool.title,
                        supporting = tool.description,
                        leadingIcon = tool.icon,
                        leadingIconTint = accent,
                        onClick = { navController.navigate(tool.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LanToolRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = accent,
        contentPadding = 0.dp,
    ) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        drawCircle(color = accent.copy(alpha = 0.14f))
                    }
                    Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

private enum class CameraScanPhase { IDLE, RUNNING }




@Composable
fun CameraNetworkScanScreen(navController: NavController) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(CameraScanPhase.IDLE) }
    var progress by remember { mutableFloatStateOf(0f) }
    var stage by remember { mutableStateOf("") }
    var liveFound by remember { mutableIntStateOf(0) }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    fun startScan() {
        if (phase == CameraScanPhase.RUNNING) return
        progress = 0f
        stage = ""
        liveFound = 0
        phase = CameraScanPhase.RUNNING
        scope.launch {
            try {
                val list = CameraNetworkScanner.scan(
                    context = context,
                    onProgress = { p ->
                        progress = p.fraction
                        stage = p.stage
                        liveFound = p.foundCount
                    },
                )
                CameraScanResultsStore.set(list)
            } catch (_: Exception) {
                CameraScanResultsStore.set(emptyList())
            }
            phase = CameraScanPhase.IDLE
            navController.navigate("other/lan_camera_results") {
                launchSingleTop = true
            }
        }
    }

    MaterialBackground(accentColor = accent) {
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                title = stringResource(R.string.lan_camera_title),
                onBack = { navController.popBackStack() },
                accentColor = accent,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accent,
                    contentPadding = 20.dp,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Videocam,
                                null,
                                tint = accent,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.lan_camera_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            stringResource(R.string.lan_camera_description),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (phase == CameraScanPhase.IDLE) {
                    MaterialButton(
                        text = stringResource(R.string.lan_camera_start),
                        onClick = { startScan() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        accentColor = accent,
                    )
                }

                if (phase == CameraScanPhase.RUNNING) {
                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accent,
                        contentPadding = 24.dp,
                    ) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { progress.coerceIn(0.02f, 1f) },
                                    modifier = Modifier
                                        .size(120.dp)
                                        .scale(pulseScale),
                                    color = accent,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeWidth = 8.dp,
                                    strokeCap = StrokeCap.Round,
                                )
                                Icon(
                                    Icons.Default.Radar,
                                    null,
                                    tint = accent,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                            Text(
                                "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                            )
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(MaterialTheme.shapes.small),
                                color = accent,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round,
                            )
                            Text(
                                stage.ifBlank { stringResource(R.string.lan_camera_scanning) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                stringResource(R.string.lan_camera_found_count, liveFound),
                                style = MaterialTheme.typography.labelLarge,
                                color = accent,
                            )
                        }
                    }
                }
            }
        }
    }
}




@Composable
fun CameraNetworkResultsScreen(navController: NavController) {
    val accent = MaterialTheme.colorScheme.primary
    val cameras = remember { CameraScanResultsStore.lastResults }
    val found = cameras.isNotEmpty()
    val resultScale = remember { Animatable(0.55f) }

    LaunchedEffect(Unit) {
        resultScale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        )
    }

    MaterialBackground(accentColor = accent) {
        Column(Modifier.fillMaxSize()) {
            SectionTopBar(
                title = stringResource(R.string.lan_camera_results_title),
                onBack = { navController.popBackStack() },
                accentColor = accent,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .scale(resultScale.value),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = if (found) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = if (found) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(80.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (found) {
                                stringResource(R.string.lan_camera_results_found_header, cameras.size)
                            } else {
                                stringResource(R.string.lan_camera_results_empty_header)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (found) {
                                stringResource(R.string.lan_camera_results_found_sub)
                            } else {
                                stringResource(R.string.lan_camera_results_empty_sub)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                if (found) {
                    item {
                        Text(
                            stringResource(R.string.lan_camera_results_list_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    items(cameras, key = { it.ip }) { cam ->
                        CameraResultCard(cam)
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    MaterialButton(
                        text = stringResource(R.string.lan_camera_again),
                        onClick = {
                            navController.popBackStack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraResultCard(cam: CameraNetworkScanner.FoundCamera) {
    val accent = MaterialTheme.colorScheme.primary
    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = accent,
        contentPadding = 16.dp,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(color = accent.copy(alpha = 0.15f))
                }
                Icon(Icons.Default.Videocam, null, tint = accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    cam.brand,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    cam.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "IP: ${cam.ip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.lan_camera_mac,
                        cam.mac ?: stringResource(R.string.lan_camera_mac_unknown),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cam.openPorts.isNotEmpty()) {
                    Text(
                        stringResource(R.string.lan_camera_ports, cam.openPorts.joinToString()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
                Text(
                    cam.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.7f),
                )
            }
        }
    }
}

