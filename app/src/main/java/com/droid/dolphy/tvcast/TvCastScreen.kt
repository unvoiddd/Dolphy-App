package com.droid.dolphy.tvcast

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.mediarouter.app.MediaRouteButton
import androidx.navigation.NavController
import com.droid.dolphy.BlurHeaderBox
import com.droid.dolphy.MaterialButton
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.MaterialOutlinedButton
import com.droid.dolphy.R
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private val castExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "dolphy-cast").apply { isDaemon = true }
}

@Composable
fun SmartTvCastScreen(navController: NavController, model: TvCastViewModel = viewModel()) {
    val context = LocalContext.current
    val state by model.state.collectAsState()
    var cast by remember { mutableStateOf<CastContext?>(null) }
    val missingSession = stringResource(R.string.cast_no_session)
    val scheme = MaterialTheme.colorScheme
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(model::importVideo) }
    DisposableEffect(context) {
        var active = true
        runCatching {
            CastContext.getSharedInstance(context.applicationContext, castExecutor)
                .addOnSuccessListener { if (active) cast = it }
                .addOnFailureListener { if (active) model.result(false, it.message) }
        }.onFailure { model.result(false, it.message) }
        onDispose { active = false }
    }
    LaunchedEffect(Unit) { model.discover() }

    BlurHeaderBox(
        title = stringResource(R.string.smarttv_cast_title),
        onBack = { navController.popBackStack() },
        accentColor = scheme.primary,
    ) { headerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = headerPadding + 16.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "import") {
                MaterialCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CastIconChip(Icons.Default.VideoFile)
                            Text(
                                text = stringResource(if (state.importing) R.string.cast_importing else R.string.smarttv_cast_choose_video),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.importing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = scheme.primary)
                            }
                        }
                        MaterialButton(
                            text = stringResource(R.string.smarttv_cast_choose_video),
                            onClick = { picker.launch("video/*") },
                            enabled = !state.importing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.notice?.let { notice ->
                            Text(
                                text = notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item(key = "google_cast") {
                MaterialCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CastIconChip(Icons.Default.Cast)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.cast_google),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.cast_google_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            if (cast != null) AndroidView(
                                factory = { ctx ->
                                    runCatching {
                                        MediaRouteButton(ctx).apply {
                                            CastButtonFactory.setUpMediaRouteButton(ctx, this)
                                        }
                                    }.getOrElse { android.widget.FrameLayout(ctx) }
                                },
                                modifier = Modifier.size(48.dp),
                            )
                        }
                        MaterialButton(
                            text = stringResource(R.string.cast_play),
                            onClick = {
                                val client = cast?.sessionManager?.currentCastSession?.remoteMediaClient
                                if (client == null) {
                                    model.result(false, missingSession)
                                } else {
                                    model.loading()
                                    val media = MediaInfo.Builder(state.videoUrl!!).setContentType(state.mime)
                                        .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).build()
                                    client.load(MediaLoadRequestData.Builder().setMediaInfo(media).setAutoplay(true).build())
                                        .setResultCallback { model.result(it.status.isSuccess, it.status.statusMessage) }
                                }
                            },
                            enabled = state.videoUrl != null && !state.importing && !state.loading && cast != null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item(key = "dlna") {
                MaterialCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CastIconChip(Icons.Default.Lan)
                            Text(
                                text = "DLNA / UPnP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = scheme.primary)
                            }
                        }
                        MaterialOutlinedButton(
                            text = stringResource(if (state.scanning) R.string.wifi_print_searching else R.string.wifi_print_scan),
                            onClick = model::discover,
                            enabled = !state.scanning,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.devices.isEmpty()) {
                            Text(
                                text = stringResource(R.string.smarttv_cast_no_devices),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(state.devices, key = { it.controlUrl }) { device ->
                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = scheme.primary,
                    contentPadding = 0.dp,
                    onClick = { model.play(device) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = scheme.primary,
                        )
                        Text(
                            text = device.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (state.videoUrl != null && !state.importing && !state.loading) {
                                scheme.primary
                            } else {
                                scheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                        )
                    }
                }
            }

            if (state.videoUrl != null) {
                item(key = "browser") {
                    MaterialCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CastIconChip(Icons.Default.OpenInNew)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.cast_browser_hint),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onSurface,
                                    )
                                    Text(
                                        text = state.videoUrl!!.replace("/video", "/play"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                            MaterialOutlinedButton(
                                text = stringResource(R.string.smarttv_cast_open_local_site),
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.videoUrl!!.replace("/video", "/play"))))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CastIconChip(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
