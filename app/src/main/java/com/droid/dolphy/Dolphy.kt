package com.droid.dolphy

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val PREFS = "DolphyPassportPrefs"

private const val KEY_ICOUNTER = "icounter"
private const val KEY_BUTTHURT = "butthurt"
private const val KEY_TIMESTAMP = "timestamp"
private const val KEY_LAST_DAY = "last_day"
private const val KEY_DAILY_NFC = "daily_nfc"
private const val KEY_DAILY_IR = "daily_ir"
private const val KEY_DAILY_BADUSB = "daily_badusb"
private const val KEY_DAILY_EXTRA = "daily_extra"
private const val KEY_DAILY_BUTTHURT = "daily_butthurt"
private const val KEY_NFC_READ_COUNT = "nfc_read_count"
private const val KEY_IR_SEND_COUNT = "ir_send_count"
private const val KEY_BAD_HID_COUNT = "bad_hid_count"
private const val KEY_NFC_EMULATE_COUNT = "nfc_emulate_count"
private const val KEY_DOLPHIN_NAME = "dolphin_name"
private const val KEY_BLE_PACKETS_SENT = "ble_packets_sent"
private const val KEY_NRF_DEVICES_FOUND = "nrf_devices_found"
private const val KEY_WIFI_BRUTE_SUCCESS = "wifi_brute_success"

private const val LEVEL_THRESHOLD = 200
private const val BUTTHURT_MAX = 14
private const val DAILY_APP_LIMIT = 20

enum class DolphinApp {
    Nfc,
    Ir,
    BadUsb,
    Extra,
    Ble,
    Network,
}

enum class DolphinDeed(val app: DolphinApp, val weight: Int) {
    NfcRead(DolphinApp.Nfc, 1),
    NfcReadSuccess(DolphinApp.Nfc, 3),
    NfcEmulate(DolphinApp.Nfc, 2),
    IrSend(DolphinApp.Ir, 1),
    BadUsbPlayScript(DolphinApp.BadUsb, 2),
    ExtraStart(DolphinApp.Extra, 2),
    BlePacketSent(DolphinApp.Ble, 0),
    NrfDeviceFound(DolphinApp.Ble, 0),
    WifiBruteSuccess(DolphinApp.Network, 5),
}

data class DolphyState(
    val icounter: Int = 0,
    val butthurt: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val nfcReadCount: Int = 0,
    val irSendCount: Int = 0,
    val badHidRunCount: Int = 0,
    val nfcEmulateCount: Int = 0,
    val blePacketsSent: Int = 0,
    val nrfDevicesFound: Int = 0,
    val wifiBruteSuccessCount: Int = 0,
    val dolphinName: String = generateDolphinName(),
) {
    val level: Int
        get() = (icounter / LEVEL_THRESHOLD) + 1

    val levelUpPending: Boolean
        get() = icounter > 0 && icounter % LEVEL_THRESHOLD == 0

    val moodLabel: String
        get() = when {
            butthurt <= 4 -> "Happy"
            butthurt <= 9 -> "Ok"
            else -> "Angry"
        }

    val xpToLevelUp: Int
        get() = LEVEL_THRESHOLD - (icounter % LEVEL_THRESHOLD)

    val xpAboveLastLevelUp: Int
        get() = icounter % LEVEL_THRESHOLD

    val levelProgress: Float
        get() {
            return (icounter % LEVEL_THRESHOLD).toFloat() / LEVEL_THRESHOLD.toFloat()
        }
}

object DolphyRepository {
    private val lock = Any()
    private var initialized = false
    private lateinit var appContext: Context
    private val _state = MutableStateFlow(DolphyState())
    val state = _state.asStateFlow()

    fun init(context: Context) {
        synchronized(lock) {
            if(initialized) return
            appContext = context.applicationContext
            initialized = true
            _state.value = refreshAndLoad(appContext)
        }
    }

