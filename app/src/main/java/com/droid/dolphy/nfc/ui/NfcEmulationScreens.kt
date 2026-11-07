package com.droid.dolphy.nfc.ui

import com.droid.dolphy.DolphyIconButton

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.BlurHeaderBox
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.TextGray
import com.droid.dolphy.trackNfcEmulate
import com.droid.dolphy.nfc.EmulatedNfcTag
import com.droid.dolphy.nfc.NfcTagEmulationStore
import com.droid.dolphy.nfc.NfcType4HostApduService
import java.net.URI
import kotlinx.coroutines.withContext

@Composable
fun NfcTagEmulationListScreen(navController: NavController) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    var tags by remember { mutableStateOf(NfcTagEmulationStore.loadTags(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var error by remember { mutableStateOf<String?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var selectedTagIds by remember { mutableStateOf(setOf<Long>()) }

    Box(modifier = Modifier.fillMaxSize()) {
        EmulationHeaderScaffold(
            title = stringResource(R.string.nfc_emulation_title),
            onBack = { navController.popBackStack() },
            accentColor = accent,
            editMode = editMode,
            hasSelection = selectedTagIds.isNotEmpty(),
            onToggleEditMode = {
                editMode = !editMode
                if (!editMode) selectedTagIds = emptySet()
            },
            onDeleteSelected = {
                if (selectedTagIds.isEmpty()) return@EmulationHeaderScaffold
                val updated = tags.filterNot { it.id in selectedTagIds }
                tags = updated
                NfcTagEmulationStore.saveTags(context, updated)
                selectedTagIds = emptySet()
                editMode = false
            },
        ) { headerPadding ->
            if (tags.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = headerPadding + 12.dp)
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.nfc_no_tags),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = headerPadding + 12.dp, bottom = 132.dp),
                ) {
                    items(tags, key = { it.id }) { tag ->
                        EmulatedTagRow(
                            tag = tag,
                            editMode = editMode,
                            selected = selectedTagIds.contains(tag.id),
                            onSelectionChange = { checked ->
                                selectedTagIds = if (checked) selectedTagIds + tag.id else selectedTagIds - tag.id
                            },
                        ) {
                            if (editMode) {
                                selectedTagIds = if (selectedTagIds.contains(tag.id)) selectedTagIds - tag.id else selectedTagIds + tag.id
                            } else {
                                NfcTagEmulationStore.setActiveTag(context, tag)
                navController.navigate("other/nfc_emulator_run/${tag.id}")
                            }
                        }
                    }
                }
            }
        }

        if (!editMode) {
            FloatingActionButton(
                onClick = {
                    showAddDialog = true
                    error = null
                },
                containerColor = accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 108.dp),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_add),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.nfc_new_tag)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.nfc_label_name)) },
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.nfc_label_url)) },
                    )
                    if (!error.isNullOrBlank()) {
                        Text(
                            text = error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                com.droid.dolphy.AccentButton(
                    onClick = {
                        val fixedName = name.trim()
                        val fixedUrl = normalizeUrl(url.trim())
                        val isValid = isValidWebUrl(fixedUrl)
                        if (fixedName.isBlank()) {
                            error = context.getString(R.string.nfc_error_empty_name)
                            return@AccentButton
                        }
                        if (!isValid) {
                            error = context.getString(R.string.nfc_error_invalid_url)
                            return@AccentButton
                        }

                        val updated = listOf(
                            EmulatedNfcTag(
                                id = System.currentTimeMillis(),
                                name = fixedName,
                                url = fixedUrl,
                            )
                        ) + tags
                        tags = updated
                        NfcTagEmulationStore.saveTags(context, updated)
                        showAddDialog = false
                        name = ""
                        url = "https://"
                        error = null
                    }
                ) {
                    Text(stringResource(R.string.nfc_add))
                }
            },
            dismissButton = {
                com.droid.dolphy.AccentButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun NfcTagEmulationRunScreen(navController: NavController, tagId: Long) {
    val context = LocalContext.current
    val activity = context as? Activity
    val accent = MaterialTheme.colorScheme.primary
    val tag = remember(tagId) { NfcTagEmulationStore.loadTags(context).firstOrNull { it.id == tagId }
        ?: NfcTagEmulationStore.getActiveTag(context)?.takeIf { it.id == tagId } }
    val hasHce = remember { supportsHce(context) }
    val nfcEnabled = remember { NfcAdapter.getDefaultAdapter(context)?.isEnabled == true }
    val infinite = rememberInfiniteTransition(label = "nfc_emulation")
    val scale by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(animation = tween(950), repeatMode = RepeatMode.Reverse),
        label = "emulation_scale",
    )

    var nfcOn by remember { mutableStateOf(nfcEnabled) }


    LaunchedEffect(tagId, hasHce, nfcOn) {
        if (hasHce && nfcOn && tag != null) {
            trackNfcEmulate(context)
        }
    }
    DisposableEffect(activity, tag) {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        val lifecycle = (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        fun enable() {
            nfcOn = adapter?.isEnabled == true
            if (tag != null && hasHce && nfcOn && adapter != null && activity != null) {
                NfcTagEmulationStore.setActiveTag(context, tag)
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                runCatching { CardEmulation.getInstance(adapter).setPreferredService(activity, ComponentName(context, NfcType4HostApduService::class.java)) }
            }
        }
        fun disable() {
            if (NfcTagEmulationStore.getActiveTag(context)?.id == tag?.id) NfcTagEmulationStore.setActiveTag(context, null)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (activity != null && adapter != null && hasHce) runCatching { CardEmulation.getInstance(adapter).unsetPreferredService(activity) }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) enable()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) disable()
        }
        lifecycle?.addObserver(observer)
        if (lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true) enable()
        onDispose { lifecycle?.removeObserver(observer); disable() }
    }

    EmulationHeaderScaffold(
        title = stringResource(R.string.nfc_emulation_title),
        onBack = { navController.popBackStack() },
        accentColor = accent,
    ) { headerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = headerPadding + 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!nfcOn || !hasHce || tag == null) {
                    Image(
                        painter = painterResource(id = R.drawable.passport_bad2_46x49),
                        contentDescription = "NFC unavailable",
                        modifier = Modifier
                            .size(width = 138.dp, height = 147.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale),
                        colorFilter = ColorFilter.tint(
                            color = accent,
                            blendMode = androidx.compose.ui.graphics.BlendMode.Modulate
                        ),
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.nfc_dolphin_emulation_51x64_transparent),
                        contentDescription = "NFC emulation",
                        modifier = Modifier
                            .size(width = 153.dp, height = 192.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale),
                        colorFilter = ColorFilter.tint(accent),
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (nfcOn && hasHce && tag != null) stringResource(R.string.nfc_emulating) else stringResource(R.string.nfc_unavailable),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        !nfcOn -> stringResource(R.string.nfc_enable_instruction)
                        else -> "NFC Forum Type 4"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = tag?.name ?: stringResource(R.string.nfc_tag_not_found),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tag?.url ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when {
                        tag == null -> stringResource(R.string.nfc_tag_not_found)
                        !hasHce -> stringResource(R.string.nfc_hce_not_supported)
                        !nfcOn -> stringResource(R.string.nfc_disabled_off)
                        else -> stringResource(R.string.nfc_present_to_reader)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EmulatedTagRow(
    tag: EmulatedNfcTag,
    editMode: Boolean,
    selected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    MaterialCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        accentColor = accent,
        cornerRadius = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onSelectionChange
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tag.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmulationHeaderScaffold(
    title: String,
    onBack: () -> Unit,
    accentColor: Color,
    editMode: Boolean = false,
    hasSelection: Boolean = false,
    onToggleEditMode: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    content: @Composable (headerPadding: Dp) -> Unit,
) {
    BlurHeaderBox(
        title = title,
        onBack = onBack,
        accentColor = accentColor,
        actions = {
            DolphyIconButton(onClick = onToggleEditMode) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = if (editMode) "Exit edit mode" else "Edit"
                )
            }
            if (editMode) {
                DolphyIconButton(
                    onClick = onDeleteSelected,
                    enabled = hasSelection
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.nfc_delete_selected)
                    )
                }
            }
        },
    ) { headerPadding ->
        content(headerPadding)
    }
}

private fun supportsHce(context: Context): Boolean =
    context.packageManager.hasSystemFeature("android.hardware.nfc.hce")

private fun normalizeUrl(raw: String): String {
    if (raw.isBlank()) return raw
    return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
}

private fun isValidWebUrl(url: String): Boolean {
    return runCatching {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        (scheme == "http" || scheme == "https") && !host.isNullOrBlank()
    }.recoverCatching {
        false
    }.getOrDefault(false)
}
