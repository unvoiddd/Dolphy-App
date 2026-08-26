@file:OptIn(ExperimentalMaterial3Api::class)

package com.droid.dolphy.nfc.ui

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.nfc.NfcAdapter
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.TextGray
import com.droid.dolphy.TintedFrameAnimation
import com.droid.dolphy.nfc.NfcNdefPayloads
import com.droid.dolphy.nfc.NfcViewModel
import com.droid.dolphy.nfc.NfcWriteOutcome
import com.droid.dolphy.nfc.ui.enableNfcForegroundDispatch

private val NfcWriteBottomScrollPadding = 72.dp

@Composable
fun NfcEraseScreen(navController: NavController, viewModel: NfcViewModel) {
    NfcTagOperationScreen(
        navController = navController,
        viewModel = viewModel,
        title = stringResource(R.string.nfc_tool_erase_title),
        waitMessage = stringResource(R.string.nfc_apply_erase),
        armsEraseOnEnter = true,
    )
}

@Composable
fun NfcWriteWaitScreen(navController: NavController, viewModel: NfcViewModel) {
    NfcTagOperationScreen(
        navController = navController,
        viewModel = viewModel,
        title = stringResource(R.string.nfc_write_title),
        waitMessage = stringResource(R.string.nfc_apply_write),
        armsEraseOnEnter = false,
    )
}