    fun recordDeed(context: Context, deed: DolphinDeed) {
        synchronized(lock) {
            init(context)
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = refreshAndLoad(appContext)
            val dailyKey = when(deed.app) {
                DolphinApp.Nfc -> KEY_DAILY_NFC
                DolphinApp.Ir -> KEY_DAILY_IR
                DolphinApp.BadUsb -> KEY_DAILY_BADUSB
                DolphinApp.Extra -> KEY_DAILY_EXTRA
                DolphinApp.Ble -> KEY_DAILY_EXTRA
                DolphinApp.Network -> KEY_DAILY_EXTRA
            }
            val currentDaily = prefs.getInt(dailyKey, 0)
            val weightLimit = (DAILY_APP_LIMIT - currentDaily).coerceAtLeast(0)
            var deedWeight = deed.weight.coerceAtMost(weightLimit).coerceAtLeast(0)

            val xpToLevelUp = LEVEL_THRESHOLD - (current.icounter % LEVEL_THRESHOLD)
            deedWeight = deedWeight.coerceAtMost(xpToLevelUp)

            val nextIcounter = current.icounter + deedWeight
            val nextDaily = currentDaily + deedWeight

            val butthurtDailyOld = prefs.getInt(KEY_DAILY_BUTTHURT, 0)
            val oldLevel = butthurtBucketLevel(butthurtDailyOld)
            val newButthurtDaily = (butthurtDailyOld + deedWeight).coerceIn(0, 46)
            val newLevel = butthurtBucketLevel(newButthurtDaily)
            val butthurtDrop = if(oldLevel != newLevel) 1 else 0
            val nextButthurt = (current.butthurt - butthurtDrop).coerceIn(0, BUTTHURT_MAX)

            val nextNfcReads = current.nfcReadCount + if(deed == DolphinDeed.NfcReadSuccess || deed == DolphinDeed.NfcRead) 1 else 0
            val nextIrSends = current.irSendCount + if(deed == DolphinDeed.IrSend) 1 else 0
            val nextBadHidRuns = current.badHidRunCount + if(deed == DolphinDeed.BadUsbPlayScript) 1 else 0
            val nextNfcEmulate = current.nfcEmulateCount + if(deed == DolphinDeed.NfcEmulate) 1 else 0
            val nextWifiBrute = current.wifiBruteSuccessCount + if(deed == DolphinDeed.WifiBruteSuccess) 1 else 0

            val ts = System.currentTimeMillis()
            prefs.edit()
                .putInt(KEY_ICOUNTER, nextIcounter)
                .putInt(KEY_BUTTHURT, nextButthurt)
                .putLong(KEY_TIMESTAMP, ts)
                .putInt(dailyKey, nextDaily)
                .putInt(KEY_DAILY_BUTTHURT, newButthurtDaily)
                .putInt(KEY_NFC_READ_COUNT, nextNfcReads)
                .putInt(KEY_IR_SEND_COUNT, nextIrSends)
                .putInt(KEY_BAD_HID_COUNT, nextBadHidRuns)
                .putInt(KEY_NFC_EMULATE_COUNT, nextNfcEmulate)
                .putInt(KEY_WIFI_BRUTE_SUCCESS, nextWifiBrute)
                .apply()

            _state.value = DolphyState(
                icounter = nextIcounter,
                butthurt = nextButthurt,
                timestamp = ts,
                nfcReadCount = nextNfcReads,
                irSendCount = nextIrSends,
                badHidRunCount = nextBadHidRuns,
                nfcEmulateCount = nextNfcEmulate,
                blePacketsSent = current.blePacketsSent,
                nrfDevicesFound = current.nrfDevicesFound,
                wifiBruteSuccessCount = nextWifiBrute,
                dolphinName = current.dolphinName,
            )
        }
    }

    fun trackBlePackets(context: Context, count: Int) {
        synchronized(lock) {
            init(context)
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = refreshAndLoad(appContext)

            val newTotal = current.blePacketsSent + count
            val xpToAdd = (newTotal / 100) - (current.blePacketsSent / 100)

            val xpToLevelUp = LEVEL_THRESHOLD - (current.icounter % LEVEL_THRESHOLD)
            val safeXp = xpToAdd.coerceAtMost(xpToLevelUp).coerceAtLeast(0)

            val nextIcounter = current.icounter + safeXp

            prefs.edit()
                .putInt(KEY_BLE_PACKETS_SENT, newTotal)
                .putInt(KEY_ICOUNTER, nextIcounter)
                .apply()

            _state.value = current.copy(
                blePacketsSent = newTotal,
                icounter = nextIcounter
            )
        }
    }

