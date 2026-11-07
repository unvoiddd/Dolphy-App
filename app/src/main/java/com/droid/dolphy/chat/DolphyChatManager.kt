package com.droid.dolphy.chat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.droid.dolphy.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val CHAT_SERVICE = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
private val CHAT_CHARACTERISTIC = UUID.fromString("00001102-0000-1000-8000-00805f9b34fb")
private val CHAT_UUID = ParcelUuid(CHAT_SERVICE)

enum class MessageDelivery { RECEIVED, PENDING, SENT, FAILED }

@Serializable
data class GlobalMessage(
    val id: String = UUID.randomUUID().toString(),
    val userName: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val senderId: String = "",
    val delivery: MessageDelivery = MessageDelivery.RECEIVED,
)

data class ChatUser(val id: String, val name: String, val address: String, val lastSeen: Long = System.currentTimeMillis())

object GlobalChatData {
    val globalMessages = mutableStateListOf<GlobalMessage>()
    fun put(message: GlobalMessage) {
        val index = globalMessages.indexOfFirst { it.id == message.id }
        if (index >= 0) globalMessages[index] = message else globalMessages.add(message)
        while (globalMessages.size > 500) globalMessages.removeAt(0)
    }
}

@SuppressLint("MissingPermission")
class DolphyChatManager(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("dolphy_chat_prefs", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val bluetooth = app.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetooth?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var server: BluetoothGattServer? = null
    private var scanning = false
    private var closed = false
    private val receivers = mutableMapOf<String, ChatWire.Receiver>()
    private val outgoing = mutableSetOf<Outgoing>()
    val discoveredUsers = mutableStateListOf<ChatUser>()
    val currentUser = mutableStateOf<String?>(null)
    val isScanning = mutableStateOf(false)
    val isAdvertising = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val senderId: String = prefs.getString("sender_id", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("sender_id", it).apply()
    }

    private val prune = object : Runnable {
        override fun run() {
            if (closed) return
            val cutoff = System.currentTimeMillis() - 30_000
            discoveredUsers.removeAll { it.lastSeen < cutoff }
            main.postDelayed(this, 10_000)
        }
    }
    private fun fail(resource: Int) { main.post { if (!closed) error.value = app.getString(resource) } }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val text = result.scanRecord?.getServiceData(CHAT_UUID)?.toString(Charsets.UTF_8) ?: return
            if (!text.startsWith("DOLPHY:")) return
            val address = result.device.address
            main.post {
                if (closed || !scanning) return@post
                val user = ChatUser(address, text.removePrefix("DOLPHY:"), address)
                val index = discoveredUsers.indexOfFirst { it.id == address }
                if (index >= 0) discoveredUsers[index] = user else discoveredUsers.add(user)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            main.post { isScanning.value = false; scanning = false }
            fail(R.string.bluetooth_scan_failed)
        }
    }
    private val advertisingCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            main.post { if (!closed) isAdvertising.value = true }
        }
        override fun onStartFailure(errorCode: Int) { fail(R.string.chat_advertising_failed) }
    }
    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            main.post {
                if (closed) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) advertise() else fail(R.string.chat_advertising_failed)
            }
        }
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) synchronized(receivers) { receivers.remove(device.address) }
        }
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
        ) {
            var status = BluetoothGatt.GATT_SUCCESS
            if (preparedWrite || offset != 0 || characteristic.uuid != CHAT_CHARACTERISTIC) {
                status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
            } else {
                try {
                    val text = synchronized(receivers) {
                        receivers.getOrPut(device.address) { ChatWire.Receiver() }.accept(value)
                    }
                    if (text != null) {
                        val message = Json.decodeFromString<GlobalMessage>(text)
                        require(message.message.isNotBlank() && message.userName.length <= 64)
                        main.post {
                            if (!closed && GlobalChatData.globalMessages.none { it.id == message.id }) {
                                GlobalChatData.put(message.copy(delivery = MessageDelivery.RECEIVED))
                            }
                        }
                    }
                } catch (_: Exception) {
                    synchronized(receivers) { receivers.remove(device.address) }
                    status = BluetoothGatt.GATT_FAILURE
                }
            }
            if (responseNeeded) runCatching { server?.sendResponse(device, requestId, status, offset, null) }
        }
    }
    fun startWithSavedName(): Boolean {
        val name = prefs.getString("user_name", null) ?: return false
        start(name)
        return true
    }
    fun start(name: String) {
        if (closed || server != null) return
        if (!hasPermissions()) { fail(R.string.bluetooth_permission_required); return }
        if (adapter?.isEnabled != true) { fail(R.string.bluetooth_enable_first); return }
        try {
            currentUser.value = name.trim().take(64).ifBlank { "Android" }
            prefs.edit().putString("user_name", currentUser.value).apply()
            advertiser = adapter?.bluetoothLeAdvertiser ?: kotlin.error("Advertising unavailable")
            server = bluetooth?.openGattServer(app, serverCallback) ?: kotlin.error("GATT server unavailable")
            val service = BluetoothGattService(CHAT_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(BluetoothGattCharacteristic(CHAT_CHARACTERISTIC,
                BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE))
            check(server?.addService(service) == true)
            scanner = adapter?.bluetoothLeScanner ?: kotlin.error("Scanner unavailable")
            scanner?.startScan(listOf(ScanFilter.Builder().setServiceUuid(CHAT_UUID).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
            scanning = true
            isScanning.value = true
            error.value = null
            main.post(prune)
        } catch (_: Exception) {
            releaseRadio()
            fail(R.string.chat_start_failed)
        }
    }
    private fun advertise() {
        var shortName = ""
        for (codePoint in currentUser.value.orEmpty().codePoints().toArray()) {
            val next = shortName + String(Character.toChars(codePoint))
            if (next.toByteArray(Charsets.UTF_8).size > 12) break
            shortName = next
        }
        val data = AdvertiseData.Builder().addServiceUuid(CHAT_UUID)
            .addServiceData(CHAT_UUID, "DOLPHY:$shortName".toByteArray(Charsets.UTF_8)).build()
        val settings = AdvertiseSettings.Builder().setConnectable(true)
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).build()
        runCatching { advertiser?.startAdvertising(settings, data, advertisingCallback) }
            .onFailure { fail(R.string.chat_advertising_failed) }
    }
    fun sendMessage(text: String, messageId: String = UUID.randomUUID().toString()): Boolean {
        if (closed || currentUser.value == null || !hasPermissions()) {
            error.value = app.getString(R.string.chat_send_not_ready)
            return false
        }
        val recipients = discoveredUsers.toList()
        if (recipients.isEmpty()) { error.value = app.getString(R.string.chat_no_recipients); return false }
        val message = GlobalMessage(id = messageId, userName = currentUser.value!!, message = text,
            senderId = senderId, delivery = MessageDelivery.PENDING)
        val wire = runCatching { ChatWire.encode(Json.encodeToString(message.copy(delivery = MessageDelivery.RECEIVED))) }
            .getOrElse { error.value = app.getString(R.string.chat_message_too_long); return false }
        GlobalChatData.put(message)
        error.value = null
        var remaining = recipients.size
        var failures = 0
        recipients.forEach { user ->
            val transfer = Outgoing(wire) { success ->
                if (!success) failures++
                remaining--
                if (remaining == 0) GlobalChatData.put(message.copy(
                    delivery = if (failures == 0) MessageDelivery.SENT else MessageDelivery.FAILED))
            }
            outgoing.add(transfer)
            transfer.connect(user.address)
        }
        return true
    }
    private inner class Outgoing(private val bytes: ByteArray, private val completed: (Boolean) -> Unit) {
        private var gatt: BluetoothGatt? = null
        private var characteristic: BluetoothGattCharacteristic? = null
        private var offset = 0
        private var done = false
        private val timeout = Runnable { finish(false) }
        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                main.post {
                    if (done) return@post
                    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                        if (!gatt.discoverServices()) finish(false)
                    } else finish(false)
                }
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                main.post {
                    if (done) return@post
                    characteristic = gatt.getService(CHAT_SERVICE)?.getCharacteristic(CHAT_CHARACTERISTIC)
                    if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null) finish(false) else writeNext()
                }
            }
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                main.post {
                    if (done) return@post
                    if (status != BluetoothGatt.GATT_SUCCESS) finish(false)
                    else if (offset >= bytes.size) finish(true) else writeNext()
                }
            }
        }
        fun connect(address: String) {
            main.postDelayed(timeout, 30_000)
            runCatching { gatt = adapter?.getRemoteDevice(address)?.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE) }
                .onFailure { finish(false) }
            if (gatt == null) finish(false)
        }
        private fun writeNext() {
            val target = characteristic ?: return finish(false)
            val client = gatt ?: return finish(false)
            val end = minOf(offset + 20, bytes.size)
            target.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            target.value = bytes.copyOfRange(offset, end)
            offset = end
            runCatching { client.writeCharacteristic(target) }.onSuccess { if (!it) finish(false) }.onFailure { finish(false) }
        }
        fun finish(success: Boolean) {
            if (done) return
            done = true
            main.removeCallbacks(timeout)
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
            outgoing.remove(this)
            completed(success)
        }
    }
    private fun hasPermissions(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return required.all { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }
    }
    private fun releaseRadio() {
        scanning = false
        runCatching { scanner?.stopScan(scanCallback) }
        runCatching { advertiser?.stopAdvertising(advertisingCallback) }
        runCatching { server?.close() }
        server = null
        isScanning.value = false
        isAdvertising.value = false
        synchronized(receivers) { receivers.clear() }
        main.removeCallbacks(prune)
    }
    override fun close() {
        closed = true
        outgoing.toList().forEach { it.finish(false) }
        releaseRadio()
    }
}
