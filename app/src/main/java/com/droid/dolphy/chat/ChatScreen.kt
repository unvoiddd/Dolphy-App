package com.droid.dolphy.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.AccentButton
import com.droid.dolphy.BluetoothPermissionGate
import com.droid.dolphy.BlurHeaderBox
import com.droid.dolphy.ExpressiveDialog
import com.droid.dolphy.R
import com.droid.dolphy.WavyCircularProgressIndicator
import com.droid.dolphy.rememberPressShrink
import java.text.SimpleDateFormat
import java.util.Date

private enum class ChatStatus { CONNECTED, SCANNING, OFFLINE }

@Composable
fun GlobalChatScreen(navController: NavController) {
    BluetoothPermissionGate(advertise = true, onBack = { navController.popBackStack() }) { ChatContent(navController) }
}

@Composable
private fun ChatContent(navController: NavController) {
    val context = LocalContext.current
    var generation by remember { mutableIntStateOf(0) }
    val manager = remember(generation) { DolphyChatManager(context) }
    val messages = GlobalChatData.globalMessages
    val listState = rememberLazyListState()
    var text by rememberSaveable { mutableStateOf("") }
    var showName by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(manager) { if (!manager.startWithSavedName()) manager.start(android.os.Build.MODEL) }
    DisposableEffect(manager) { onDispose { manager.close() } }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    val status = when {
        manager.error.value != null -> ChatStatus.OFFLINE
        manager.isScanning.value && manager.discoveredUsers.isNotEmpty() -> ChatStatus.CONNECTED
        manager.isScanning.value -> ChatStatus.SCANNING
        else -> ChatStatus.OFFLINE
    }
    val sendAction = {
        if (manager.sendMessage(text.trim())) text = ""
    }

    BlurHeaderBox(
        title = "Dolphy Chat",
        onBack = { navController.popBackStack() },
        accentColor = MaterialTheme.colorScheme.primary,
        actions = {
            TextButton(onClick = { name = manager.currentUser.value.orEmpty(); showName = true }) {
                Text(stringResource(R.string.chat_change_name))
            }
        },
    ) { headerPadding ->
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(headerPadding))
            ChatStatusHeader(status = status, peerCount = manager.discoveredUsers.size)
            manager.error.value?.let { error ->
                Spacer(Modifier.height(8.dp))
                ChatErrorBanner(error = error, onRetry = { manager.close(); generation++ })
            }
            if (messages.isEmpty()) {
                ChatEmptyState(modifier = Modifier.weight(1f), scanning = manager.isScanning.value && status != ChatStatus.OFFLINE)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            mine = message.senderId == manager.senderId,
                            onRetry = { manager.sendMessage(it.message, it.id) },
                        )
                    }
                }
            }
            val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            val bottomBarClearance = if (imeVisible) 0.dp else 96.dp
            ChatInputRow(
                text = text,
                onTextChange = { text = it },
                sendEnabled = text.isNotBlank(),
                performSend = sendAction,
                modifier = Modifier.padding(bottom = bottomBarClearance),
            )
        }
    }
    if (showName) ExpressiveDialog(
        onDismissRequest = { showName = false },
        title = { Text(stringResource(R.string.chat_change_name)) },
        text = { TextField(
            value = name,
            onValueChange = { name = it.take(64) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        ) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            context.getSharedPreferences("dolphy_chat_prefs", 0).edit().putString("user_name", name.trim()).apply()
            manager.close()
            generation++
            showName = false
        }) { Text(stringResource(R.string.chat_save_name)) } },
        dismissButton = { TextButton(onClick = { showName = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChatStatusHeader(status: ChatStatus, peerCount: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatStatusChip(status)
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.chat_peers, peerCount),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.chat_privacy_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatStatusChip(status: ChatStatus) {
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    val icon: ImageVector
    val labelRes: Int
    when (status) {
        ChatStatus.CONNECTED -> {
            container = scheme.primaryContainer; content = scheme.onPrimaryContainer
            icon = Icons.Filled.CheckCircle; labelRes = R.string.chat_status_connected
        }
        ChatStatus.SCANNING -> {
            container = scheme.secondaryContainer; content = scheme.onSecondaryContainer
            icon = Icons.Filled.BluetoothSearching; labelRes = R.string.chat_status_scanning
        }
        ChatStatus.OFFLINE -> {
            container = scheme.errorContainer; content = scheme.onErrorContainer
            icon = Icons.Filled.BluetoothDisabled; labelRes = R.string.chat_status_offline
        }
    }
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ChatErrorBanner(error: String, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            AccentButton(
                onClick = onRetry,
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
            ) {
                Text(
                    stringResource(R.string.chat_retry),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ChatEmptyState(modifier: Modifier = Modifier, scanning: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
            if (scanning) {
                WavyCircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    trackColor = Color.Transparent,
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(scheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Forum,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.chat_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChatMessageItem(message: GlobalMessage, mine: Boolean, onRetry: (GlobalMessage) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val timeText = SimpleDateFormat("HH:mm", LocalConfiguration.current.locales[0]).format(Date(message.timestamp))
    val bubbleShape = if (mine) {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 8.dp)
    } else {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 28.dp)
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Text(
            message.userName + " · " + timeText,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
        )
        Surface(
            shape = bubbleShape,
            color = if (mine) scheme.primaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (mine) scheme.onPrimaryContainer else scheme.onSurface,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                message.message,
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (mine) scheme.onPrimaryContainer else scheme.onSurface,
            )
        }
        if (mine) {
            val (deliveryIcon, deliveryRes, deliveryTint) = when (message.delivery) {
                MessageDelivery.PENDING -> Triple(Icons.Filled.Schedule, R.string.chat_delivery_pending, scheme.onSurfaceVariant)
                MessageDelivery.SENT -> Triple(Icons.Filled.DoneAll, R.string.chat_delivery_sent, scheme.onSurfaceVariant)
                MessageDelivery.FAILED -> Triple(Icons.Filled.ErrorOutline, R.string.chat_delivery_failed, scheme.error)
                MessageDelivery.RECEIVED -> Triple(Icons.Filled.DoneAll, R.string.chat_delivery_sent, scheme.onSurfaceVariant)
            }
            Row(
                Modifier.padding(top = 2.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(deliveryIcon, contentDescription = null, tint = deliveryTint, modifier = Modifier.size(12.dp))
                Text(
                    stringResource(deliveryRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = deliveryTint,
                )
            }
            if (message.delivery == MessageDelivery.FAILED) {
                TextButton(
                    onClick = { onRetry(message) },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = scheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.chat_retry), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ChatInputRow(
    text: String,
    onTextChange: (String) -> Unit,
    sendEnabled: Boolean,
    performSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (interaction, scale) = rememberPressShrink(pressedScale = 0.88f)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scheme.surfaceContainer,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_message_hint)) },
                maxLines = 4,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = scheme.onSurface,
                    unfocusedTextColor = scheme.onSurface,
                    cursorColor = scheme.primary,
                    focusedContainerColor = scheme.surfaceContainerHighest,
                    unfocusedContainerColor = scheme.surfaceContainerHighest,
                    disabledContainerColor = scheme.surfaceContainerHighest,
                    errorContainerColor = scheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = scheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = scheme.onSurfaceVariant,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (sendEnabled) performSend() }),
            )
            FilledIconButton(
                onClick = performSend,
                enabled = sendEnabled,
                modifier = Modifier.size(56.dp).scale(scale),
                shape = CircleShape,
                interactionSource = interaction,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary,
                    disabledContainerColor = scheme.surfaceContainerHighest,
                    disabledContentColor = scheme.onSurface.copy(alpha = 0.38f),
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send_message))
            }
        }
    }
}