    fun trackNrfDevices(context: Context, count: Int) {
        synchronized(lock) {
            init(context)
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = refreshAndLoad(appContext)

            val newTotal = current.nrfDevicesFound + count
            val xpToAdd = (newTotal / 5) - (current.nrfDevicesFound / 5)

            val xpToLevelUp = LEVEL_THRESHOLD - (current.icounter % LEVEL_THRESHOLD)
            val safeXp = xpToAdd.coerceAtMost(xpToLevelUp).coerceAtLeast(0)

            val nextIcounter = current.icounter + safeXp

            prefs.edit()
                .putInt(KEY_NRF_DEVICES_FOUND, newTotal)
                .putInt(KEY_ICOUNTER, nextIcounter)
                .apply()

            _state.value = current.copy(
                nrfDevicesFound = newTotal,
                icounter = nextIcounter
            )
        }
    }

    fun refresh(context: Context) {
        synchronized(lock) {
            init(context)
            _state.value = refreshAndLoad(appContext)
        }
    }

    private fun refreshAndLoad(context: Context): DolphyState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ensureDolphinName(prefs)
        val today = LocalDate.now()
        val lastDayRaw = prefs.getString(KEY_LAST_DAY, null)
        val lastDay = runCatching { lastDayRaw?.let(LocalDate::parse) }.getOrNull() ?: today
        val daysBetween = ChronoUnit.DAYS.between(lastDay, today)

        if(daysBetween > 0) {
            val currentButthurt = prefs.getInt(KEY_BUTTHURT, 0)
            val bumpedButthurt = (currentButthurt + daysBetween.toInt()).coerceIn(0, BUTTHURT_MAX)
            prefs.edit()
                .putString(KEY_LAST_DAY, today.toString())
                .putInt(KEY_BUTTHURT, bumpedButthurt)
                .putInt(KEY_DAILY_NFC, 0)
                .putInt(KEY_DAILY_IR, 0)
                .putInt(KEY_DAILY_BADUSB, 0)
                .putInt(KEY_DAILY_EXTRA, 0)
                .putInt(KEY_DAILY_BUTTHURT, 0)
                .apply()
        } else if(lastDayRaw == null) {
            prefs.edit().putString(KEY_LAST_DAY, today.toString()).apply()
        }

