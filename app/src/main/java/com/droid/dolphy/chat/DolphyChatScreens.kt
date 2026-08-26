package com.droid.dolphy.chat

import com.droid.dolphy.DolphyIconButton

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.droid.dolphy.R
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.SectionTopBar
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import android.content.SharedPreferences


private val DOLPHY_CHAT_UUID = ParcelUuid.fromString("00001101-0000-1000-8000-00805f9b34fb")
private val DOLPHY_SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
private val DOLPHY_CHARACTERISTIC_UUID = UUID.fromString("00001102-0000-1000-8000-00805f9b34fb")


object GlobalChatData {
    private const val PREFS_NAME = "dolphy_chat_prefs"
    private const val KEY_USER_NAME = "user_name"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    fun hasUserName(): Boolean {
        return prefs.contains(KEY_USER_NAME)
    }


    val globalMessages = mutableStateListOf<GlobalMessage>()
}

@Serializable
data class GlobalMessage(
    val id: String = UUID.randomUUID().toString(),
    val userName: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ChatUser(
    val id: String,
    val name: String,
    val address: String,
    val lastSeen: Long = System.currentTimeMillis()
)

@Serializable
data class ChatMessage(
    val from: String,
    val to: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class DolphyChatManager(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanCallback: ScanCallback? = null
    private var gattServer: BluetoothGattServer? = null
    private var gattConnections = ConcurrentHashMap<String, BluetoothGatt>()

    val discoveredUsers = mutableStateListOf<ChatUser>()
    val isScanning = mutableStateOf(false)
    val isAdvertising = mutableStateOf(false)
    val currentUser = mutableStateOf<String?>(null)

    private val messageHandlers = mutableMapOf<String, (ChatMessage) -> Unit>()

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            value?.let { data ->
                try {

                    val message = String(data, StandardCharsets.UTF_8)
                    val parts = message.split(":", limit = 2)

                    if (parts.size == 2) {
                        val fromName = parts[0]
                        val messageText = parts[1]


                        val globalMessage = GlobalMessage(
                            userName = fromName,
                            message = messageText
                        )
                        GlobalChatData.globalMessages.add(globalMessage)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }


            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }
    }

    init {

        GlobalChatData.init(context)

        try {
            advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startWithSavedName(): Boolean {
        val savedName = GlobalChatData.getUserName()
        return if (savedName != null) {

            startAdvertising(savedName)
            startScanning()
            true
        } else {
            false
        }
    }

    fun startAdvertising(userName: String): Boolean {
        if (!hasPermissions()) return false
        if (!isBluetoothEnabled()) return false
        if (advertiser == null || bluetoothAdapter == null) return false

        currentUser.value = userName


        GlobalChatData.saveUserName(userName)


        startGattServer()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .setConnectable(true)
            .build()

        val serviceData = "DOLPHY:$userName".toByteArray(StandardCharsets.UTF_8)

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(DOLPHY_CHAT_UUID)
            .addServiceData(DOLPHY_CHAT_UUID, serviceData)
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertisingCallback)
            isAdvertising.value = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            isAdvertising.value = false
            return false
        }
    }

    private fun startGattServer() {
        gattServer?.let { server ->
            val service = BluetoothGattService(DOLPHY_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            val characteristic = BluetoothGattCharacteristic(
                DOLPHY_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            characteristic.addDescriptor(
                BluetoothGattDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )

            service.addCharacteristic(characteristic)
            server.addService(service)

        }
    }

    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertisingCallback)
        isAdvertising.value = false
    }

    fun startScanning() {
        if (!hasPermissions() || bluetoothLeScanner == null) return
        if (!isBluetoothEnabled()) return


        stopScanning()


        discoveredUsers.clear()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val scanRecord = result.scanRecord ?: return


                    val serviceData = scanRecord.getServiceData(DOLPHY_CHAT_UUID)
                    val serviceUuids = scanRecord.serviceUuids


                    val isDolphyDevice = serviceUuids?.contains(DOLPHY_CHAT_UUID) == true ||
                        (serviceData != null && String(serviceData, StandardCharsets.UTF_8).startsWith("DOLPHY:"))

                    if (isDolphyDevice) {
                        val userName = if (serviceData != null) {
                            try {
                                String(serviceData, StandardCharsets.UTF_8).removePrefix("DOLPHY:")
                            } catch (e: Exception) {
                                "Unknown"
                            }
                        } else {
                            result.device.name ?: "Unknown"
                        }


                        if (userName != currentUser.value) {
                            val user = ChatUser(
                                id = result.device.address,
                                name = userName,
                                address = result.device.address
                            )


                            val existingIndex = discoveredUsers.indexOfFirst { it.id == user.id }
                            if (existingIndex >= 0) {
                                discoveredUsers[existingIndex] = user.copy(lastSeen = System.currentTimeMillis())
                            } else {
                                discoveredUsers.add(user)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }
        }


        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothLeScanner?.startScan(scanCallback)
            isScanning.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            isScanning.value = false
        }
    }

    fun stopScanning() {
        scanCallback?.let { bluetoothLeScanner?.stopScan(it) }
        isScanning.value = false
    }

    fun sendMessage(message: String): Boolean {
        try {
            val userName = currentUser.value ?: return false


            val globalMessage = GlobalMessage(
                userName = userName,
                message = message
            )
            GlobalChatData.globalMessages.add(globalMessage)


            if (discoveredUsers.isEmpty()) {

                return true
            }


            val simpleMessage = "$userName:$message"


            discoveredUsers.forEach { user ->
                sendQuickMessage(user, simpleMessage)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun createMessageWithChecksum(userName: String, message: String): String {

        val fullMessage = "$userName:$message"
        val checksum = fullMessage.hashCode().toString(16).uppercase()
        return "$fullMessage|$checksum"
    }

    private fun verifyMessageChecksum(messageWithChecksum: String): Pair<String, String>? {
        val parts = messageWithChecksum.split("|")
        if (parts.size != 2) return null

        val message = parts[0]
        val receivedChecksum = parts[1]
        val calculatedChecksum = message.hashCode().toString(16).uppercase()

        return if (receivedChecksum == calculatedChecksum) {
            val messageParts = message.split(":", limit = 2)
            if (messageParts.size == 2) {
                Pair(messageParts[0], messageParts[1])
            } else null
        } else null
    }

    private fun sendQuickMessage(user: ChatUser, message: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(user.address)
            if (device != null) {

                device.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {

                            gatt?.discoverServices()
                        } else {

                            gatt?.disconnect()
                            gatt?.close()
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val service = gatt?.getService(DOLPHY_SERVICE_UUID)
                            val characteristic = service?.getCharacteristic(DOLPHY_CHARACTERISTIC_UUID)

                            if (characteristic != null) {

                                val messageData = message.toByteArray(StandardCharsets.UTF_8)

                                if (messageData.size <= 20) {

                                    characteristic.value = messageData
                                    gatt.writeCharacteristic(characteristic)
                                } else {

                                    sendChunkedMessage(gatt, characteristic, messageData)
                                }
                            }
                        }
                    }

                    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {

                        gatt?.disconnect()
                        gatt?.close()
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendChunkedMessage(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        val chunks = mutableListOf<ByteArray>()
        for (i in data.indices step 20) {
            val end = minOf(i + 20, data.size)
            chunks.add(data.copyOfRange(i, end))
        }


        chunks.forEachIndexed { index, chunk ->
            characteristic.value = chunk
            gatt.writeCharacteristic(characteristic)
        }
    }

    fun setMessageHandler(userId: String, handler: (ChatMessage) -> Unit) {
        messageHandlers[userId] = handler
    }

    private fun hasPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    private val advertisingCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {

        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DolphyChatWelcomeScreen(navController: NavController) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val chatManager = remember {
        try {
            DolphyChatManager(context)
        } catch (e: Exception) {
            hasError = true
            errorMessage = "Ошибка инициализации: ${e.message}"
            null
        }
    }
    var userName by remember { mutableStateOf("") }
    val accentColor = MaterialTheme.colorScheme.primary


    val isBluetoothAvailable = remember {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter
            bluetoothAdapter != null
        } catch (e: Exception) {
            hasError = true
            errorMessage = "Ошибка Bluetooth: ${e.message}"
            false
        }
    }

    MaterialBackground(accentColor = accentColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (hasError) {

                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(80.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Произошла ошибка",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            } else if (!isBluetoothAvailable) {

                Icon(
                    imageVector = Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(80.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Bluetooth недоступен",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Для работы Dolphy Chat необходим Bluetooth",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            } else {

                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(80.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))


                Text(
                    text = "Dolphy Chat",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))


                Text(
                    text = "Чат по BLE, дистанция 10-30 метров",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            if (isBluetoothAvailable && !hasError) {
                Spacer(modifier = Modifier.height(48.dp))


                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Введите имя") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedIndicatorColor = accentColor,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))


                com.droid.dolphy.AccentButton(
                    onClick = {
                        if (userName.isNotBlank()) {

                            navController.navigate("other/dolphy_chat_main/${userName}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = userName.isNotBlank()
                ) {
                    Text(
                        text = "Далее",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DolphyChatMainScreen(userName: String, navController: NavController) {
    val context = LocalContext.current
    val chatManager = remember { DolphyChatManager(context) }
    val discoveredUsers = chatManager.discoveredUsers
    val isScanning = chatManager.isScanning
    val isAdvertising = chatManager.isAdvertising
    val accentColor = MaterialTheme.colorScheme.primary


    LaunchedEffect(Unit) {

        chatManager.startAdvertising(userName)
        chatManager.startScanning()
    }


    DisposableEffect(Unit) {
        onDispose {
            chatManager.stopAdvertising()
            chatManager.stopScanning()
        }
    }

    MaterialBackground(accentColor = accentColor) {
        Column(modifier = Modifier.fillMaxSize()) {
            SectionTopBar(
                transparent = true,
                title = "Dolphy Chat",
                onBack = { navController.popBackStack() }
            )


            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isScanning.value && isAdvertising.value) {
                        CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Поиск устройств...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Реклама включена",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    } else if (isAdvertising.value) {
                        Text(
                            text = "Реклама включена",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ожидание сканирования...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    } else {
                        Text(
                            text = "Инициализация...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }


            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (discoveredUsers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Пользователи не найдены\nУбедитесь что у них запущен Dolphy Chat",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(discoveredUsers, key = { it.id }) { user ->
                        ChatUserCard(
                            user = user,
                            accentColor = accentColor,
                            onClick = {
                                navController.navigate("other/dolphy_chat_global")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatUserCard(
    user: ChatUser,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Доступен для чата",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }


            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DolphyChatConversationScreen(
    userId: String,
    userName: String,
    navController: NavController
) {
    val context = LocalContext.current
    val chatManager = remember { DolphyChatManager(context) }
    val accentColor = MaterialTheme.colorScheme.primary

    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<String>() }

    MaterialBackground(accentColor = accentColor) {
        Column(modifier = Modifier.fillMaxSize()) {
            SectionTopBar(
                transparent = true,
                title = userName,
                onBack = { navController.popBackStack() }
            )


            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    ChatBubble(
                        message = message,
                        isFromMe = true,
                        accentColor = accentColor
                    )
                }
            }


            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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

                            messages.add(messageText)
                            messageText = ""
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
fun ChatBubble(
    message: String,
    isFromMe: Boolean,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    if (isFromMe) {
                        AbsoluteRoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                    } else {
                        AbsoluteRoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                    }
                )
                .background(
                    if (isFromMe) accentColor else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp, 8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFromMe) Color.White else MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