@Composable
private fun NfcTagOperationScreen(
    navController: NavController,
    viewModel: NfcViewModel,
    title: String,
    waitMessage: String,
    armsEraseOnEnter: Boolean,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val accent = MaterialTheme.colorScheme.primary
    val adapter = remember(activity) { activity?.let { NfcAdapter.getDefaultAdapter(it.applicationContext) } }
    val nfcReady = adapter != null && adapter.isEnabled
    val outcome by viewModel.writeOutcome.collectAsState()

    if (armsEraseOnEnter) {
        DisposableEffect(Unit) {
            viewModel.armErase()
            onDispose { viewModel.disarmWrite() }
        }
    } else {
        DisposableEffect(Unit) {
            onDispose { viewModel.disarmWrite() }
        }
    }

    DisposableEffect(activity, nfcReady, outcome) {
        if (activity != null && nfcReady && outcome !is NfcWriteOutcome.Success) {
            enableNfcForegroundDispatch(activity, enabled = true)
        }
        onDispose {
            if (activity != null) {
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
                SectionTopBar(
                    transparent = true,
                    title = title,
                    onBack = { navController.popBackStack() },
                )
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (val o = outcome) {
                        is NfcWriteOutcome.Success -> SuccessBlock()
                        is NfcWriteOutcome.Failure -> FailureBlock(
                            message = o.message.ifBlank { stringResource(R.string.nfc_write_failed) },
                            accent = accent,
                            onRetry = {
                                viewModel.clearWriteFailure()
                                viewModel.retryLastWrite()
                            },
                        )
                        NfcWriteOutcome.Idle ->                         WaitingBlock(
                            nfcReady = nfcReady,
                            accent = accent,
                            waitMessage = waitMessage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingBlock(nfcReady: Boolean, accent: Color, waitMessage: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (nfcReady) {
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
                contentDescription = null,
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.passport_bad2_46x49),
                contentDescription = null,
                modifier = Modifier.size(width = 138.dp, height = 147.dp),
                colorFilter = ColorFilter.tint(accent, BlendMode.Modulate),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (nfcReady) waitMessage else stringResource(R.string.nfc_unavailable),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (nfcReady) stringResource(R.string.nfc_waiting) else stringResource(R.string.nfc_enable_instruction),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuccessBlock() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + scaleIn(tween(380), initialScale = 0.5f),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.cd_nfc_success),
                tint = Color(0xFF43A047),
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.nfc_data_written),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FailureBlock(message: String, accent: Color, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(20.dp))
        com.droid.dolphy.AccentButton(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
        ) {
            Text(stringResource(R.string.nfc_retry))
        }
    }
}

@Composable
fun NfcWriteMenuScreen(navController: NavController, viewModel: NfcViewModel) {
    val accent = MaterialTheme.colorScheme.primary
    DisposableEffect(Unit) {
        onDispose { viewModel.disarmWrite() }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = NfcWriteBottomScrollPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTopBar(
                    transparent = true,
                    title = stringResource(R.string.nfc_write_menu_title),
                    onBack = { navController.popBackStack() },
                )
                Text(
                    text = stringResource(R.string.nfc_write_pick_type),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WriteTypeCard(
                    title = stringResource(R.string.nfc_write_type_contact),
                    description = stringResource(R.string.nfc_write_type_contact_desc),
                    icon = Icons.Outlined.ContactPage,
                    accent = accent,
                onClick = { navController.navigate("other/nfc_write_contact") },
                )
                WriteTypeCard(
                    title = stringResource(R.string.nfc_write_type_wifi),
                    description = stringResource(R.string.nfc_write_type_wifi_desc),
                    icon = Icons.Outlined.Wifi,
                    accent = accent,
                onClick = { navController.navigate("other/nfc_write_form/wifi") },
                )
                WriteTypeCard(
                    title = stringResource(R.string.nfc_write_type_url),
                    description = stringResource(R.string.nfc_write_type_url_desc),
                    icon = Icons.Outlined.Link,
                    accent = accent,
                onClick = { navController.navigate("other/nfc_write_form/url") },
                )
                WriteTypeCard(
                    title = stringResource(R.string.nfc_write_type_email),
                    description = stringResource(R.string.nfc_write_type_email_desc),
                    icon = Icons.Outlined.Email,
                    accent = accent,
                onClick = { navController.navigate("other/nfc_write_form/email") },
                )
                WriteTypeCard(
                    title = stringResource(R.string.nfc_write_type_text),
                    description = stringResource(R.string.nfc_write_type_text_desc),
                    icon = Icons.Outlined.TextFields,
                    accent = accent,
                onClick = { navController.navigate("other/nfc_write_form/text") },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun WriteTypeCard(
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    MaterialCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        accentColor = accent,
        cornerRadius = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun NfcWriteContactScreen(navController: NavController, viewModel: NfcViewModel) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    var showPhoneDialog by remember { mutableStateOf(false) }
    var phoneInput by remember { mutableStateOf("") }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val pair = readContactNameAndPhone(context, uri)
        if (pair == null) {
            Toast.makeText(context, context.getString(R.string.nfc_contact_no_phone), Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val (name, phone) = pair
        try {
            viewModel.armWrite(NfcNdefPayloads.vCard(name, phone))
                            navController.navigate("other/nfc_write_wait")
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "", Toast.LENGTH_SHORT).show()
        }
    }

    val permission = Manifest.permission.READ_CONTACTS
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pickContact.launch(null)
        else {
            Toast.makeText(context, context.getString(R.string.nfc_contact_permission_rationale), Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.disarmWrite() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTopBar(
                    transparent = true,
                    title = stringResource(R.string.nfc_write_contact_title),
                    onBack = { navController.popBackStack() },
                )
                MaterialCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPhoneDialog = true },
                    accentColor = accent,
                    cornerRadius = 14.dp,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Nfc, null, tint = accent)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.nfc_write_add_phone), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.nfc_write_add_phone_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                MaterialCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (ContextCompat.checkSelfPermission(context, permission)) {
                                android.content.pm.PackageManager.PERMISSION_GRANTED -> pickContact.launch(null)
                                else -> permissionLauncher.launch(permission)
                            }
                        },
                    accentColor = accent,
                    cornerRadius = 14.dp,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.ContactPage, null, tint = accent)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.nfc_write_add_from_contacts), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.nfc_write_add_from_contacts_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showPhoneDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneDialog = false },
            title = { Text(stringResource(R.string.nfc_write_enter_phone_title)) },
            text = {
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.nfc_phone_placeholder)) },
                )
            },
            confirmButton = {
                com.droid.dolphy.AccentButton(onClick = {
                    val raw = phoneInput.trim()
                    if (raw.isEmpty()) return@AccentButton
                    try {
                        viewModel.armWrite(NfcNdefPayloads.telephone(raw))
                        showPhoneDialog = false
                        phoneInput = ""
                            navController.navigate("other/nfc_write_wait")
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "", Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.nfc_next)) }
            },
            dismissButton = {
                com.droid.dolphy.AccentButton(onClick = { showPhoneDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
fun NfcWriteFormScreen(kind: String, navController: NavController, viewModel: NfcViewModel) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    var ssid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var email by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var plainText by remember { mutableStateOf("") }

    val title = when (kind) {
        "wifi" -> stringResource(R.string.nfc_write_type_wifi)
        "url" -> stringResource(R.string.nfc_write_type_url)
        "email" -> stringResource(R.string.nfc_write_type_email)
        else -> stringResource(R.string.nfc_write_type_text)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.disarmWrite() }
    }

    fun go() {
        try {
            val msg = when (kind) {
                "wifi" -> NfcNdefPayloads.wifiCredential(ssid, wifiPass)
                "url" -> NfcNdefPayloads.url(url)
                "email" -> NfcNdefPayloads.email(email, subject, "")
                else -> NfcNdefPayloads.plainText(plainText)
            }
            viewModel.armWrite(msg)
                            navController.navigate("other/nfc_write_wait")
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: context.getString(R.string.nfc_write_failed), Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = NfcWriteBottomScrollPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTopBar(transparent = true, title = title, onBack = { navController.popBackStack() })

                when (kind) {
                    "wifi" -> {
                        OutlinedTextField(
                            value = ssid,
                            onValueChange = { ssid = it },
                            label = { Text(stringResource(R.string.nfc_label_ssid)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = wifiPass,
                            onValueChange = { wifiPass = it },
                            label = { Text(stringResource(R.string.nfc_label_wifi_password)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "url" -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(stringResource(R.string.nfc_label_url)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "email" -> {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(stringResource(R.string.nfc_label_email)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text(stringResource(R.string.nfc_label_email_subject)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> {
                        OutlinedTextField(
                            value = plainText,
                            onValueChange = { plainText = it },
                            label = { Text(stringResource(R.string.nfc_label_plain_text)) },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                com.droid.dolphy.AccentButton(
                    onClick = { go() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) {
                    Text(stringResource(R.string.nfc_next))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun readContactNameAndPhone(context: Context, contactUri: Uri): Pair<String, String>? {
    val cr = context.contentResolver
    cr.query(contactUri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)
        ?.use { c ->
            if (!c.moveToFirst()) return null
            val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            if (idIdx < 0) return null
            val id = c.getString(idIdx) ?: return null
            val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else ""
            cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                arrayOf(id),
                null,
            )?.use { pc ->
                if (pc.moveToFirst()) {
                    val numIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numIdx < 0) return null
                    val phone = pc.getString(numIdx) ?: return null
                    return Pair(name, phone)
                }
            }
        }
    return null
}

