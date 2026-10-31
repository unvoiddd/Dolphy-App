package com.droid.dolphy.network

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialButton
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import kotlinx.coroutines.flow.catch

private enum class MyCamPhase { SCANNING, LIVE_MJPEG, LIVE_SNAPSHOT, LIVE_RTSP, NO_CAMERAS, ERROR }

@Composable
fun MyCamScreen(navController: NavController) {
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    var phase by remember { mutableStateOf(MyCamPhase.SCANNING) }
    var statusText by remember { mutableStateOf("") }
    var logLines by remember { mutableStateOf(listOf<String>()) }
    var mjpegTarget by remember { mutableStateOf<MyCamEngine.CameraTarget.Mjpeg?>(null) }
    var snapshotTarget by remember { mutableStateOf<MyCamEngine.CameraTarget.Snapshot?>(null) }
    var rtspUrl by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var forcedMode by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        phase = MyCamPhase.SCANNING
        mjpegTarget = null
        snapshotTarget = null
        rtspUrl = null
        logLines = emptyList()
        statusText = context.getString(R.string.my_cam_status_searching)

        val result = MyCamEngine.findCamera(
            context = context,
            onStatus = { st ->
                statusText = when (st) {
                    MyCamEngine.Status.Searching -> context.getString(R.string.my_cam_status_searching)
                    is MyCamEngine.Status.Requesting -> context.getString(R.string.my_cam_status_requesting, st.ip)
                    MyCamEngine.Status.CheckingPassword -> context.getString(R.string.my_cam_status_checking)
                    MyCamEngine.Status.NoReply -> context.getString(R.string.my_cam_status_no_reply)
                    MyCamEngine.Status.NextDevice -> context.getString(R.string.my_cam_status_next_device)
                }
            },
            onLog = { msg -> logLines = logLines + msg },
            forceAll = forcedMode,
        )

        when (val target = result.target) {
            is MyCamEngine.CameraTarget.Mjpeg -> {
                mjpegTarget = target
                phase = MyCamPhase.LIVE_MJPEG
            }
            is MyCamEngine.CameraTarget.Snapshot -> {
                snapshotTarget = target
                phase = MyCamPhase.LIVE_SNAPSHOT
            }
            is MyCamEngine.CameraTarget.Rtsp -> {
                rtspUrl = target.url
                phase = MyCamPhase.LIVE_RTSP
            }
            null -> {
                if (result.cameraSeen) {
                    logLines = logLines + context.getString(R.string.my_cam_log_no_video)
                    phase = MyCamPhase.ERROR
                } else {
                    phase = MyCamPhase.NO_CAMERAS
                }
            }
        }
    }

    MaterialBackground(accentColor = accent) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                SectionTopBar(
                    title = stringResource(R.string.my_cam_title),
                    onBack = { navController.popBackStack() },
                    accentColor = accent,
                    alwaysCollapsed = true,
                )
                when (phase) {
                    MyCamPhase.SCANNING -> Unit
                    MyCamPhase.LIVE_MJPEG -> mjpegTarget?.let {
                        MjpegView(it.url, it.authHeader) { msg ->
                            logLines = logLines + msg
                            phase = MyCamPhase.ERROR
                        }
                    }
                    MyCamPhase.LIVE_SNAPSHOT -> snapshotTarget?.let {
                        SnapshotView(it.url, it.authHeader) { msg ->
                            logLines = logLines + msg
                            phase = MyCamPhase.ERROR
                        }
                    }
                    MyCamPhase.LIVE_RTSP -> rtspUrl?.let { url ->
                        RtspView(url) { msg ->
                            logLines = logLines + msg
                            phase = MyCamPhase.ERROR
                        }
                    }
                    MyCamPhase.NO_CAMERAS -> FailureCenter(
                        icon = Icons.Default.WarningAmber,
                        title = stringResource(R.string.my_cam_no_cameras),
                        logLines = logLines,
                        onRetry = { forcedMode = false; attempt++ },
                        onRetryAllDevices = { forcedMode = true; attempt++ },
                    )
                    MyCamPhase.ERROR -> FailureCenter(
                        icon = Icons.Default.ErrorOutline,
                        title = stringResource(R.string.my_cam_error_title),
                        logLines = logLines,
                        onRetry = { forcedMode = false; attempt++ },
                    )
                }
            }
            if (phase == MyCamPhase.SCANNING) {
                ScanningCenter(statusText)
            }
        }
    }
}

@Composable
private fun ScanningCenter(status: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(120.dp),
            strokeWidth = 6.dp,
        )
        Text(
            text = status,
            modifier = Modifier
                .widthIn(max = 160.dp)
                .padding(8.dp),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MjpegView(url: String, authHeader: String?, onInterrupted: (String) -> Unit) {
    val context = LocalContext.current
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url, authHeader) {
        MyCamEngine.mjpegFrames(url, authHeader)
            .catch { e -> onInterrupted(context.getString(R.string.my_cam_stream_interrupted, e.message ?: context.getString(R.string.my_cam_stream_no_reply))) }
            .collect { frame = it }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = frame
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.my_cam_live_label),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun SnapshotView(url: String, authHeader: String?, onInterrupted: (String) -> Unit) {
    val context = LocalContext.current
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url, authHeader) {
        MyCamEngine.snapshotFrames(url, authHeader)
            .catch { e -> onInterrupted(context.getString(R.string.my_cam_snapshot_interrupted, e.message ?: context.getString(R.string.my_cam_stream_no_reply))) }
            .collect { frame = it }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = frame
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.my_cam_live_label),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun RtspView(url: String, onError: (String) -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            val source = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(url))
            setMediaSource(source)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    onError(context.getString(R.string.my_cam_rtsp_error, error.errorCodeName))
                }
            })
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.player = player },
        )
    }
}

@Composable
private fun FailureCenter(
    icon: ImageVector,
    title: String,
    logLines: List<String>,
    onRetry: () -> Unit,
    onRetryAllDevices: (() -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = androidx.compose.ui.graphics.Color.White,
            )
            if (logLines.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accent,
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = stringResource(R.string.my_cam_log_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                        Spacer(Modifier.height(8.dp))
                        logLines.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            MaterialButton(
                text = stringResource(R.string.my_cam_retry),
                onClick = onRetry,
                accentColor = accent,
            )
            if (onRetryAllDevices != null) {
                Spacer(Modifier.height(12.dp))
                MaterialButton(
                    text = stringResource(R.string.my_cam_retry_all),
                    onClick = onRetryAllDevices,
                    accentColor = accent,
                )
            }
        }
    }
}
