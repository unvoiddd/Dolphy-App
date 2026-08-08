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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.TextGray
import com.droid.dolphy.trackNfcEmulate
import com.droid.dolphy.nfc.EmulatedNfcTag
import com.droid.dolphy.nfc.NfcTagEmulationStore
import com.droid.dolphy.nfc.NfcType4HostApduService
import com.droid.dolphy.nfc.RootNfcHelper
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
        MaterialBackground(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                EmulationTopBar(
                    title = stringResource(R.string.nfc_emulation_title),
                    onBack = { navController.popBackStack() },
                    editMode = editMode,
                    hasSelection = selectedTagIds.isNotEmpty(),
                    onToggleEditMode = {
                        editMode = !editMode
                        if (!editMode) selectedTagIds = emptySet()
                    },
                    onDeleteSelected = {
                        if (selectedTagIds.isEmpty()) return@EmulationTopBar
                        val updated = tags.filterNot { it.id in selectedTagIds }
                        tags = updated
                        NfcTagEmulationStore.saveTags(context, updated)
                        selectedTagIds = emptySet()
                        editMode = false
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (tags.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.nfc_no_tags),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 132.dp),
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
                TextButton(
                    onClick = {
                        val fixedName = name.trim()
                        val fixedUrl = normalizeUrl(url.trim())
                        val isValid = isValidWebUrl(fixedUrl)
                        if (fixedName.isBlank()) {
                            error = context.getString(R.string.nfc_error_empty_name)
                            return@TextButton
                        }
                        if (!isValid) {
                            error = context.getString(R.string.nfc_error_invalid_url)
                            return@TextButton
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
                TextButton(onClick = { showAddDialog = false }) {
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
    val tag = remember(tagId) { NfcTagEmulationStore.loadTags(context).firstOrNull { it.id == tagId } }
    val hasHce = remember { supportsHce(context) }
    val nfcEnabled = remember { NfcAdapter.getDefaultAdapter(context)?.isEnabled == true }
    val isDefaultPaymentApp = remember { isDolphyDefaultPaymentService(context) }
    val infinite = rememberInfiniteTransition(label = "nfc_emulation")
    val scale by infinite.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(animation = tween(950), repeatMode = RepeatMode.Reverse),
        label = "emulation_scale",
    )

    var rootMode by remember { mutableStateOf(false) }
    var nfcOn by remember { mutableStateOf(nfcEnabled) }
    var isDefault by remember { mutableStateOf(isDefaultPaymentApp) }

    LaunchedEffect(tagId) {
        tag?.let { NfcTagEmulationStore.setActiveTag(context, it) }
        val mode = withContext(kotlinx.coroutines.Dispatchers.IO) {
            RootNfcHelper.prepareForEmulation(context)
        }
        rootMode = mode == "root"
        nfcOn = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true || RootNfcHelper.isNfcEnabled(context)
        isDefault = isDolphyDefaultPaymentService(context) || rootMode
    }

    LaunchedEffect(hasHce, nfcOn, isDefault) {
        if (hasHce && nfcOn && !isDefault && !rootMode) {
            openPaymentSettingsForDolphy(context)
        }
    }
    LaunchedEffect(tagId, hasHce, nfcOn, isDefault) {
        if (hasHce && nfcOn && isDefault && tag != null) {
            trackNfcEmulate(context)
        }
    }
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                EmulationTopBar(
                    title = stringResource(R.string.nfc_emulation_title),
                    onBack = { navController.popBackStack() },
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!nfcOn) {
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
                            text = if (nfcOn) stringResource(R.string.nfc_emulating) else stringResource(R.string.nfc_unavailable),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                !nfcOn -> stringResource(R.string.nfc_enable_instruction)
                                rootMode -> "NFC Forum Type 4 · root"
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
                                !hasHce -> stringResource(R.string.nfc_hce_not_supported)
                                !nfcEnabled -> stringResource(R.string.nfc_disabled_off)
                                isDefaultPaymentApp -> stringResource(R.string.nfc_present_to_reader)
                                else -> stringResource(R.string.nfc_set_default_payment)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!isDefaultPaymentApp) {
                            Text(
                                text = stringResource(R.string.nfc_open_payment_settings),
                                color = accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { openPaymentSettingsForDolphy(context) },
                            )
                        }
                    }
                }
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
private fun EmulationTopBar(
    title: String,
    onBack: () -> Unit,
    editMode: Boolean = false,
    hasSelection: Boolean = false,
    onToggleEditMode: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
) {
    SectionTopBar(
        title = title,
        onBack = onBack,
        transparent = true,
        actions = {
            DolphyIconButton(onClick = onToggleEditMode) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = if (editMode) "Выйти из режима редактирования" else "Редактировать"
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
    )
}

private fun supportsHce(context: Context): Boolean {
    val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
    val emulation = CardEmulation.getInstance(adapter)
    return emulation.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_PAYMENT) ||
        context.packageManager.hasSystemFeature("android.hardware.nfc.hce")
}

private fun isDolphyDefaultPaymentService(context: Context): Boolean {
    val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
    return runCatching {
        CardEmulation.getInstance(adapter).isDefaultServiceForCategory(
            ComponentName(context, NfcType4HostApduService::class.java),
            CardEmulation.CATEGORY_PAYMENT,
        )
    }.getOrDefault(false)
}

private fun openPaymentSettingsForDolphy(context: Context): Boolean {
    val intents = buildList {
        val component = ComponentName(context, NfcType4HostApduService::class.java)

        add(
            Intent(CardEmulation.ACTION_CHANGE_DEFAULT).apply {
                putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT)
                putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, component)
            }
        )
        add(Intent("android.settings.NFC_PAYMENT_SETTINGS"))
        add(Intent(Settings.ACTION_NFC_SETTINGS))
        add(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }

    intents.forEach { intent ->
        val launched = runCatching {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        if (launched) return true
    }

    return false
}

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

