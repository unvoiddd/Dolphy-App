package com.droid.dolphy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object BleSpamRuntime {
    private const val PREFS = "DolphyPrefs"
    private const val NOTIFICATION_CHANNEL_ID = "ble_spam_channel"
    private const val NOTIFICATION_ID = 1001
    private const val ACTION_STOP_ALL = "com.droid.dolphy.action.BLE_STOP_ALL"

    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val spammers = mutableMapOf<Pair<SpamType, Any?>, Spammer>()
    private val _spammingStates = MutableStateFlow<Map<Pair<SpamType, Any?>, Boolean>>(emptyMap())
    val spammingStates: StateFlow<Map<Pair<SpamType, Any?>, Boolean>> = _spammingStates.asStateFlow()

    private val _bleDelay = MutableStateFlow(20)
    val bleDelay: StateFlow<Int> = _bleDelay.asStateFlow()

    private val _kitchenSinkActive = MutableStateFlow(false)
    val kitchenSinkActive: StateFlow<Boolean> = _kitchenSinkActive.asStateFlow()
    private var kitchenSinkJob: Job? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        updateNotification()
    }

    fun setBleDelay(delay: Int) {
        val clamped = delay.coerceIn(10, 1000)
        _bleDelay.value = clamped
        Helper.delay = clamped
    }

    fun trackSentPackets(count: Int = 1) {
        val context = appContext
        if (context != null) {
            trackBlePackets(context, count)
        } else {
            trackBlePackets(count)
        }
    }

    fun toggleBleSpam(type: SpamType, subtype: Any?) {
        val key = Pair(type, subtype)
        val isCurrentlySpamming = _spammingStates.value[key] == true
        if (isCurrentlySpamming) {
            spammers[key]?.stop()
            spammers.remove(key)
            _spammingStates.value = _spammingStates.value.toMutableMap().apply { this[key] = false }
        } else {
            val spammer = createSpammer(type, subtype) ?: return
            spammers[key] = spammer
            spammer.start()
            _spammingStates.value = _spammingStates.value.toMutableMap().apply { this[key] = true }
        }
        updateNotification()
        updateWidgets()
    }

    fun toggleKitchenSink() {
        if (kitchenSinkJob != null) {
            stopKitchenSink()
        } else {
            startKitchenSink()
        }
        updateNotification()
        updateWidgets()
    }

    fun stopAllBleSpam() {
        spammers.values.forEach { it.stop() }
        spammers.clear()
        _spammingStates.value = emptyMap()
        stopKitchenSink()
        updateNotification()
        updateWidgets()
    }

    fun toggleSection(section: BleSection) {
        val keys = sectionKeys(section)
        val anyActive = keys.any { _spammingStates.value[it] == true }
        if (anyActive) {
            keys.forEach { stopKey(it) }
        } else {
            keys.forEach { startKey(it) }
        }
        updateNotification()
        updateWidgets()
    }

    fun isSectionActive(section: BleSection): Boolean {
        return sectionKeys(section).any { _spammingStates.value[it] == true }
    }

    private fun startKey(key: Pair<SpamType, Any?>) {
        if (_spammingStates.value[key] == true) return
        val spammer = createSpammer(key.first, key.second) ?: return
        spammers[key] = spammer
        spammer.start()
        _spammingStates.value = _spammingStates.value.toMutableMap().apply { this[key] = true }
    }

    private fun stopKey(key: Pair<SpamType, Any?>) {
        spammers[key]?.stop()
        spammers.remove(key)
        _spammingStates.value = _spammingStates.value.toMutableMap().apply { this[key] = false }
    }

    private fun createSpammer(type: SpamType, subtype: Any?): Spammer? {
        return when (type) {
            SpamType.CONTINUITY -> {
                val mode = subtype as? ContinuityMode ?: ContinuityMode(ContinuityType.DEVICE, false)
                ContinuitySpam(mode.type, mode.crashMode)
            }
            SpamType.EASY_SETUP -> EasySetupSpam(subtype as? EasySetupDevice.Type ?: EasySetupDevice.Type.BUDS)
            SpamType.FAST_PAIR -> FastPairSpam()
            SpamType.SWIFT_PAIR -> SwiftPairSpam()
            SpamType.XIAOMI -> XiaomiQuickConnect()
            SpamType.PHANTOM -> BluetoothPhantomSpammer()
        }
    }

    private fun sectionKeys(section: BleSection): List<Pair<SpamType, Any?>> {
        return when (section) {
            BleSection.IOS -> listOf(
                Pair(SpamType.CONTINUITY, ContinuityMode(ContinuityType.DEVICE, false)),
                Pair(SpamType.CONTINUITY, ContinuityMode(ContinuityType.NOTYOURDEVICE, false)),
                Pair(SpamType.CONTINUITY, ContinuityMode(ContinuityType.ACTION, false)),
                Pair(SpamType.CONTINUITY, ContinuityMode(ContinuityType.ACTION, true))
            )
            BleSection.SAMSUNG -> listOf(
                Pair(SpamType.EASY_SETUP, EasySetupDevice.Type.WATCH),
                Pair(SpamType.EASY_SETUP, EasySetupDevice.Type.BUDS)
            )
            BleSection.ANDROID -> listOf(Pair(SpamType.FAST_PAIR, null))
            BleSection.WINDOWS -> listOf(Pair(SpamType.SWIFT_PAIR, null))
            BleSection.XIAOMI -> listOf(Pair(SpamType.XIAOMI, null))
            BleSection.PHANTOM -> listOf(Pair(SpamType.PHANTOM, null))
        }
    }

    private fun startKitchenSink() {
        if (kitchenSinkJob != null) return
        val items = buildAllBleItems(appContext ?: return)
        if (items.isEmpty()) return
        _kitchenSinkActive.value = true
        kitchenSinkJob = scope.launch {
            while (true) {
                val item = items.random()
                val spammer = item.spammerFactory()
                spammer.start()
                delay(_bleDelay.value.toLong().coerceAtLeast(20))
                spammer.stop()
            }
        }
    }

    private fun stopKitchenSink() {
        kitchenSinkJob?.cancel()
        kitchenSinkJob = null
        _kitchenSinkActive.value = false
    }

    private fun updateNotification() {
        val context = appContext ?: return
        val activeSections = mutableListOf<String>()
        if (isSectionActive(BleSection.IOS)) activeSections += "iOS"
        if (isSectionActive(BleSection.SAMSUNG)) activeSections += "Samsung"
        if (isSectionActive(BleSection.ANDROID)) activeSections += "Android"
        if (isSectionActive(BleSection.WINDOWS)) activeSections += "Windows"
        if (isSectionActive(BleSection.XIAOMI)) activeSections += "Xiaomi"
        if (isSectionActive(BleSection.PHANTOM)) activeSections += "Phantom"
        if (_kitchenSinkActive.value) activeSections += "Kitchen sink"

        if (activeSections.isEmpty()) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "BLE Spam",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(context, BleSpamWidgetProvider::class.java).apply {
            action = ACTION_STOP_ALL
        }
        val stopPending = PendingIntent.getBroadcast(
            context,
            100,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Spam: ${activeSections.joinToString(", ")}"
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("BLE Spam")
            .setContentText(text)
            .setOngoing(true)
            .addAction(0, "Остановить", stopPending)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun updateWidgets() {
        val context = appContext ?: return
        BleSpamWidgetProvider.updateAll(context)
    }

    fun handleStopAllAction() {
        stopAllBleSpam()
    }
}

