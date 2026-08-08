package com.droid.dolphy

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object IrWidgetRuntime {
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var stormJob: Job? = null
    private var jammerJob: Job? = null

    private const val BASE_PATH =
        "flipperzero-firmware-dev/applications/main/infrared/resources/infrared/assets"

    private val assetMap = mapOf(
        UniversalCategory.TV to "$BASE_PATH/tv.ir",
        UniversalCategory.AUDIO to "$BASE_PATH/audio.ir",
        UniversalCategory.AC to "$BASE_PATH/ac.ir",
        UniversalCategory.PROJECTOR to "$BASE_PATH/projector.ir"
    )

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun isStormRunning(): Boolean = stormJob?.isActive == true

    fun isJammerRunning(): Boolean = jammerJob?.isActive == true

    fun toggleStorm() {
        val context = appContext ?: return
        if (isStormRunning()) {
            stormJob?.cancel()
            stormJob = null
            return
        }
        stormJob = scope.launch {
            try {
                val assetPaths = withContext(Dispatchers.IO) {
                    IrStormRepository.getTvAssetPaths(context)
                }
                for (assetPath in assetPaths) {
                    if (!isStormRunning()) break
                    val btn = withContext(Dispatchers.IO) {
                        val buttons = IrRepository.loadButtons(context, assetPath)
                        IrStormRepository.pickShutdownButton(buttons)
                    }
                    if (btn != null) {
                        withContext(Dispatchers.IO) {
                            transmitIr(context, btn)
                        }
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                Log.e("IrWidgetRuntime", "Storm error: ${e.message}")
            } finally {
                stormJob = null
            }
        }
    }

    fun toggleJammer() {
        val context = appContext ?: return
        if (isJammerRunning()) {
            jammerJob?.cancel()
            jammerJob = null
            return
        }
        jammerJob = scope.launch {
            try {
                val buttons = withContext(Dispatchers.IO) {
                    val flIndex = IrRepository.getFlipperIndex(context)
                    flIndex.entries
                        .filter { it.key.substringBefore('/').equals("ИК глушилка", ignoreCase = true) }
                        .flatMap { (_, models) ->
                            models.values.flatMap { assetPath -> IrRepository.loadButtons(context, assetPath) }
                        }
                }
                if (buttons.isEmpty()) return@launch

                while (isJammerRunning()) {
                    for (btn in buttons) {
                        if (!isJammerRunning()) break
                        withContext(Dispatchers.IO) {
                            transmitIr(context, btn)
                        }
                        delay(8)
                    }
                }
            } catch (e: Exception) {
                Log.e("IrWidgetRuntime", "Jammer error: ${e.message}")
            } finally {
                jammerJob = null
            }
        }
    }

    fun sendUniversal(category: UniversalCategory, action: UniversalAction) {
        val context = appContext ?: return
        scope.launch {
            val assetPath = assetMap[category] ?: return@launch
            val signals = withContext(Dispatchers.IO) {
                val all = IrRepository.loadButtons(context, assetPath)
                val aliases = universalAliases(action.signalName)
                val direct = all.filter { it.name.equals(action.signalName, ignoreCase = true) }
                if (direct.isNotEmpty()) {
                    direct
                } else {
                    all.filter { btn ->
                        val norm = normalizeSearch(btn.name ?: "")
                        aliases.any { alias -> norm == alias || norm.contains(alias) }
                    }
                }
            }
            signals.forEach { btn ->
                withContext(Dispatchers.IO) {
                    transmitIr(context, btn)
                }
                delay(10)
            }
        }
    }

    private fun normalizeSearch(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9а-яё]+"), " ")
            .trim()
    }

    private fun universalAliases(signalName: String): Set<String> {
        val base = normalizeSearch(signalName)
        return when (base) {
            "power", "on", "off" -> setOf("power", "on", "off", "standby", "pwr", "on off", "onoff")
            "mute" -> setOf("mute", "silence", "volmute")
            "vol up", "vol_up", "volume up", "v+" -> setOf("vol up", "vol_up", "volume up", "vol+", "v+", "volume+", "volup")
            "vol dn", "vol_dn", "volume dn", "v-" -> setOf("vol dn", "vol_dn", "volume dn", "vol-", "v-", "volume-", "voldn")
            "ch next", "ch_next", "channel next", "ch+", "next ch" -> setOf("ch next", "ch_next", "channel next", "ch+", "prog+", "p+")
            "ch prev", "ch_prev", "channel prev", "ch-", "prev ch" -> setOf("ch prev", "ch_prev", "channel prev", "ch-", "prog-", "p-")
            "play" -> setOf("play")
            "pause" -> setOf("pause")
            "next" -> setOf("next", "next track", "skip next")
            "prev" -> setOf("prev", "previous", "prev track", "skip prev")
            "cool hi", "cool_hi" -> setOf("cool hi", "cool_hi", "cool+", "cool up", "cool_up")
            "cool lo", "cool_lo" -> setOf("cool lo", "cool_lo", "cool-", "cool down", "cool_dn")
            "heat hi", "heat_hi" -> setOf("heat hi", "heat_hi", "heat+", "heat up", "heat_up")
            "heat lo", "heat_lo" -> setOf("heat lo", "heat_lo", "heat-", "heat down", "heat_dn")
            "dh" -> setOf("dh", "dry", "drying")
            else -> setOf(base)
        }
    }
}

enum class UniversalCategory {
    TV,
    AUDIO,
    AC,
    PROJECTOR
}

data class UniversalAction(
    val signalName: String,
    val displayName: String
)

