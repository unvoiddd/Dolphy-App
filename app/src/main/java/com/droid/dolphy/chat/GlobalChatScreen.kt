package com.droid.dolphy.chat

import com.droid.dolphy.DolphyIconButton

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.SectionTopBar
import kotlinx.datetime.Clock
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.navigationBarsPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalChatScreen(navController: NavController) {
    val context = LocalContext.current
    val chatManager = remember { DolphyChatManager(context) }
    val messages = GlobalChatData.globalMessages
    val discoveredUsers = chatManager.discoveredUsers
    val currentUser = chatManager.currentUser
    val accentColor = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }


    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }


    LaunchedEffect(Unit) {
        val deviceName = android.os.Build.MODEL ?: "Android"
        if (!chatManager.startWithSavedName()) {

            chatManager.startAdvertising(deviceName)
            chatManager.startScanning()
        }

    }


    DisposableEffect(Unit) {
        onDispose {
            chatManager.stopScanning()
            chatManager.stopAdvertising()
        }
    }

    MaterialBackground(accentColor = accentColor) {
        Column(modifier = Modifier.fillMaxSize()) {
            SectionTopBar(
                transparent = true,
                title = "Dolphy Chat",
                onBack = {
                    chatManager.stopAdvertising()
                    chatManager.stopScanning()
                    navController.popBackStack()
                }
            )


            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (discoveredUsers.size > 0) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${discoveredUsers.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }


            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    GlobalMessageBubble(
                        message = message,
                        isFromMe = message.userName == currentUser.value,
                        accentColor = accentColor
                    )
                }
            }


            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 100.dp, top = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Введите сообщение") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedIndicatorColor = accentColor,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                )

                DolphyIconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            val success = chatManager.sendMessage(messageText)
                            if (success) {
                                messageText = ""
                            } else {

                            }
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    enabled = messageText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun GlobalMessageBubble(
    message: GlobalMessage,
    isFromMe: Boolean,
    accentColor: Color
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = dateFormat.format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
    ) {

        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isFromMe) {
                Text(
                    text = message.userName,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }


        Box(
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        if (isFromMe) {
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 4.dp
                            )
                        } else {
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 4.dp,
                                bottomEnd = 16.dp
                            )
                        }
                    )
                    .background(
                        if (isFromMe) accentColor else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(12.dp, 8.dp)
            ) {
                Text(
                    text = message.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFromMe) Color.White else MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