        return DolphyState(
            icounter = prefs.getInt(KEY_ICOUNTER, 0),
            butthurt = prefs.getInt(KEY_BUTTHURT, 0),
            timestamp = prefs.getLong(KEY_TIMESTAMP, System.currentTimeMillis()),
            nfcReadCount = prefs.getInt(KEY_NFC_READ_COUNT, 0),
            irSendCount = prefs.getInt(KEY_IR_SEND_COUNT, 0),
            badHidRunCount = prefs.getInt(KEY_BAD_HID_COUNT, 0),
            nfcEmulateCount = prefs.getInt(KEY_NFC_EMULATE_COUNT, 0),
            blePacketsSent = prefs.getInt(KEY_BLE_PACKETS_SENT, 0),
            nrfDevicesFound = prefs.getInt(KEY_NRF_DEVICES_FOUND, 0),
            wifiBruteSuccessCount = prefs.getInt(KEY_WIFI_BRUTE_SUCCESS, 0),
            dolphinName = prefs.getString(KEY_DOLPHIN_NAME, null) ?: generateDolphinName(),
        )
    }

    fun initializeNewUser(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        if (prefs.getString(KEY_LAST_DAY, null) == null) {
            prefs.edit()
                .putString(KEY_LAST_DAY, today.toString())
                .putInt(KEY_BUTTHURT, 0)
                .putInt(KEY_ICOUNTER, 0)
                .putString(KEY_DOLPHIN_NAME, generateDolphinName())
                .apply()
        } else {
            ensureDolphinName(prefs)
        }
    }

    fun setButthurt(context: Context, value: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_BUTTHURT, value.coerceIn(0, BUTTHURT_MAX)).apply()
        refresh(context)
    }

    fun setDolphinName(context: Context, value: String) {
        synchronized(lock) {
            init(context)
            val normalized = value.trim().take(10)
            if (normalized.isBlank()) return
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_DOLPHIN_NAME, normalized).apply()
            _state.value = refreshAndLoad(appContext)
        }
    }

    fun acknowledgeLevelUp(context: Context) {
        synchronized(lock) {
            init(context)
            val current = refreshAndLoad(appContext)
            if (current.levelUpPending) {
                val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val nextIcounter = current.icounter + 1
                prefs.edit().putInt(KEY_ICOUNTER, nextIcounter).apply()
                _state.value = refreshAndLoad(appContext)
            }
        }
    }

    fun trackWifiBruteSuccess(context: Context) {
        recordDeed(context, DolphinDeed.WifiBruteSuccess)
    }

    fun trackBlePackets(count: Int) {
        if (initialized) trackBlePackets(appContext, count)
    }

    fun trackNrfDevices(count: Int) {
        if (initialized) trackNrfDevices(appContext, count)
    }

    fun trackWifiBruteSuccess() {
        if (initialized) recordDeed(appContext, DolphinDeed.WifiBruteSuccess)
    }

    private fun butthurtBucketLevel(value: Int): Int {
        return (value / 15) + if(value % 15 != 0) 1 else 0
    }

    private fun ensureDolphinName(prefs: android.content.SharedPreferences) {
        if (prefs.getString(KEY_DOLPHIN_NAME, null) == null) {
            prefs.edit().putString(KEY_DOLPHIN_NAME, generateDolphinName()).apply()
        }
    }
}

private val dolphinNameLeft = listOf(
    "ancient", "hollow", "strange", "disappeared", "unknown", "unthinkable", "unnameable",
    "nameless", "my", "concealed", "forgotten", "hidden", "mysterious", "obscure",
    "random", "remote", "uncharted", "undefined", "untraveled", "untold"
)

private val dolphinNameRight = listOf(
    "door",
    "entrance",
    "doorway",
    "entry",
    "portal",
    "entree",
    "opening",
    "crack",
    "access",
    "corridor",
    "passage",
    "port",
)

private fun generateDolphinName(): String {
    val left = dolphinNameLeft.random()
    val right = dolphinNameRight.random()
    return "${left}_${right}".replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase() else char.toString()
    }
}

fun trackNfcRead(context: Context) = DolphyRepository.recordDeed(context, DolphinDeed.NfcReadSuccess)
fun trackNfcEmulate(context: Context) = DolphyRepository.recordDeed(context, DolphinDeed.NfcEmulate)
fun trackIrSend(context: Context) = DolphyRepository.recordDeed(context, DolphinDeed.IrSend)
fun trackBadHidRun(context: Context) = DolphyRepository.recordDeed(context, DolphinDeed.BadUsbPlayScript)
fun trackBlePackets(context: Context, count: Int) = DolphyRepository.trackBlePackets(context, count)
fun trackNrfDevices(context: Context, count: Int) = DolphyRepository.trackNrfDevices(context, count)
fun trackWifiBruteSuccess(context: Context) = DolphyRepository.recordDeed(context, DolphinDeed.WifiBruteSuccess)

fun trackBlePackets(count: Int) = DolphyRepository.trackBlePackets(count)
fun trackNrfDevices(count: Int) = DolphyRepository.trackNrfDevices(count)
fun trackWifiBruteSuccess() = DolphyRepository.trackWifiBruteSuccess()

class DolphyViewModel(application: Application) : AndroidViewModel(application) {
    val dolphyState: StateFlow<DolphyState>
        get() = DolphyRepository.state

    fun acknowledgeLevelUp() {
        DolphyRepository.acknowledgeLevelUp(getApplication())
    }

    init {
        DolphyRepository.init(application)
        DolphyRepository.refresh(application)
    }
}

class DolphyViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DolphyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DolphyViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

