@file:OptIn(ExperimentalMaterial3Api::class)
package com.droid.dolphy

import androidx.compose.material3.ExperimentalMaterial3Api

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.OpenableColumns
import android.util.Log
import android.hardware.ConsumerIrManager
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List as ListIcon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector

import com.droid.dolphy.M3SegmentedListItem
import com.droid.dolphy.M3SegmentedListItemSpacing
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.m3SegmentedItems
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TintedFrameAnimation(
                frames = listOf(
                    R.drawable.common_loading_24_frame_01,
                    R.drawable.common_loading_24_frame_02,
                    R.drawable.common_loading_24_frame_03,
                    R.drawable.common_loading_24_frame_04,
                    R.drawable.common_loading_24_frame_05,
                    R.drawable.common_loading_24_frame_06,
                    R.drawable.common_loading_24_frame_07
                ),
                modifier = Modifier.size(42.dp),
                frameDelayMs = 200L,
                tintColor = MaterialTheme.colorScheme.primary,
                contentDescription = "IR loading animation"
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.ir_loading_remotes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}




data class IrButton(
    val name: String?,
    val frequency: Int,
    val pattern: IntArray,
    val protocol: String? = null,
    val irCode: String? = null,
    val timings: String? = null
)

data class UserIrRemote(
    val id: Int,
    val fileName: String,
    val commands: List<IrButton>
)

data class IrFavoriteRemote(
    val key: String,
    val title: String,
    val subtitle: String,
    val route: String
)

object IrFavoriteStore {
    private const val PREFS_NAME = "DolphyPrefs"
    private const val KEY_FAVORITES_JSON = "ir_favorite_remotes_json"
    private val favoriteList = mutableStateListOf<IrFavoriteRemote>()
    private var loaded = false

    fun favorites(): List<IrFavoriteRemote> = favoriteList

    fun ensureLoaded(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = runCatching {
            JSONArray(prefs.getString(KEY_FAVORITES_JSON, "[]") ?: "[]")
        }.getOrElse { JSONArray() }
        val restored = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val favorite = IrFavoriteRemote(
                    key = item.optString("key"),
                    title = item.optString("title"),
                    subtitle = item.optString("subtitle"),
                    route = item.optString("route")
                )
                if (favorite.key.isNotBlank() && favorite.title.isNotBlank() && favorite.route.isNotBlank()) {
                    add(favorite)
                }
            }
        }
        favoriteList.clear()
        favoriteList.addAll(restored)
        loaded = true
    }

    fun contains(context: Context, key: String): Boolean {
        ensureLoaded(context)
        return favoriteList.any { it.key == key }
    }

    fun toggle(context: Context, favorite: IrFavoriteRemote) {
        ensureLoaded(context)
        val index = favoriteList.indexOfFirst { it.key == favorite.key }
        if (index >= 0) favoriteList.removeAt(index) else favoriteList.add(0, favorite)
        persist(context)
    }

    fun remove(context: Context, keys: Set<String>) {
        ensureLoaded(context)
        if (favoriteList.removeAll { it.key in keys }) persist(context)
    }

    fun updateTitle(context: Context, key: String, title: String) {
        ensureLoaded(context)
        val index = favoriteList.indexOfFirst { it.key == key }
        if (index < 0) return
        favoriteList[index] = favoriteList[index].copy(title = title)
        persist(context)
    }

    private fun persist(context: Context) {
        val array = JSONArray()
        favoriteList.forEach { favorite ->
            array.put(JSONObject().apply {
                put("key", favorite.key)
                put("title", favorite.title)
                put("subtitle", favorite.subtitle)
                put("route", favorite.route)
            })
        }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FAVORITES_JSON, array.toString())
            .apply()
    }
}

@Composable
private fun FavoriteRemoteButton(favorite: IrFavoriteRemote) {
    val context = LocalContext.current
    var selected by remember(favorite.key) { mutableStateOf(false) }

    LaunchedEffect(favorite.key) {
        selected = IrFavoriteStore.contains(context, favorite.key)
    }

    DolphyIconButton(
        onClick = {
            IrFavoriteStore.toggle(context, favorite)
            selected = IrFavoriteStore.contains(context, favorite.key)
        },
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = stringResource(if (selected) R.string.ir_remove_from_favorites else R.string.ir_add_to_favorites),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

object UserIrRemoteStore {
    private const val PREFS_NAME = "DolphyPrefs"
    private const val KEY_USER_REMOTES_JSON = "user_ir_remotes_json"

    private val remoteList = mutableStateListOf<UserIrRemote>()
    private var nextId = 1
    private var loaded = false

    fun remotes(): List<UserIrRemote> = remoteList

    fun ensureLoaded(context: Context) {
        if (loaded) return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_USER_REMOTES_JSON, "[]") ?: "[]"
        val array = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }

        val restored = mutableListOf<UserIrRemote>()
        for (i in 0 until array.length()) {
            val remoteObj = array.optJSONObject(i) ?: continue
            val id = remoteObj.optInt("id", -1)
            val fileName = remoteObj.optString("fileName")
            val commandsArray = remoteObj.optJSONArray("commands") ?: JSONArray()
            if (id < 0 || fileName.isBlank()) continue

            val commands = mutableListOf<IrButton>()
            for (j in 0 until commandsArray.length()) {
                val cmdObj = commandsArray.optJSONObject(j) ?: continue
                val frequency = cmdObj.optInt("frequency", 0)
                val patternArray = cmdObj.optJSONArray("pattern") ?: JSONArray()
                if (frequency <= 0 || patternArray.length() == 0) continue
                val pattern = IntArray(patternArray.length()) { idx ->
                    patternArray.optInt(idx, 0)
                }
                if (pattern.any { it <= 0 }) continue
                commands += IrButton(
                    name = cmdObj.optString("name").ifBlank { null },
                    frequency = frequency,
                    pattern = pattern,
                    protocol = cmdObj.optString("protocol").ifBlank { null },
                    irCode = cmdObj.optString("irCode").ifBlank { null },
                    timings = cmdObj.optString("timings").ifBlank { null }
                )
            }
            if (commands.isNotEmpty()) {
                restored += UserIrRemote(id = id, fileName = fileName, commands = commands)
            }
        }

        remoteList.clear()
        remoteList.addAll(restored)
        nextId = (remoteList.maxOfOrNull { it.id } ?: 0) + 1
        loaded = true
    }

    private fun persist(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val remotesArray = JSONArray()
        remoteList.forEach { remote ->
            val commandsArray = JSONArray()
            remote.commands.forEach { cmd ->
                val patternArray = JSONArray()
                cmd.pattern.forEach { value -> patternArray.put(value) }
                commandsArray.put(
                    JSONObject().apply {
                        put("name", cmd.name ?: "")
                        put("frequency", cmd.frequency)
                        put("pattern", patternArray)
                        put("protocol", cmd.protocol ?: "")
                        put("irCode", cmd.irCode ?: "")
                        put("timings", cmd.timings ?: "")
                    }
                )
            }

            remotesArray.put(
                JSONObject().apply {
                    put("id", remote.id)
                    put("fileName", remote.fileName)
                    put("commands", commandsArray)
                }
            )
        }
        prefs.edit().putString(KEY_USER_REMOTES_JSON, remotesArray.toString()).apply()
    }

    fun addRemote(context: Context, fileName: String, commands: List<IrButton>): UserIrRemote {
        ensureLoaded(context)
        val remote = UserIrRemote(id = nextId++, fileName = fileName, commands = commands)
        remoteList.add(0, remote)
        persist(context)
        return remote
    }

    fun getRemote(id: Int): UserIrRemote? = remoteList.firstOrNull { it.id == id }

    fun removeByIds(context: Context, ids: Set<Int>) {
        ensureLoaded(context)
        if (ids.isEmpty()) return
        remoteList.removeAll { it.id in ids }
        IrFavoriteStore.remove(context, ids.mapTo(mutableSetOf()) { "user:$it" })
        persist(context)
    }

    fun renameRemote(context: Context, id: Int, newFileName: String) {
        ensureLoaded(context)
        val index = remoteList.indexOfFirst { it.id == id }
        if (index < 0) return
        remoteList[index] = remoteList[index].copy(fileName = newFileName)
        IrFavoriteStore.updateTitle(context, "user:$id", newFileName)
        persist(context)
    }
}

object IrRepository {
    private var tvIndexCache: Map<String, Map<String, String>>? = null
    private var flIndexCache: Map<String, Map<String, String>>? = null
    private val buttonsCache = mutableMapOf<String, List<IrButton>>()
    private val resolvedAssetCache = mutableMapOf<String, String>()


    private fun listAssets(context: Context, path: String): List<String> {
        return try {
            context.assets.list(path)?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun rc5ToPattern(address: Int, command: Int, toggle: Int = 0): IntArray {
        val bits = mutableListOf(1, 1, toggle)

        for (i in 4 downTo 0) bits.add((address shr i) and 1)
        for (i in 5 downTo 0) bits.add((command shr i) and 1)
        val T = 889
        val pattern = mutableListOf<Int>()
        var state = true
        for (bit in bits) {
            val first = bit == 1
            val second = !first
            for (half in listOf(first, second)) {
                if (pattern.isNotEmpty() && half == state) {
                    pattern[pattern.lastIndex] = pattern.last() + T
                } else {
                    pattern.add(T)
                    state = half
                }
            }
        }
        return pattern.toIntArray()
    }

    private fun necToPattern(addrBytes: List<Int>, cmdBytes: List<Int>): IntArray {
        val bits = mutableListOf<Int>()
        for (b in addrBytes) for (i in 0..7) bits.add((b shr i) and 1)
        for (b in cmdBytes) for (i in 0..7) bits.add((b shr i) and 1)
        val T = 562
        val pattern = mutableListOf<Int>()

        pattern.add(9000); pattern.add(4500)
        for (bit in bits) {
            pattern.add(T)
            pattern.add(if (bit == 0) T else 3 * T)
        }
        pattern.add(T)
        return pattern.toIntArray()
    }

    private fun previewTimings(pattern: IntArray, maxValues: Int = 24): String {
        val preview = pattern.take(maxValues).joinToString(" ")
        return if (pattern.size > maxValues) "$preview ..." else preview
    }

    private fun reverseByte(value: Int): Int {
        var v = value and 0xFF
        var out = 0
        for (i in 0 until 8) {
            if ((v and (1 shl i)) != 0) out = out or (1 shl (7 - i))
        }
        return out and 0xFF
    }

    private fun parseHexBytes(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.trim()
            .split(Regex("\\s+"))
            .mapNotNull { it.trim().toIntOrNull(16) }
    }

    private fun bytesToIntLE(bytes: List<Int>): Int {
        var value = 0
        for (i in bytes.indices) {
            value = value or ((bytes[i] and 0xFF) shl (8 * i))
        }
        return value
    }

    private fun intToBytesLE(value: Int, count: Int): ByteArray {
        val out = ByteArray(count)
        for (i in 0 until count) {
            out[i] = ((value ushr (8 * i)) and 0xFF).toByte()
        }
        return out
    }

    private fun buildPdwmPattern(
        preambleMark: Int,
        preambleSpace: Int,
        bit1Mark: Int,
        bit1Space: Int,
        bit0Mark: Int,
        bit0Space: Int,
        data: ByteArray,
        bits: Int
    ): IntArray {
        val out = ArrayList<Int>(2 + bits * 2 + 1)
        if (preambleMark > 0) out.add(preambleMark)
        if (preambleSpace > 0) out.add(preambleSpace)
        val pwm = bit1Space == bit0Space
        for (i in 0 until bits) {
            val b = (data[i / 8].toInt() ushr (i % 8)) and 0x1
            if (b == 1) {
                out.add(bit1Mark)
                if (!pwm || i != bits - 1) out.add(bit1Space)
            } else {
                out.add(bit0Mark)
                if (!pwm || i != bits - 1) out.add(bit0Space)
            }
        }
        if (!pwm) {

            out.add(bit1Mark)
        }
        return out.toIntArray()
    }

    private fun buildManchesterPattern(
        preambleMark: Int,
        preambleSpace: Int,
        halfBit: Int,
        data: ByteArray,
        bits: Int,
        toggleBitDoubleIndex: Int = -1
    ): IntArray {
        val out = ArrayList<Int>(2 + bits * 2)
        var currentLevelMark = true
        fun emit(markLevel: Boolean, duration: Int) {
            if (duration <= 0) return
            if (out.isEmpty()) {

                if (!markLevel) {
                    out.add(1)
                    currentLevelMark = true
                }
            }
            if (out.isEmpty()) {

                out.add(duration)
                currentLevelMark = markLevel
                return
            }
            if (currentLevelMark == markLevel) {
                out[out.lastIndex] = out.last() + duration
            } else {
                out.add(duration)
                currentLevelMark = markLevel
            }
        }

        if (preambleMark > 0) emit(true, preambleMark)
        if (preambleSpace > 0) emit(false, preambleSpace)

        for (i in 0 until bits) {
            val b = (data[i / 8].toInt() ushr (i % 8)) and 0x1

            val firstIsMark = b == 1
            val firstDur = halfBit * if (i == toggleBitDoubleIndex) 2 else 1
            val secondDur = halfBit
            emit(firstIsMark, firstDur)
            emit(!firstIsMark, secondDur)
        }
        return out.toIntArray()
    }

    private fun protocolFrequency(proto: String, defaultFreq: Int): Int {
        return when (proto.uppercase()) {
            "SIRC", "SIRC15", "SIRC20", "PIONEER" -> 40000
            "RC5", "RC5X", "RC6" -> 36000
            else -> defaultFreq
        }
    }

    private fun parseIrText(text: String): List<IrButton> {
        val out = mutableListOf<IrButton>()
        var currentBlock = mutableListOf<String>()

        fun parseBlock(lines: List<String>) {
            if (lines.isEmpty()) return
            var name: String? = null
            var freq = 38000
            var freqSet = false
            var pattern: IntArray? = null
            var proto: String? = null
            var rawData: String? = null
            var addressRaw: String? = null
            var commandRaw: String? = null
            var addressBytes = mutableListOf<Int>()
            var commandBytes = mutableListOf<Int>()
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("name:") -> name = trimmed.substringAfter(':').trim()
                    trimmed.startsWith("frequency:") -> {
                        freq = trimmed.substringAfter(':').trim().toFloatOrNull()?.toInt() ?: freq
                        freqSet = true
                    }
                    trimmed.startsWith("data:") -> {
                        rawData = trimmed.substringAfter(':').trim()
                        pattern = rawData.split(Regex("\\s+"))
                            .mapNotNull { it.toIntOrNull() }.toIntArray()
                        if (pattern?.isEmpty() == true) pattern = null
                    }
                    trimmed.startsWith("type:") -> {

                    }
                    trimmed.startsWith("protocol:") -> proto = trimmed.substringAfter(':').trim()
                    trimmed.startsWith("address:") -> {
                        addressRaw = trimmed.substringAfter(':').trim()
                        addressBytes = parseHexBytes(addressRaw).toMutableList()
                    }
                    trimmed.startsWith("command:") -> {
                        commandRaw = trimmed.substringAfter(':').trim()
                        commandBytes = parseHexBytes(commandRaw).toMutableList()
                    }
                }
            }
            if (name == null) return
            if (pattern != null && pattern.isNotEmpty()) {
                Log.d("IrRepository", "Parsed: $name (freq=$freq Hz, pattern_len=${pattern.size})")
                out.add(
                    IrButton(
                        name = name,
                        frequency = freq,
                        pattern = pattern,
                        protocol = proto ?: "RAW",
                        irCode = rawData,
                        timings = previewTimings(pattern)
                    )
                )
            } else if (proto != null && addressBytes.isNotEmpty() && commandBytes.isNotEmpty()) {
                val protoKey = proto.uppercase()
                val addressInt = bytesToIntLE(addressBytes)
                val commandInt = bytesToIntLE(commandBytes)
                val pat = when (protoKey) {
                    "NEC" -> {
                        val address = addressInt and 0xFF
                        val command = commandInt and 0xFF
                        val data = address or ((address.inv() and 0xFF) shl 8) or
                            ((command and 0xFF) shl 16) or ((command.inv() and 0xFF) shl 24)
                        buildPdwmPattern(9000, 4500, 560, 1690, 560, 560, intToBytesLE(data, 4), 32)
                    }
                    "NECEXT" -> {
                        val address = addressInt and 0xFFFF
                        val command = commandInt and 0xFFFF
                        val data = address or ((command and 0xFFFF) shl 16)
                        buildPdwmPattern(9000, 4500, 560, 1690, 560, 560, intToBytesLE(data, 4), 32)
                    }
                    "NEC42" -> {
                        val address = addressInt and 0x1FFF
                        val command = commandInt and 0xFF
                        val data1 = (address and 0x1FFF) or
                            ((address.inv() and 0x1FFF) shl 13) or
                            ((command and 0x3F) shl 26)
                        val data2 = ((command and 0xC0) ushr 6) or
                            ((command.inv() and 0xFF) shl 2)
                        val bytes = ByteArray(8)
                        val b1 = intToBytesLE(data1, 4)
                        val b2 = intToBytesLE(data2, 4)
                        System.arraycopy(b1, 0, bytes, 0, 4)
                        System.arraycopy(b2, 0, bytes, 4, 4)
                        buildPdwmPattern(9000, 4500, 560, 1690, 560, 560, bytes, 42)
                    }
                    "SAMSUNG32" -> {
                        val address = addressInt and 0xFF
                        val command = commandInt and 0xFF
                        val data = address or (address shl 8) or
                            ((command and 0xFF) shl 16) or ((command.inv() and 0xFF) shl 24)
                        buildPdwmPattern(4500, 4500, 550, 1650, 550, 550, intToBytesLE(data, 4), 32)
                    }
                    "KASEIKYO" -> {
                        val address = addressInt and 0x3FFFFFF
                        val command = commandInt and 0x3FF
                        val data = ByteArray(6)
                        val id = (address ushr 24) and 0x3
                        val vendorId = (address ushr 8) and 0xFFFF
                        val genre1 = (address ushr 4) and 0xF
                        val genre2 = address and 0xF
                        data[0] = (vendorId and 0xFF).toByte()
                        data[1] = ((vendorId ushr 8) and 0xFF).toByte()
                        var vendorParity = (data[0].toInt() and 0xFF) xor (data[1].toInt() and 0xFF)
                        vendorParity = (vendorParity and 0xF) xor (vendorParity ushr 4)
                        data[2] = ((vendorParity and 0xF) or (genre1 shl 4)).toByte()
                        data[3] = ((genre2 and 0xF) or ((command and 0xF) shl 4)).toByte()
                        data[4] = ((id shl 6) or ((command ushr 4) and 0x3F)).toByte()
                        data[5] = (data[2].toInt() xor data[3].toInt() xor data[4].toInt()).toByte()
                        buildPdwmPattern(3456, 1728, 432, 1296, 432, 432, data, 48)
                    }
                    "RCA" -> {
                        val address = addressInt and 0xF
                        val command = commandInt and 0xFF
                        val data = address or ((command and 0xFF) shl 4) or
                            ((address.inv() and 0xF) shl 12) or ((command.inv() and 0xFF) shl 16)
                        buildPdwmPattern(4000, 4000, 500, 2000, 500, 1000, intToBytesLE(data, 4), 24)
                    }
                    "PIONEER" -> {
                        val address = addressInt and 0xFF
                        val command = commandInt and 0xFF
                        val data = ByteArray(5)
                        data[0] = address.toByte()
                        data[1] = (address.inv() and 0xFF).toByte()
                        data[2] = command.toByte()
                        data[3] = (command.inv() and 0xFF).toByte()
                        data[4] = 0
                        buildPdwmPattern(8500, 4225, 500, 1500, 500, 500, data, 33)
                    }
                    "SIRC" -> {
                        val address = addressInt and 0x1F
                        val command = commandInt and 0x7F
                        val data = command or (address shl 7)
                        buildPdwmPattern(2400, 600, 1200, 600, 600, 600, intToBytesLE(data, 3), 12)
                    }
                    "SIRC15" -> {
                        val address = addressInt and 0xFF
                        val command = commandInt and 0x7F
                        val data = command or (address shl 7)
                        buildPdwmPattern(2400, 600, 1200, 600, 600, 600, intToBytesLE(data, 3), 15)
                    }
                    "SIRC20" -> {
                        val address = addressInt and 0x1FFF
                        val command = commandInt and 0x7F
                        val data = command or (address shl 7)
                        buildPdwmPattern(2400, 600, 1200, 600, 600, 600, intToBytesLE(data, 4), 20)
                    }
                    "RC5", "RC5X" -> {
                        val address = addressInt and 0x1F
                        val command = commandInt and 0x3F
                        var data = 0x01
                        if (protoKey == "RC5") data = data or 0x02
                        data = data or ((reverseByte(address) ushr 3) shl 3)
                        data = data or ((reverseByte(command) ushr 2) shl 8)
                        val b0 = (data.inv() and 0xFF).toByte()
                        val b1 = ((data ushr 8).inv() and 0xFF).toByte()
                        buildManchesterPattern(0, 0, 888, byteArrayOf(b0, b1), 14)
                    }
                    "RC6" -> {
                        val address = addressInt and 0xFF
                        val command = commandInt and 0xFF
                        var data = 0x01
                        data = data or (reverseByte(address) shl 5)
                        data = data or (reverseByte(command) shl 13)
                        buildManchesterPattern(2666, 889, 444, intToBytesLE(data, 4), 21, toggleBitDoubleIndex = 4)
                    }
                    else -> {
                        Log.w("IrRepository", "Unsupported protocol: $proto for $name")
                        null
                    }
                }
                if (pat != null) {
                    Log.d("IrRepository", "Parsed: $name (proto=$proto, freq=$freq Hz, pattern_len=${pat.size})")
                    out.add(
                        IrButton(
                            name = name,
                            frequency = if (freqSet) freq else protocolFrequency(proto, freq),
                            pattern = pat,
                            protocol = proto,
                            irCode = "address: ${addressRaw ?: ""}; command: ${commandRaw ?: ""}",
                            timings = previewTimings(pat)
                        )
                    )
                } else {
                    Log.w("IrRepository", "Failed to generate pattern for $name ($proto)")
                }
            } else {
                Log.w("IrRepository", "Skipped $name: no pattern (raw=$pattern, proto=$proto, addr=${addressBytes.size}, cmd=${commandBytes.size})")
            }
        }

        for (line in text.lineSequence()) {
            val trimmedStart = line.trimStart()
            if (trimmedStart.startsWith("name:")) {
                parseBlock(currentBlock)
                currentBlock = mutableListOf(line)
            } else if (currentBlock.isNotEmpty()) {
                currentBlock.add(line)
            }
        }
        parseBlock(currentBlock)
        return out
    }

    private fun resolveAssetPath(context: Context, requestedPath: String): String? {
        resolvedAssetCache[requestedPath]?.let { return it }
        val normalized = requestedPath.replace('\\', '/').removePrefix("/")
        val candidates = LinkedHashSet<String>()
        candidates.add(normalized)
        if (normalized.startsWith("assets/")) {
            candidates.add(normalized.removePrefix("assets/"))
        }

        val fileName = normalized.substringAfterLast('/')
        val universalBase =
            "flipperzero-firmware-dev/applications/main/infrared/resources/infrared/assets"
        if (fileName.isNotBlank()) {
            candidates.add("$universalBase/$fileName")
        }

        for (candidate in candidates) {
            try {
                context.assets.open(candidate).close()
                resolvedAssetCache[requestedPath] = candidate
                return candidate
            } catch (_: Exception) {

            }
        }


        if (fileName.isNotBlank()) {
            val found = findAssetByName(context, fileName, "")
            if (found != null) {
                resolvedAssetCache[requestedPath] = found
                return found
            }
        }
        return null
    }

    private fun findAssetByName(context: Context, fileName: String, dir: String): String? {
        val entries = try {
            context.assets.list(dir)?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        for (entry in entries) {
            val path = if (dir.isBlank()) entry else "$dir/$entry"
            if (entry.equals(fileName, ignoreCase = true)) {
                try {
                    context.assets.open(path).close()
                    return path
                } catch (_: Exception) {

                }
            }
            val children = try { context.assets.list(path) } catch (_: Exception) { null }
            if (!children.isNullOrEmpty()) {
                val found = findAssetByName(context, fileName, path)
                if (found != null) return found
            }
        }
        return null
    }

    fun parseIrAsset(context: Context, assetPath: String): List<IrButton> {
        val resolvedPath = resolveAssetPath(context, assetPath)
        if (resolvedPath == null) {
            Log.e("IrRepository", "Failed to resolve asset path: $assetPath")
            return emptyList()
        }
        val text = try {
            context.assets.open(resolvedPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.e("IrRepository", "Failed to read asset $resolvedPath (requested $assetPath): ${e.message}")
            return emptyList()
        }
        val parsed = parseIrText(text)
        Log.i("IrRepository", "Parsed ${parsed.size} buttons from $resolvedPath (requested $assetPath)")
        return parsed
    }

    fun parseIrUri(context: Context, uri: Uri): List<IrButton> {
        val text = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: Exception) {
            Log.e("IrRepository", "Failed to read user IR file $uri: ${e.message}")
            null
        } ?: return emptyList()
        return parseIrText(text)
    }

    fun parseIrFile(file: File): List<IrButton> {
        val text = try {
            if (!file.exists() || !file.isFile) return emptyList()
            file.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.e("IrRepository", "Failed to read IR file ${file.absolutePath}: ${e.message}")
            return emptyList()
        }
        return parseIrText(text)
    }

    fun getTvIndex(context: Context): Map<String, Map<String, String>> {
        tvIndexCache?.let { return it }

        val base = "tv_best_remotes"
        val brands = listAssets(context, base)
        val result = mutableMapOf<String, MutableMap<String, String>>()
        for (brand in brands) {
            val models = listAssets(context, "$base/$brand")
            val modelMap = mutableMapOf<String, String>()
            for (model in models.filter { it.endsWith(".ir") }) {
                modelMap[model.removeSuffix(".ir")] = "$base/$brand/$model"
            }
            if (modelMap.isNotEmpty()) result[brand] = modelMap
        }
        tvIndexCache = result
        return result
    }

    fun getFlipperIndex(context: Context): Map<String, Map<String, String>> {
        flIndexCache?.let { return it }

        val cacheFile = java.io.File(context.filesDir, "ir_flipper_index_v1.txt")
        if (cacheFile.isFile && cacheFile.length() > 64) {
            try {
                val result = mutableMapOf<String, MutableMap<String, String>>()
                cacheFile.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split('\t')
                        if (parts.size >= 3) {
                            val catBrand = parts[0]
                            val model = parts[1]
                            val path = parts[2]
                            result.getOrPut(catBrand) { mutableMapOf() }[model] = path
                        }
                    }
                }
                if (result.isNotEmpty()) {
                    flIndexCache = result
                    return result
                }
            } catch (e: Exception) {
                Log.w("IrRepository", "IR index cache read failed: ${e.message}")
            }
        }
        val base = "Flipper-IRDB-main"
        val cats = listAssets(context, base)
        val result = mutableMapOf<String, MutableMap<String, String>>()
        for (cat in cats) {
            val brands = listAssets(context, "$base/$cat")
            for (brand in brands) {
                val models = listAssets(context, "$base/$cat/$brand")
                val modelMap = mutableMapOf<String, String>()
                for (model in models.filter { it.endsWith(".ir") }) {
                    modelMap[model.removeSuffix(".ir")] = "$base/$cat/$brand/$model"
                }
                if (modelMap.isNotEmpty()) result["$cat/$brand"] = modelMap
            }
        }
        flIndexCache = result
        try {
            cacheFile.bufferedWriter().use { w ->
                for ((catBrand, models) in result) {
                    for ((model, path) in models) {
                        w.append(catBrand).append('\t').append(model).append('\t').append(path).append('\n')
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("IrRepository", "IR index cache write failed: ${e.message}")
        }
        return result
    }


    fun warmIndexInBackground(context: Context) {
        Thread({
            try {
                getFlipperIndex(context.applicationContext)
                getTvIndex(context.applicationContext)
            } catch (e: Exception) {
                Log.w("IrRepository", "warmIndex: ${e.message}")
            }
        }, "ir-index-warm").start()
    }

    fun loadButtons(context: Context, assetPath: String): List<IrButton> {
        return buttonsCache.getOrPut(assetPath) { parseIrAsset(context, assetPath) }
    }
}

internal fun localizeFlipperCategory(res: Resources, cat: String): String {
    val id = when (cat.lowercase()) {
        "_converted_" -> R.string.ir_cat_standard
        "acs" -> R.string.ir_cat_air_conditioners
        "air_purifiers" -> R.string.ir_cat_air_purifiers
        "audio_and_video_receivers" -> R.string.ir_cat_av_receivers
        "bidet" -> R.string.ir_cat_bidets
        "blu-ray" -> R.string.ir_cat_bluray
        "cable_boxes" -> R.string.ir_cat_cable_stb
        "cameras" -> R.string.ir_cat_cameras
        "car_multimedia" -> R.string.ir_cat_car_multimedia
        "cctv" -> R.string.ir_cat_cctv
        "cd_players" -> R.string.ir_cat_cd_players
        "clocks" -> R.string.ir_cat_clocks
        "computers" -> R.string.ir_cat_computers
        "consoles" -> R.string.ir_cat_consoles
        "converters" -> R.string.ir_cat_converters
        "digital_signs" -> R.string.ir_cat_digital_signage
        "dust_collectors" -> R.string.ir_cat_vacuum_cleaners
        "dvb-t" -> R.string.ir_cat_dvb_t
        "dvd_players" -> R.string.ir_cat_dvd_players
        "fans" -> R.string.ir_cat_fans
        "fireplaces" -> R.string.ir_cat_fireplaces
        "head_units" -> R.string.ir_cat_head_units
        "heaters" -> R.string.ir_cat_heaters
        "humidifiers" -> R.string.ir_cat_humidifiers
        "kvm" -> R.string.ir_cat_kvm
        "laserdisc" -> R.string.ir_cat_laserdisc
        "led_lighting" -> R.string.ir_cat_led_lighting
        "minidisc" -> R.string.ir_cat_minidisc
        "miscellaneous" -> R.string.ir_cat_miscellaneous
        "monitors" -> R.string.ir_cat_monitors
        "multimedia" -> R.string.ir_cat_multimedia
        "picture_frames" -> R.string.ir_cat_photo_frames
        "projectors" -> R.string.ir_cat_projectors
        "soundbars" -> R.string.ir_cat_soundbars
        "speakers" -> R.string.ir_cat_speakers
        "streaming_devices" -> R.string.ir_cat_streaming
        "touchscreen_displays" -> R.string.ir_cat_touchscreens
        "toys" -> R.string.ir_cat_toys
        "tv_tuner" -> R.string.ir_cat_tv_tuners
        "tvs" -> R.string.ir_cat_tv
        "universal_tv_remotes" -> R.string.ir_cat_universal
        "vacuum_cleaners" -> R.string.ir_cat_vacuum_cleaners
        "vcr" -> R.string.ir_cat_vcr
        "videoconferencing" -> R.string.ir_cat_video_conferencing
        "whiteboards" -> R.string.ir_cat_whiteboards
        "window_cleaners" -> R.string.ir_cat_window_cleaners
        "banners" -> R.string.ir_cat_banners
        "ик глушилка" -> R.string.ir_cat_jammer
        else -> null
    }
    return if (id != null) res.getString(id) else cat
}

@Composable
private fun categoryIcon(catId: String): ImageVector {
    return when (catId.lowercase()) {
        "tvs", "tv_tuner", "cable_boxes", "dvb-t", "streaming_devices", "universal_tv_remotes" -> Icons.Default.Tv
        "_converted_" -> Icons.Default.Home
        "acs" -> Icons.Default.AcUnit
        "air_purifiers" -> Icons.Default.Air
        "audio_and_video_receivers" -> Icons.Default.VolumeUp
        "bidet" -> Icons.Default.Water
        "blu-ray", "dvd_players", "cd_players", "vcr", "laserdisc", "minidisc" -> Icons.Default.DiscFull
        "cameras" -> Icons.Default.CameraAlt
        "car_multimedia" -> Icons.Default.DirectionsCar
        "cctv" -> Icons.Default.Videocam
        "clocks" -> Icons.Default.Alarm
        "computers" -> Icons.Default.Computer
        "consoles" -> Icons.Default.VideogameAsset
        "converters" -> Icons.Default.PowerSettingsNew
        "digital_signs", "banners" -> Icons.Default.ViewCarousel
        "dust_collectors", "vacuum_cleaners", "window_cleaners" -> Icons.Default.CleaningServices
        "fans" -> Icons.Default.Air
        "fireplaces", "heaters" -> Icons.Default.LocalFireDepartment
        "head_units" -> Icons.Default.Headphones
        "humidifiers" -> Icons.Default.WaterDrop
        "kvm" -> Icons.Default.SettingsEthernet
        "led_lighting" -> Icons.Default.Lightbulb
        "miscellaneous", "multimedia" -> Icons.Default.Dashboard
        "monitors" -> Icons.Default.MonitorWeight
        "picture_frames" -> Icons.Default.Panorama
        "projectors" -> Icons.Default.Slideshow
        "soundbars", "speakers" -> Icons.Default.Speaker
        "touchscreen_displays" -> Icons.Default.TouchApp
        "toys" -> Icons.Default.Toys
        "videoconferencing" -> Icons.Default.Videocam
        "whiteboards" -> Icons.Default.Draw
        "ик глушилка" -> Icons.Default.Block
        else -> Icons.Default.DevicesOther
    }
}

@Composable
private fun FlipperCategoryRow(
    icon: ImageVector,
    title: String,
    accent: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}







fun transmitIr(context: Context, button: IrButton) {
    Log.d("IrTransmit", "=== START transmitIr for: ${button.name} ===")

    val cm = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    if (cm == null) {
        Log.e("IrTransmit", "FATAL: ConsumerIrManager is NULL!")
        return
    }
    Log.d("IrTransmit", "✓ ConsumerIrManager obtained")

    val hasIr = cm.hasIrEmitter()
    Log.d("IrTransmit", "hasIrEmitter() = $hasIr")
    if (!hasIr) {
        Log.e("IrTransmit", "Device does NOT have IR emitter capability")
        return
    }

    val freq = button.frequency
    val pat = button.pattern

    Log.d("IrTransmit", "Frequency: $freq Hz")
    Log.d("IrTransmit", "Pattern length: ${pat.size} values")

    if (pat.isEmpty()) {
        Log.e("IrTransmit", "Pattern is EMPTY!")
        return
    }


    val first10 = pat.take(10).joinToString(", ")
    Log.d("IrTransmit", "First 10 values: $first10")

    val invalidCount = pat.count { it <= 0 }
    if (invalidCount > 0) {
        Log.e("IrTransmit", "Pattern has $invalidCount invalid (<=0) values!")
        val invalidIndices = pat.mapIndexed { i, v -> if (v <= 0) i else null }.filterNotNull()
        Log.d("IrTransmit", "Invalid indices: $invalidIndices")
        return
    }
    Log.d("IrTransmit", "✓ All pattern values are positive")

    try {
        Log.d("IrTransmit", "Calling ConsumerIrManager.transmit($freq, IntArray[${pat.size}])...")
        cm.transmit(freq, pat)
        trackIrSend(context)
        Log.i("IrTransmit", "✓✓✓ IR transmission successful! ✓✓✓")
    } catch (e: Exception) {
        Log.e("IrTransmit", "transmit() threw exception: ${e.javaClass.simpleName}: ${e.message}")
        e.printStackTrace()
    }
    Log.d("IrTransmit", "=== END transmitIr ===")
}




fun hasIrEmitter(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    return cm?.hasIrEmitter() == true
}

private val irListsBottomPadding = 220.dp

@Composable
fun IRTvHome(navController: NavController) {
    val bottomScrollPadding = irListsBottomPadding
    val context = LocalContext.current
    var brands by remember { mutableStateOf<List<String>?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            brands = IrRepository.getTvIndex(context).keys.sorted()
        }
    }

    if (brands == null) {
        LoadingScreen()
        return
    }

    if (brands!!.isEmpty()) {
        SimpleMessageScreen(stringResource(R.string.ir_tv_db_not_found))
        return
    }
    val filteredBrands = remember(brands, searchQuery) {
        val items = brands.orEmpty()
        if (searchQuery.isBlank()) {
            items
        } else {
            items.filter { brand -> matchesTvBrandQuery(brand, searchQuery) }
        }
    }
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SectionTopBar(title = stringResource(R.string.ir_tv_remotes), onBack = { navController.popBackStack() }, transparent = true)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentPadding = PaddingValues(bottom = bottomScrollPadding),
            verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)
        ) {
            item {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text(stringResource(R.string.ir_search_brand)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
            if (filteredBrands.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.ir_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                m3SegmentedItems(filteredBrands) { index, count, brand ->
                    M3SegmentedListItem(
                        index = index,
                        count = count,
                        headline = brand,
                        onClick = { navController.navigate("other/tv_brand/${Uri.encode(brand)}") },
                    )
                }
            }
        }
    }
}

@Composable
fun IRFlipperHome(navController: NavController) {
    val bottomScrollPadding = irListsBottomPadding
    val context = LocalContext.current
    val res = context.resources
    var categories by remember { mutableStateOf<List<String>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FlipperSearchResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            categories = IrRepository.getFlipperIndex(context).keys
                .map { it.substringBefore('/') }
                .distinct()
                .sorted()
        }
    }

    LaunchedEffect(searchQuery) {
        val query = searchQuery
        if (query.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        val results = withContext(Dispatchers.IO) {
            findFlipperRemotes(context, query, limit = 40)
        }
        searchResults = results
    }

    if (categories == null) {
        LoadingScreen()
        return
    }

    if (categories!!.isEmpty()) {
        SimpleMessageScreen(stringResource(R.string.ir_flipper_db_not_found))
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    val favTitle = stringResource(R.string.ir_favorites)
    val userRemotesTitle = stringResource(R.string.ir_user_remotes_title)
    val resultsLabel = stringResource(R.string.ir_label_results)
    val notFound = stringResource(R.string.ir_not_found)
    val flipperTitle = stringResource(R.string.ir_flipper_remotes)
    val smartSearch = stringResource(R.string.ir_smart_search)
    val searchCd = stringResource(R.string.cd_search)

    val quickLinks = listOf(
        Triple(Icons.Default.Star, favTitle, "other/ir_favorites"),
        Triple(Icons.Default.FolderOpen, userRemotesTitle, "other/user_ir_remotes"),
    )
    val catRows = buildList {
        add(Triple(Icons.Default.Tv, "Телевизоры", "other/ir_tv_home"))
        categories!!.forEach { catId ->
            add(
                Triple(
                    categoryIcon(catId),
                    localizeFlipperCategory(res, catId),
                    if (catId.equals("ик глушилка", ignoreCase = true)) {
                        "other/ir_jammer"
                    } else {
                        "other/flipper_cat/${Uri.encode(catId)}"
                    },
                ),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SectionTopBar(title = flipperTitle, onBack = { navController.popBackStack() }, transparent = true)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentPadding = PaddingValues(bottom = bottomScrollPadding),
            verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)
        ) {
            item {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text(smartSearch) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = searchCd) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
            m3SegmentedItems(quickLinks) { index, count, link ->
                M3SegmentedListItem(
                    index = index,
                    count = count,
                    headline = link.second,
                    leadingIcon = link.first,
                    leadingIconTint = accent,
                    onClick = { navController.navigate(link.third) },
                )
            }

            if (searchQuery.isNotBlank()) {
                if (searchResults.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        SectionLabel(resultsLabel)
                    }
                    m3SegmentedItems(searchResults) { index, count, item ->
                        M3SegmentedListItem(
                            index = index,
                            count = count,
                            headline = item.model,
                            supporting = "${localizeFlipperCategory(res, item.category)} • ${item.brand}",
                            onClick = {
                                navController.navigate(
                                    "flipper_remote/${Uri.encode(item.category)}/${Uri.encode(item.brand)}/${Uri.encode(item.model)}"
                                )
                            },
                        )
                    }
                } else {
                    item {
                        Text(
                            text = notFound,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                item {
                    Spacer(Modifier.height(12.dp))
                    SectionLabel(flipperTitle)
                }
                m3SegmentedItems(catRows) { index, count, row ->
                    M3SegmentedListItem(
                        index = index,
                        count = count,
                        headline = row.second,
                        leadingIcon = row.first,
                        leadingIconTint = accent,
                        onClick = { navController.navigate(row.third) },
                    )
                }
            }
        }
    }
}

private data class FlipperSearchResult(
    val category: String,
    val brand: String,
    val model: String,
    val score: Int
)

private fun normalizeSearch(text: String): String {
    return text.lowercase()
        .replace("ё", "е")
        .replace(Regex("[^a-z0-9а-я]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun toAcronym(text: String): String {
    val parts = normalizeSearch(text).split(" ").filter { it.isNotBlank() }
    if (parts.isEmpty()) return ""
    return parts.joinToString("") { it.take(1) }
}

private fun translitRuToEn(text: String): String {
    val map = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya"
    )
    val sb = StringBuilder()
    for (c in text.lowercase()) {
        val r = map[c]
        if (r != null) sb.append(r) else sb.append(c)
    }
    return sb.toString()
}

private fun translitEnToRu(text: String): String {
    var t = text.lowercase()
    val pairs = listOf(
        "sch" to "щ", "sh" to "ш", "ch" to "ч", "zh" to "ж", "ts" to "ц",
        "yu" to "ю", "ya" to "я"
    )
    pairs.forEach { (a, b) -> t = t.replace(a, b) }
    val map = mapOf(
        'a' to "а", 'b' to "б", 'v' to "в", 'g' to "г", 'd' to "д", 'e' to "е",
        'z' to "з", 'i' to "и", 'y' to "й", 'k' to "к", 'l' to "л", 'm' to "м",
        'n' to "н", 'o' to "о", 'p' to "п", 'r' to "р", 's' to "с", 't' to "т",
        'u' to "у", 'f' to "ф", 'h' to "х", 'c' to "к", 'j' to "дж", 'q' to "к",
        'w' to "в", 'x' to "кс"
    )
    val sb = StringBuilder()
    for (c in t) {
        val r = map[c]
        if (r != null) sb.append(r) else sb.append(c)
    }
    return sb.toString()
}

private fun expandQueryAliases(tokens: Set<String>): Set<String> {
    val out = tokens.toMutableSet()
    fun addAll(keys: List<String>, aliases: List<String>) {
        if (keys.any { it in tokens }) out.addAll(aliases)
    }
    addAll(listOf("tv", "тв", "телевизор", "телек"), listOf("tv", "tvs", "television", "телевизор", "тв", "телек"))
    addAll(listOf("samsung", "самсунг"), listOf("samsung", "самсунг"))
    addAll(listOf("xiaomi", "сяоми", "шаоми", "mi", "redmi"), listOf("xiaomi", "сяоми", "шаоми", "mi", "redmi"))
    addAll(listOf("acer", "асер"), listOf("acer", "асер"))
    addAll(listOf("lg", "лджи", "лг"), listOf("lg", "лджи", "лг"))
    addAll(listOf("sony", "сони"), listOf("sony", "сони"))
    addAll(listOf("philips", "филипс", "филлипс"), listOf("philips", "филипс", "филлипс"))
    addAll(listOf("panasonic", "панасоник"), listOf("panasonic", "панасоник"))
    addAll(listOf("toshiba", "тошиба"), listOf("toshiba", "тошиба"))
    addAll(listOf("sharp", "шарп"), listOf("sharp", "шарп"))
    addAll(listOf("monitor", "монитор"), listOf("monitor", "монитор", "мониторы"))
    addAll(listOf("rgb", "ргб"), listOf("rgb", "ргб", "цвет", "color", "light", "lighting", "led", "leds"))
    addAll(listOf("air", "воздух", "эир"), listOf("air", "air purifier", "air_purifier", "очиститель", "очиститель воздуха", "purifier"))
    addAll(listOf("clean", "очист", "очиститель"), listOf("clean", "cleaner", "purifier", "очиститель", "очистка"))
    addAll(listOf("ac", "кондиционер", "кондей", "aircon"), listOf("ac", "acs", "air conditioner", "кондиционер", "кондей"))
    addAll(listOf("soundbar", "саундбар"), listOf("soundbar", "sound bar", "саундбар"))
    addAll(listOf("projector", "проектор"), listOf("projector", "проектор"))
    addAll(listOf("cam", "camera", "камера"), listOf("camera", "cam", "камера"))
    addAll(listOf("receiver", "ресивер"), listOf("receiver", "reciever", "ресивер", "av", "avr"))
    addAll(listOf("speaker", "speakers", "колонка", "колонки"), listOf("speaker", "speakers", "колонка", "колонки"))
    addAll(listOf("console", "консоль", "ps", "xbox"), listOf("console", "консоль", "ps", "playstation", "xbox"))
    addAll(listOf("fan", "вентилятор"), listOf("fan", "вентилятор"))
    addAll(listOf("heater", "обогреватель"), listOf("heater", "heaters", "обогреватель"))
    addAll(listOf("dvd", "blu", "bluray"), listOf("dvd", "dvd player", "blu", "blu-ray", "bluray"))
    addAll(listOf("tv box", "приставка", "stb"), listOf("cable", "cable box", "set top", "set-top", "stb", "приставка"))
    return out
}

private fun editDistanceMax(a: String, b: String, max: Int): Int {
    if (kotlin.math.abs(a.length - b.length) > max) return max + 1
    val prev = IntArray(b.length + 1) { it }
    val curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        var rowMin = curr[0]
        val ca = a[i - 1]
        for (j in 1..b.length) {
            val cost = if (ca == b[j - 1]) 0 else 1
            val v = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            curr[j] = v
            if (v < rowMin) rowMin = v
        }
        if (rowMin > max) return max + 1
        System.arraycopy(curr, 0, prev, 0, curr.size)
    }
    return prev[b.length]
}

private fun fuzzyContains(token: String, text: String, maxDist: Int = 2): Boolean {
    if (token.length < 4) return false
    val parts = normalizeSearch(text).split(" ").filter { it.isNotBlank() }
    return parts.any { editDistanceMax(token, it, maxDist) <= maxDist }
}

private fun scoreMatch(tokens: Set<String>, category: String, brand: String, model: String): Int {
    val catN = normalizeSearch(category)
    val brandN = normalizeSearch(brand)
    val modelN = normalizeSearch(model)
    val catAcr = toAcronym(catN)
    val brandAcr = toAcronym(brandN)
    val modelAcr = toAcronym(modelN)
    var score = 0
    tokens.forEach { t ->
        if (t.isBlank()) return@forEach
        if (brandN.contains(t)) score += 8
        if (modelN.contains(t)) score += 6
        if (catN.contains(t)) score += 4
        if (!brandN.contains(t) && fuzzyContains(t, brandN)) score += 3
        if (!modelN.contains(t) && fuzzyContains(t, modelN)) score += 2
        if (t == brandAcr) score += 4
        if (t == modelAcr) score += 3
        if (t == catAcr) score += 2
    }
    if (tokens.any { it in setOf("tv", "тв", "телевизор", "телек") } &&
        (catN.contains("tv") || catN.contains("tvs") || catN.contains("телевиз"))
    ) {
        score += 8
    }
    if (tokens.any { it in setOf("monitor", "монитор") } &&
        (catN.contains("monitor") || catN.contains("монитор"))
    ) {
        score += 8
    }
    if (tokens.any { it == brandN }) score += 6
    return score
}

private fun findFlipperRemotes(context: Context, query: String, limit: Int): List<FlipperSearchResult> {
    val base = normalizeSearch(query)
    val tokensRaw = base.split(" ").filter { it.isNotBlank() }.toMutableSet()
    val translitEn = normalizeSearch(translitRuToEn(base))
    val translitRu = normalizeSearch(translitEnToRu(base))
    tokensRaw.addAll(translitEn.split(" ").filter { it.isNotBlank() })
    tokensRaw.addAll(translitRu.split(" ").filter { it.isNotBlank() })
    if (tokensRaw.isEmpty()) return emptyList()
    val tokens = expandQueryAliases(tokensRaw)
    val flIndex = IrRepository.getFlipperIndex(context)
    val results = ArrayList<FlipperSearchResult>()
    for ((catBrand, models) in flIndex) {
        val category = catBrand.substringBefore('/')
        val brand = catBrand.substringAfter('/')
        val brandScoreBase = scoreMatch(tokens, category, brand, brand)
        if (brandScoreBase <= 0 && tokens.any { it.length >= 4 }) {
            continue
        }
        for (model in models.keys) {
            val score = scoreMatch(tokens, category, brand, model)
            if (score > 0) {
                results.add(FlipperSearchResult(category, brand, model, score))
            }
        }
    }
    return results
        .sortedWith(compareByDescending<FlipperSearchResult> { it.score }.thenBy { it.model })
        .take(limit)
}

private fun matchesTvBrandQuery(brand: String, query: String): Boolean {
    val normalizedBrand = normalizeSearch(brand)
    val normalizedQuery = normalizeSearch(query)
    if (normalizedQuery.isBlank()) return true
    if (normalizedBrand.contains(normalizedQuery)) return true

    val translitEn = normalizeSearch(translitRuToEn(normalizedQuery))
    if (translitEn.isNotBlank() && normalizedBrand.contains(translitEn)) return true

    val translitRu = normalizeSearch(translitEnToRu(normalizedQuery))
    if (translitRu.isNotBlank() && normalizedBrand.contains(translitRu)) return true

    return fuzzyContains(normalizedQuery, normalizedBrand)
}

private data class UniversalSignalAction(
    val signalName: String,
    val displayName: String
)

private data class UniversalCategoryConfig(
    val id: String,
    val title: String,
    val description: String,
    val assetFiles: List<String>,
    val actions: List<UniversalSignalAction>
)

private object UniversalRemotesRepository {
    private const val BASE_PATH =
        "flipperzero-firmware-dev/applications/main/infrared/resources/infrared/assets"

    fun getCategories(context: Context): List<UniversalCategoryConfig> {
        val flipperIndex = IrRepository.getFlipperIndex(context)
        fun stackedFiles(category: String): List<String> = flipperIndex
            .filterKeys { it.substringBefore('/').equals(category, ignoreCase = true) }
            .values
            .flatMap { it.values }
            .distinct()

        fun stackedCategory(
            id: String,
            sourceCategory: String,
            title: String,
            actionNames: List<String>
        ) = UniversalCategoryConfig(
            id = id,
            title = title,
            description = context.getString(R.string.universal_desc_stacked, title),
            assetFiles = stackedFiles(sourceCategory),
            actions = actionNames.distinct().map { UniversalSignalAction(it, it.replace('_', ' ')) }
        )

        val tvActions = listOf(
            UniversalSignalAction(signalName = "Power", displayName = "Power"),
            UniversalSignalAction(signalName = "Ch_next", displayName = "CH+"),
            UniversalSignalAction(signalName = "Ch_prev", displayName = "CH-"),
            UniversalSignalAction(signalName = "Vol_up", displayName = "VOL+"),
            UniversalSignalAction(signalName = "Vol_dn", displayName = "VOL-")
        )

        val acActions = listOf(
            UniversalSignalAction(signalName = "Off", displayName = "Off"),
            UniversalSignalAction(signalName = "Dh", displayName = "Dry"),
            UniversalSignalAction(signalName = "Cool_hi", displayName = "\u2744\ufe0f+"),
            UniversalSignalAction(signalName = "Cool_lo", displayName = "\u2744\ufe0f-"),
            UniversalSignalAction(signalName = "Heat_hi", displayName = "\u2600\ufe0f+"),
            UniversalSignalAction(signalName = "Heat_lo", displayName = "\u2600\ufe0f-")
        )

        val audioActions = listOf(
            UniversalSignalAction(signalName = "Power", displayName = "Power"),
            UniversalSignalAction(signalName = "Mute", displayName = "Mute"),
            UniversalSignalAction(signalName = "Play", displayName = "Play"),
            UniversalSignalAction(signalName = "Pause", displayName = "Pause"),
            UniversalSignalAction(signalName = "Next", displayName = "Next"),
            UniversalSignalAction(signalName = "Prev", displayName = "Prev"),
            UniversalSignalAction(signalName = "Vol_up", displayName = "VOL+"),
            UniversalSignalAction(signalName = "Vol_dn", displayName = "VOL-")
        )

        val projectorActions = listOf(
            UniversalSignalAction(signalName = "Power", displayName = "Power"),
            UniversalSignalAction(signalName = "Mute", displayName = "Mute"),
            UniversalSignalAction(signalName = "Vol_up", displayName = "VOL+"),
            UniversalSignalAction(signalName = "Vol_dn", displayName = "VOL-")
        )

        return listOf(
            UniversalCategoryConfig(
                id = "tvs",
                title = context.getString(R.string.ir_cat_tv),
                description = context.getString(R.string.universal_desc_tv_ir),
                assetFiles = listOf("$BASE_PATH/tv.ir"),
                actions = tvActions
            ),
            UniversalCategoryConfig(
                id = "acs",
                title = context.getString(R.string.ir_cat_air_conditioners),
                description = context.getString(R.string.universal_desc_ac_ir),
                assetFiles = listOf("$BASE_PATH/ac.ir"),
                actions = acActions
            ),
            UniversalCategoryConfig(
                id = "audio",
                title = context.getString(R.string.ir_cat_audio),
                description = context.getString(R.string.universal_desc_audio_ir),
                assetFiles = listOf("$BASE_PATH/audio.ir"),
                actions = audioActions
            ),
            UniversalCategoryConfig(
                id = "projectors",
                title = context.getString(R.string.ir_cat_projectors),
                description = context.getString(R.string.universal_desc_projector_ir),
                assetFiles = listOf("$BASE_PATH/projector.ir"),
                actions = projectorActions
            ),
            stackedCategory(
                id = "stacked_led_lighting",
                sourceCategory = "LED_Lighting",
                title = context.getString(R.string.ir_cat_led_lighting),
                actionNames = listOf(
                    "Power", "On", "Off", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                    "Red", "Green", "Blue", "White", "Yellow", "Orange", "Pink", "Purple",
                    "Brighter", "Dimmer", "Bright_up", "Bright_down", "Flash", "Strobe", "Fade", "Smooth", "Auto"
                )
            ),
            stackedCategory(
                id = "stacked_fans",
                sourceCategory = "Fans",
                title = context.getString(R.string.ir_cat_fans),
                actionNames = listOf(
                    "Power", "On", "Off", "1", "2", "3", "4", "5", "6", "Speed", "Speed_Up",
                    "Speed_Down", "Mode", "Timer", "Swing", "Osc", "Oscillate", "Rotate", "Auto"
                )
            ),
            stackedCategory(
                id = "stacked_heaters",
                sourceCategory = "Heaters",
                title = context.getString(R.string.ir_cat_heaters),
                actionNames = listOf("Power", "On", "Off", "TEMP+", "TEMP-", "Temp_up", "Temp_dn", "Mode", "Timer", "Oscillate", "Fan")
            ),
            stackedCategory(
                id = "stacked_fireplaces",
                sourceCategory = "Fireplaces",
                title = context.getString(R.string.ir_cat_fireplaces),
                actionNames = listOf("Power", "On", "Off", "Heat", "Flame", "Temp_up", "Temp_down", "Timer")
            )
        )
    }

    fun getCategory(context: Context, id: String): UniversalCategoryConfig? = getCategories(context).firstOrNull { it.id == id }

    fun getSignalsForAction(
        context: Context,
        category: UniversalCategoryConfig,
        signalName: String
    ): List<IrButton> {
        if (category.assetFiles.isEmpty()) return emptyList()
        val aliasSet = universalAliases(signalName)
        val all = category.assetFiles
            .flatMap { IrRepository.loadButtons(context, it) }
        val direct = all.filter { it.name.equals(signalName, ignoreCase = true) }
        if (direct.isNotEmpty()) return direct
        return all.filter { btn ->
            val norm = normalizeSearch(btn.name ?: "")
            aliasSet.any { alias -> norm == alias || norm.contains(alias) }
        }
    }
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

@Composable
fun UniversalRemotesHomeScreen(navController: NavController) {
    val context = LocalContext.current
    val categories = remember { UniversalRemotesRepository.getCategories(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SectionTopBar(title = stringResource(R.string.ir_universal_remotes), onBack = { navController.popBackStack() }, transparent = true)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentPadding = PaddingValues(bottom = irListsBottomPadding),
            verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)
        ) {
            m3SegmentedItems(categories) { index, count, category ->
                M3SegmentedListItem(
                    index = index,
                    count = count,
                    headline = category.title,
                    supporting = category.description,
                    onClick = { navController.navigate("other/universal_remote/${category.id}") },
                )
            }
        }
    }
}

@Composable
fun IrFavoritesScreen(navController: NavController) {
    val context = LocalContext.current
    val favorites = IrFavoriteStore.favorites()
    var storeReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        IrFavoriteStore.ensureLoaded(context)
        storeReady = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionTopBar(
            title = stringResource(R.string.ir_favorites),
            onBack = { navController.popBackStack() },
            transparent = true
        )

        if (!storeReady) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                DolphyCircularProgressIndicator()
            }
        } else if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ir_no_favorites),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentPadding = PaddingValues(bottom = irListsBottomPadding),
                verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)
            ) {
                m3SegmentedItems(favorites, key = { it.key }) { index, count, favorite ->
                    M3SegmentedListItem(
                        index = index,
                        count = count,
                        headline = favorite.title,
                        supporting = favorite.subtitle.takeIf { it.isNotBlank() },
                        leadingIcon = Icons.Default.Star,
                        leadingIconTint = MaterialTheme.colorScheme.primary,
                        onClick = { navController.navigate(favorite.route) },
                    )
                }
            }
        }
    }
}

private fun brightenAccent(color: Color, amount: Float = 0.25f): Color {
    return Color(
        red = color.red + (1f - color.red) * amount,
        green = color.green + (1f - color.green) * amount,
        blue = color.blue + (1f - color.blue) * amount,
        alpha = 1f
    )
}

private fun actionIcon(action: UniversalSignalAction): ImageVector? {
    val key = action.signalName.lowercase()
    return when {
        key == "power" || key == "off" || key == "on" -> Icons.Default.Power
        key == "vol_up" -> Icons.Default.VolumeUp
        key == "vol_dn" -> Icons.Default.VolumeDown
        key == "ch_next" -> Icons.Default.KeyboardArrowUp
        key == "ch_prev" -> Icons.Default.KeyboardArrowDown
        key == "next" -> Icons.Default.SkipNext
        key == "prev" -> Icons.Default.SkipPrevious
        key == "play" -> Icons.Default.PlayArrow
        key == "pause" -> Icons.Default.Pause
        key == "mute" -> Icons.Default.VolumeMute
        else -> null
    }
}

@Composable
private fun UniversalActionButton(
    action: UniversalSignalAction,
    accent: Color,
    enabled: Boolean,
    onClick: (UniversalSignalAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val liquidBackdrop = LocalLiquidGlassBackdrop.current
    if (LocalLiquidGlassEnabled.current && liquidBackdrop != null && enabled) {
        com.kyant.backdrop.catalog.components.LiquidButton(
            onClick = { onClick(action) },
            backdrop = liquidBackdrop,
            modifier = modifier.heightIn(min = 58.dp).fillMaxWidth(),
            tint = accent,
            applyDefaultHeight = false,
            contentPaddingHorizontal = 12.dp,
        ) {
            val icon = actionIcon(action)
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                action.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.Black,
            )
        }
        return
    }
    Button(
        onClick = { onClick(action) },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.heightIn(min = 58.dp)
    ) {
        val icon = actionIcon(action)
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(action.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UniversalVerticalPair(
    top: UniversalSignalAction?,
    bottom: UniversalSignalAction?,
    accent: Color,
    enabled: Boolean,
    onClick: (UniversalSignalAction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (top != null && bottom != null) {
        MaterialCard(
            modifier = modifier.fillMaxWidth(),
            accentColor = accent,
            cornerRadius = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onClick(top) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actionIcon(top)?.let {
                        Icon(it, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(top.displayName, color = Color.White)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.35f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onClick(bottom) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actionIcon(bottom)?.let {
                        Icon(it, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(bottom.displayName, color = Color.White)
                }
            }
        }
    } else if (top != null || bottom != null) {
        val single = top ?: bottom ?: return
        UniversalActionButton(
            action = single,
            accent = accent,
            enabled = enabled,
            onClick = onClick,
            modifier = modifier.fillMaxWidth()
        )
    } else {
        Spacer(modifier = modifier.height(116.dp))
    }
}

@Composable
fun UniversalRemoteCategoryScreen(navController: NavController, categoryIdEnc: String) {
    val context = LocalContext.current
    val categoryId = Uri.decode(categoryIdEnc)
    val category = UniversalRemotesRepository.getCategory(context, categoryId)
    if (category == null) {
        SimpleMessageScreen(stringResource(R.string.ir_not_found))
        return
    }

    val scope = rememberCoroutineScope()
    var isSending by remember { mutableStateOf(false) }
    var totalSignals by remember { mutableStateOf(0) }
    var sentSignals by remember { mutableStateOf(0) }
    var sendingAction by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var sendingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var baseSignalCount by remember { mutableStateOf<Int?>(null) }
    var baseInfoMessage by remember { mutableStateOf("") }

    LaunchedEffect(category.id) {
        val count = withContext(Dispatchers.IO) {
            try {
                category.assetFiles.flatMap { IrRepository.loadButtons(context, it) }.size
            } catch (e: Exception) {
                Log.e("UniversalRemotes", "Failed to load base for ${category.id}: ${e.message}", e)
                -1
            }
        }
        baseSignalCount = count
        baseInfoMessage = when {
            count < 0 -> context.getString(R.string.universal_db_failed)
            count == 0 -> context.getString(R.string.universal_db_empty)
            else -> context.getString(R.string.universal_signal_count, count)
        }
        Log.i("UniversalRemotes", "Base count for ${category.id}: $count; files=${category.assetFiles}")
    }

    fun stopSending() {
        sendingJob?.cancel()
        sendingJob = null
        isSending = false
    }

    fun startSending(action: UniversalSignalAction) {
        isSending = true
        sendingAction = action.displayName
        totalSignals = 0
        sentSignals = 0
        statusMessage = context.getString(R.string.universal_loading_signals)

        sendingJob = scope.launch(Dispatchers.Default) {
            try {
                val signals = withContext(Dispatchers.IO) {
                    runCatching {
                        UniversalRemotesRepository.getSignalsForAction(context, category, action.signalName)
                    }.onFailure { err ->
                        Log.e(
                            "UniversalRemotes",
                            "Failed to load signals for ${category.id}/${action.signalName}: ${err.message}",
                            err
                        )
                    }.getOrElse { emptyList() }
                }
                if (signals.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isSending = false
                        val filesHint = if (category.assetFiles.isNotEmpty()) {
                            context.getString(R.string.universal_files_list, category.assetFiles.joinToString())
                        } else {
                            context.getString(R.string.universal_files_missing)
                        }
                        statusMessage = context.getString(
                            R.string.universal_no_signals_for,
                            action.displayName,
                            category.title,
                            filesHint
                        )
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    totalSignals = signals.size
                    sentSignals = 0
                    statusMessage = ""
                }
                for ((index, signal) in signals.withIndex()) {
                    withContext(Dispatchers.Main) {
                        sentSignals = index + 1
                    }
                    withContext(Dispatchers.IO) {
                        transmitIr(context, signal)
                    }
                    delay(20)
                }
                withContext(Dispatchers.Main) {
                    isSending = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSending = false
                    statusMessage = context.getString(
                        R.string.universal_error_unknown,
                        e.message ?: context.getString(R.string.universal_unknown)
                    )
                }
            }
        }
    }

    val actionsBySignal = category.actions.associateBy { it.signalName.lowercase() }
    val powerAction = actionsBySignal["power"] ?: actionsBySignal["off"] ?: actionsBySignal["on"]
    val leftTop = actionsBySignal["ch_next"] ?: actionsBySignal["cool_hi"] ?: actionsBySignal["prev"]
    val leftBottom = actionsBySignal["ch_prev"] ?: actionsBySignal["cool_lo"] ?: actionsBySignal["next"]
    val rightTop = actionsBySignal["vol_up"] ?: actionsBySignal["heat_hi"]
    val rightBottom = actionsBySignal["vol_dn"] ?: actionsBySignal["heat_lo"]

    val pairedSignals = setOfNotNull(
        powerAction?.signalName?.lowercase(),
        leftTop?.signalName?.lowercase(),
        leftBottom?.signalName?.lowercase(),
        rightTop?.signalName?.lowercase(),
        rightBottom?.signalName?.lowercase()
    )
    val remainingActions = category.actions.filter { it.signalName.lowercase() !in pairedSignals }
    val brightAccent = brightenAccent(MaterialTheme.colorScheme.primary)

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SectionTopBar(
            title = stringResource(R.string.ir_universal_remotes) + ": ${category.title}",
            onBack = { navController.popBackStack() },
            transparent = true,
            actions = {
                FavoriteRemoteButton(
                    IrFavoriteRemote(
                        key = "universal:${category.id}",
                        title = category.title,
                        subtitle = stringResource(R.string.ir_universal_remotes),
                        route = "other/universal_remote/${Uri.encode(category.id)}"
                    )
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (baseInfoMessage.isNotBlank()) {
                Text(
                    text = baseInfoMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (baseSignalCount == null || baseSignalCount == 0 || baseSignalCount == -1) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            powerAction?.let { action ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    UniversalActionButton(
                        action = action,
                        accent = brightAccent,
                        enabled = !isSending,
                        onClick = ::startSending,
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UniversalVerticalPair(
                    top = leftTop,
                    bottom = leftBottom,
                    accent = brightAccent,
                    enabled = !isSending,
                    onClick = ::startSending,
                    modifier = Modifier.weight(1f)
                )
                UniversalVerticalPair(
                    top = rightTop,
                    bottom = rightBottom,
                    accent = brightAccent,
                    enabled = !isSending,
                    onClick = ::startSending,
                    modifier = Modifier.weight(1f)
                )
            }

            if (remainingActions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(remainingActions.chunked(2)) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { action ->
                                UniversalActionButton(
                                    action = action,
                                    accent = brightAccent,
                                    enabled = !isSending,
                                    onClick = ::startSending,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(2 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (isSending) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.ir_sending)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TintedFrameAnimation(
                        frames = listOf(
                            R.drawable.mm_infrared_14_frame_01,
                            R.drawable.mm_infrared_14_frame_02,
                            R.drawable.mm_infrared_14_frame_03,
                            R.drawable.mm_infrared_14_frame_04,
                            R.drawable.mm_infrared_14_frame_05,
                            R.drawable.mm_infrared_14_frame_06
                        ),
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.CenterHorizontally),
                        frameDelayMs = 333L,
                        tintColor = MaterialTheme.colorScheme.primary,
                        contentDescription = "IR sending animation"
                    )
                    Text("$sendingAction: $sentSignals / $totalSignals")
                    DolphyLinearProgressIndicator(
                        progress = if (totalSignals > 0) sentSignals.toFloat() / totalSignals else 0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { stopSending() }) {
                    Text(stringResource(R.string.stop))
                }
            }
        )
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && it.moveToFirst()) {
            val value = it.getString(nameIndex)
            if (!value.isNullOrBlank()) return value
        }
    }
    return uri.lastPathSegment ?: "remote.ir"
}

@Composable
fun UserIrRemotesScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importError by remember { mutableStateOf<String?>(null) }
    var storeReady by remember { mutableStateOf(false) }
    val remotes = UserIrRemoteStore.remotes()
    var editMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var renameDialogVisible by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        UserIrRemoteStore.ensureLoaded(context)
        storeReady = true
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val fileName = resolveDisplayName(context, uri)
            if (!fileName.endsWith(".ir", ignoreCase = true)) {
                importError = context.getString(R.string.ir_import_select_ir)
                return@launch
            }
            val commands = withContext(Dispatchers.IO) {
                IrRepository.parseIrUri(context, uri)
            }
            if (commands.isEmpty()) {
                importError = context.getString(R.string.ir_import_failed)
                return@launch
            }
            UserIrRemoteStore.addRemote(context = context, fileName = fileName, commands = commands)
            importError = null
        }
    }

    val selectedSingle = selectedIds.singleOrNull()
    if (renameDialogVisible && selectedSingle != null) {
        AlertDialog(
            onDismissRequest = { renameDialogVisible = false },
            title = { Text(stringResource(R.string.ir_rename_remote)) },
            text = {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim()
                    if (newName.isNotEmpty()) {
                        val finalName = if (newName.endsWith(".ir", ignoreCase = true)) newName else "$newName.ir"
                        UserIrRemoteStore.renameRemote(context = context, id = selectedSingle, newFileName = finalName)
                    }
                    renameDialogVisible = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogVisible = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            SectionTopBar(
                title = stringResource(R.string.ir_user_remotes),
                onBack = { navController.popBackStack() },
                transparent = true,
                actions = {
                    DolphyIconButton(onClick = {
                        editMode = !editMode
                        selectedIds = emptySet()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.ir_edit_mode),
                            tint = Color.White
                        )
                    }
                    if (editMode) {
                        DolphyIconButton(
                            onClick = { UserIrRemoteStore.removeByIds(context = context, ids = selectedIds); selectedIds = emptySet() },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cd_delete),
                                tint = if (selectedIds.isNotEmpty()) Color.White else Color.Gray
                            )
                        }
                        DolphyIconButton(
                            onClick = {
                                val id = selectedSingle ?: return@DolphyIconButton
                                renameText = UserIrRemoteStore.getRemote(id)?.fileName ?: ""
                                renameDialogVisible = true
                            },
                            enabled = selectedSingle != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = stringResource(R.string.ir_rename),
                                tint = if (selectedSingle != null) Color.White else Color.Gray
                            )
                        }
                    }
                }
            )

            if (remotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (!storeReady) {
                        DolphyCircularProgressIndicator()
                    } else {
                        Text(
                            text = stringResource(R.string.ir_no_user_remotes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(remotes, key = { it.id }) { remote ->
                        MaterialCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = MaterialTheme.colorScheme.primary,
                            cornerRadius = 12.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (editMode) {
                                    Checkbox(
                                        checked = selectedIds.contains(remote.id),
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) {
                                                selectedIds + remote.id
                                            } else {
                                                selectedIds - remote.id
                                            }
                                        }
                                    )
                                }
                                Text(
                                    text = remote.fileName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .padding(end = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!editMode) {
                                    val liquidBd = LocalLiquidGlassBackdrop.current
                                    if (LocalLiquidGlassEnabled.current && liquidBd != null) {
                                        com.kyant.backdrop.catalog.components.LiquidButton(
                                            onClick = { navController.navigate("other/user_ir_remote/${remote.id}") },
                                            backdrop = liquidBd,
                                            modifier = Modifier
                                                .height(40.dp)
                                                .width(116.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                            applyDefaultHeight = false,
                                            contentPaddingHorizontal = 8.dp,
                                        ) {
                                            Text(
                                                stringResource(R.string.open),
                                                maxLines = 1,
                                                overflow = TextOverflow.Clip,
                                                color = Color.Black,
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = { navController.navigate("other/user_ir_remote/${remote.id}") },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .height(40.dp)
                                                .width(116.dp)
                                        ) {
                                            Text(stringResource(R.string.open), maxLines = 1, overflow = TextOverflow.Clip)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        if (importError != null) {
                            Text(
                                text = importError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        val liquidBackdrop = LocalLiquidGlassBackdrop.current
        if (LocalLiquidGlassEnabled.current && liquidBackdrop != null) {
            com.kyant.backdrop.catalog.components.LiquidButton(
                backdrop = liquidBackdrop,
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = 112.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.ir_import))
            }
        } else {
            FloatingActionButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 24.dp, bottom = 112.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.ir_import))
            }
        }
    }
}

@Composable
fun UserIrRemoteScreen(navController: NavController, remoteIdArg: String) {
    val context = LocalContext.current
    var storeReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        UserIrRemoteStore.ensureLoaded(context)
        storeReady = true
    }

    if (!storeReady) {
        LoadingScreen()
        return
    }

    val remoteId = remoteIdArg.toIntOrNull()
    val remote = if (remoteId == null) null else UserIrRemoteStore.getRemote(remoteId)

    if (remote == null) {
        SimpleMessageScreen(stringResource(R.string.ir_remote_not_found))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SectionTopBar(
            title = remote.fileName,
            onBack = { navController.popBackStack() },
            transparent = true,
            actions = {
                FavoriteRemoteButton(
                    IrFavoriteRemote(
                        key = "user:${remote.id}",
                        title = remote.fileName,
                        subtitle = stringResource(R.string.ir_user_remotes),
                        route = "other/user_ir_remote/${remote.id}"
                    )
                )
            }
        )
        IrRemoteControlLayout(
            buttons = remote.commands,
            metaLines = emptyList(),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun TVBrandScreen(navController: NavController, brandEnc: String) {
    val brand = Uri.decode(brandEnc)
    val context = LocalContext.current
    var models by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(brand) {
        withContext(Dispatchers.IO) {
            val tvIndex = IrRepository.getTvIndex(context)
            models = tvIndex[brand]?.keys?.sorted() ?: emptyList()
        }
    }

    if (models == null) {
        LoadingScreen()
        return
    }

    if (models!!.isEmpty()) {
        SimpleMessageScreen(stringResource(R.string.ir_remotes_for_brand_not_found, brand))
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SectionTopBar(title = brand, onBack = { navController.popBackStack() }, transparent = true)
        SimpleListScreen(items = models!!) { model ->
                        navController.navigate("other/tv_remote/${Uri.encode(brand)}/${Uri.encode(model)}")
        }
    }
}

@Composable
fun TVRemoteScreen(navController: NavController, brandEnc: String, remoteEnc: String) {
    val brand = Uri.decode(brandEnc)
    val model = Uri.decode(remoteEnc)
    val displayName = model
    val context = LocalContext.current
    val tvIndex = remember { IrRepository.getTvIndex(context) }
    val assetPath = remember(brand, model) { tvIndex[brand]?.get(model) }
    var buttons by remember { mutableStateOf<List<IrButton>?>(null) }

    LaunchedEffect(assetPath) {
        buttons = if (assetPath == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                val baseButtons = IrRepository.loadButtons(context, assetPath)
                val hasPower = baseButtons.any { isPower((it.name ?: "").lowercase()) }
                if (baseButtons.size >= 6 && hasPower) {
                    baseButtons
                } else {
                    val brandMap = tvIndex[brand].orEmpty()
                    val merged = LinkedHashMap<String, IrButton>()
                    fun keyFor(btn: IrButton): String {
                        return normalizeSearch(btn.name ?: "")
                    }
                    baseButtons.forEach { merged[keyFor(it)] = it }
                    for (path in brandMap.values) {
                        val list = IrRepository.loadButtons(context, path)
                        for (btn in list) {
                            val key = keyFor(btn)
                            if (key.isNotBlank() && !merged.containsKey(key)) {
                                merged[key] = btn
                            }
                        }
                    }
                    merged.values.toList().ifEmpty { baseButtons }
                }
            }
        }
    }

    if (buttons == null) {
        LoadingScreen()
        return
    }

    if (buttons!!.isEmpty()) {
        SimpleMessageScreen(stringResource(R.string.ir_no_commands_for, displayName))
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionTopBar(
            title = displayName,
            onBack = { navController.popBackStack() },
            transparent = true,
            actions = {
                FavoriteRemoteButton(
                    IrFavoriteRemote(
                        key = "tv:$brand:$model",
                        title = displayName,
                        subtitle = brand,
                        route = "other/tv_remote/${Uri.encode(brand)}/${Uri.encode(model)}"
                    )
                )
            }
        )

        IrRemoteControlLayout(
            buttons = buttons!!,
            metaLines = listOf(stringResource(R.string.ir_meta_brand, brand)),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun FlipperCategoryScreen(navController: NavController, catEnc: String) {
    val cat = Uri.decode(catEnc)
    val context = LocalContext.current
    val res = context.resources
    var brands by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(cat) {
        withContext(Dispatchers.IO) {
            val flIndex = IrRepository.getFlipperIndex(context)
            brands = flIndex.keys
                .filter { it.startsWith("$cat/") }
                .map { it.substringAfter("$cat/") }
                .distinct()
                .sorted()
        }
    }

    if (brands == null) {
        LoadingScreen()
        return
    }

    if (brands!!.isEmpty()) {
        SimpleMessageScreen(stringResource(R.string.ir_brands_not_found_category, localizeFlipperCategory(res, cat)))
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SectionTopBar(title = localizeFlipperCategory(res, cat), onBack = { navController.popBackStack() }, transparent = true)
        SimpleListScreen(items = brands!!) { brand ->
                        navController.navigate("other/flipper_brand/${Uri.encode(cat)}/${Uri.encode(brand)}")
        }
    }
}

@Composable
fun FlipperBrandScreen(navController: NavController, catEnc: String, brandEnc: String) {
    val cat = Uri.decode(catEnc)
    val brand = Uri.decode(brandEnc)
    val context = LocalContext.current
    var remotes by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(cat, brand) {
        withContext(Dispatchers.IO) {
            val flIndex = IrRepository.getFlipperIndex(context)
            val key = "$cat/$brand"
            remotes = flIndex[key]?.keys?.sorted() ?: emptyList()
        }
    }

    if (remotes == null) {
        LoadingScreen()
        return
    }

    if (remotes!!.isEmpty()) {
        SimpleMessageScreen(stringResource(R.string.ir_remotes_for_brand_not_found, brand))
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        SectionTopBar(title = brand, onBack = { navController.popBackStack() }, transparent = true)
        SimpleListScreen(items = remotes!!) { model ->
                        navController.navigate("other/flipper_remote/${Uri.encode(cat)}/${Uri.encode(brand)}/${Uri.encode(model)}")
        }
    }
}

@Composable
fun FlipperRemoteScreen(navController: NavController, catEnc: String, brandEnc: String, remoteEnc: String) {
    val cat = Uri.decode(catEnc)
    val brand = Uri.decode(brandEnc)
    val model = Uri.decode(remoteEnc)
    val displayName = model
    val context = LocalContext.current
    val flIndex = remember { IrRepository.getFlipperIndex(context) }
    val assetPath = remember(cat, brand, model) { flIndex["$cat/$brand"]?.get(model) }
    var buttons by remember { mutableStateOf<List<IrButton>?>(null) }

    LaunchedEffect(assetPath) {
        buttons = if (assetPath == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) { IrRepository.loadButtons(context, assetPath) }
        }
    }

    if (buttons == null) {
        LoadingScreen()
        return
    }

    if (buttons!!.isEmpty()) {
        SimpleMessageScreen(stringResource(R.string.ir_no_commands_for, displayName))
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionTopBar(
            title = displayName,
            onBack = { navController.popBackStack() },
            transparent = true,
            actions = {
                FavoriteRemoteButton(
                    IrFavoriteRemote(
                        key = "flipper:$cat:$brand:$model",
                        title = displayName,
                        subtitle = "$brand · ${localizeFlipperCategory(context.resources, cat)}",
                        route = "other/flipper_remote/${Uri.encode(cat)}/${Uri.encode(brand)}/${Uri.encode(model)}"
                    )
                )
            }
        )

        IrRemoteControlLayout(
            buttons = buttons!!,
            metaLines = listOf(
                stringResource(R.string.ir_meta_category_only, cat),
                stringResource(R.string.ir_meta_brand, brand)
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        )
    }
}

private data class IrRemoteGroups(
    val power: List<IrButton>,
    val dpadUp: IrButton?,
    val dpadDown: IrButton?,
    val dpadLeft: IrButton?,
    val dpadRight: IrButton?,
    val dpadOk: IrButton?,
    val volUp: IrButton?,
    val volDown: IrButton?,
    val channelUp: IrButton?,
    val channelDown: IrButton?,
    val mute: IrButton?,
    val numeric: List<IrButton>,
    val media: List<IrButton>,
    val navigation: List<IrButton>,
    val other: List<IrButton>,
    val weird: List<IrButton>
)

@Composable
private fun IrRemoteControlLayout(
    buttons: List<IrButton>,
    metaLines: List<String>,
    modifier: Modifier = Modifier
) {
    val bottomScrollPadding = 160.dp
    val context = LocalContext.current
    val res = context.resources
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val groups = remember(buttons) { classifyIrButtons(buttons) }
    val onPress: (IrButton) -> Unit = { btn ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vibrateRemoteButton(context)
        scope.launch(Dispatchers.IO) {
            transmitIr(context, btn)
        }
    }

    LazyColumn(
        modifier = modifier.padding(bottom = 16.dp),
        contentPadding = PaddingValues(bottom = bottomScrollPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (metaLines.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                ) {
                    metaLines.forEach { line ->
                        Text(text = line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (groups.power.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    groups.power.take(1).forEach { btn ->
                        RemoteIconButton(
                            button = btn,
                            icon = Icons.Default.Power,
                            onPress = onPress,
                            modifier = Modifier.size(74.dp)
                        )
                    }
                }
            }
        }

        if (groups.dpadUp != null || groups.dpadDown != null || groups.dpadLeft != null || groups.dpadRight != null || groups.dpadOk != null) {
            item {
                DpadSection(groups = groups, onPress = onPress)
            }
        }

        if (groups.volUp != null || groups.volDown != null || groups.channelUp != null || groups.channelDown != null || groups.mute != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groups.volUp?.let { RemoteTextButton(it, "VOL +", onPress, Modifier.fillMaxWidth()) }
                        groups.mute?.let { RemoteTextButton(it, "MUTE", onPress, Modifier.fillMaxWidth()) }
                        groups.volDown?.let { RemoteTextButton(it, "VOL -", onPress, Modifier.fillMaxWidth()) }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groups.channelUp?.let { RemoteTextButton(it, "CH +", onPress, Modifier.fillMaxWidth()) }
                        groups.channelDown?.let { RemoteTextButton(it, "CH -", onPress, Modifier.fillMaxWidth()) }
                    }
                }
            }
        }

        if (groups.numeric.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.ir_section_digits)) }
            items(groups.numeric.chunked(3)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { btn ->
                        RemoteTextButton(
                            button = btn,
                            label = prettyLabel(res, btn),
                            onPress = onPress,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (groups.navigation.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.ir_section_menu)) }
            items(groups.navigation.chunked(3)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { btn ->
                        RemoteAdaptiveButton(btn, onPress, Modifier.weight(1f))
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (groups.media.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.ir_section_media)) }
            items(groups.media.chunked(3)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { btn ->
                        RemoteAdaptiveButton(btn, onPress, Modifier.weight(1f))
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (groups.other.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.ir_section_other)) }
            items(groups.other.chunked(3)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { btn ->
                        RemoteAdaptiveButton(btn, onPress, Modifier.weight(1f))
                    }
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (groups.weird.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.ir_section_unknown_cmds)) }
            items(groups.weird.chunked(4)) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { btn ->
                        RemoteTextButton(
                            button = btn,
                            label = prettyLabel(res, btn),
                            onPress = onPress,
                            modifier = Modifier.weight(1f),
                            small = true
                        )
                    }
                    repeat(4 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DpadSection(groups: IrRemoteGroups, onPress: (IrButton) -> Unit) {
    val dpadOffset = 60.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(204.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.52f), CircleShape)
        )

        groups.dpadUp?.let {
            RemoteDpadButton(
                icon = Icons.Default.KeyboardArrowUp,
                button = it,
                onPress = onPress,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = -dpadOffset)
            )
        }
        groups.dpadDown?.let {
            RemoteDpadButton(
                icon = Icons.Default.KeyboardArrowDown,
                button = it,
                onPress = onPress,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = dpadOffset)
            )
        }
        groups.dpadLeft?.let {
            RemoteDpadButton(
                icon = Icons.Default.KeyboardArrowLeft,
                button = it,
                onPress = onPress,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = -dpadOffset)
            )
        }
        groups.dpadRight?.let {
            RemoteDpadButton(
                icon = Icons.Default.KeyboardArrowRight,
                button = it,
                onPress = onPress,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = dpadOffset)
            )
        }
        groups.dpadOk?.let {
            RemoteTextButton(
                button = it,
                label = "OK",
                onPress = onPress,
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center),
                rounded = true,
                small = false
            )
        }
    }
}

@Composable
private fun RemoteAdaptiveButton(
    button: IrButton,
    onPress: (IrButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val res = LocalContext.current.resources
    RemoteTextButton(
        button = button,
        label = prettyLabel(res, button),
        icon = iconForRemoteButton(button),
        onPress = onPress,
        modifier = modifier
    )
}

@Composable
private fun RemoteDpadButton(
    icon: ImageVector,
    button: IrButton,
    onPress: (IrButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val res = LocalContext.current.resources
    val accent = MaterialTheme.colorScheme.primary
    val liquidBackdrop = LocalLiquidGlassBackdrop.current
    if (LocalLiquidGlassEnabled.current && liquidBackdrop != null) {
        com.kyant.backdrop.catalog.components.LiquidButton(
            onClick = { onPress(button) },
            backdrop = liquidBackdrop,
            modifier = modifier.size(54.dp),
            tint = accent,
            applyDefaultHeight = false,
            contentPaddingHorizontal = 0.dp,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = prettyLabel(res, button),
                modifier = Modifier.size(30.dp),
                tint = Color.Black,
            )
        }
        return
    }
    Button(
        onClick = { onPress(button) },
        modifier = modifier.size(54.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = prettyLabel(res, button),
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun RemoteIconButton(
    button: IrButton,
    icon: ImageVector,
    onPress: (IrButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val res = LocalContext.current.resources
    val accent = MaterialTheme.colorScheme.primary
    val liquidBackdrop = LocalLiquidGlassBackdrop.current
    if (LocalLiquidGlassEnabled.current && liquidBackdrop != null) {
        com.kyant.backdrop.catalog.components.LiquidButton(
            onClick = { onPress(button) },
            backdrop = liquidBackdrop,
            modifier = modifier.size(54.dp),
            tint = accent,
            applyDefaultHeight = false,
            contentPaddingHorizontal = 0.dp,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = prettyLabel(res, button),
                tint = Color.Black,
            )
        }
        return
    }
    Button(
        onClick = { onPress(button) },
        modifier = modifier,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(imageVector = icon, contentDescription = prettyLabel(res, button))
    }
}

@Composable
private fun RemoteTextButton(
    button: IrButton,
    label: String,
    onPress: (IrButton) -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    rounded: Boolean = false,
    icon: ImageVector? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    val liquidBackdrop = LocalLiquidGlassBackdrop.current
    if (LocalLiquidGlassEnabled.current && liquidBackdrop != null) {
        com.kyant.backdrop.catalog.components.LiquidButton(
            onClick = { onPress(button) },
            backdrop = liquidBackdrop,
            modifier = modifier.heightIn(min = if (small) 38.dp else 50.dp),
            tint = accent.copy(alpha = if (small) 0.85f else 1f),
            applyDefaultHeight = false,
            contentPaddingHorizontal = if (small) 10.dp else 14.dp,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(if (small) 18.dp else 24.dp),
                    tint = Color.Black,
                )
            } else {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                    color = Color.Black,
                )
            }
        }
        return
    }
    Button(
        onClick = { onPress(button) },
        modifier = modifier.heightIn(min = if (small) 38.dp else 50.dp),
        shape = if (rounded) CircleShape else RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = if (small) 0.78f else 0.93f),
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(if (small) 18.dp else 24.dp)
            )
        } else {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

private fun classifyIrButtons(buttons: List<IrButton>): IrRemoteGroups {
    val power = mutableListOf<IrButton>()
    val numeric = mutableListOf<IrButton>()
    val media = mutableListOf<IrButton>()
    val navigation = mutableListOf<IrButton>()
    val other = mutableListOf<IrButton>()
    val weird = mutableListOf<IrButton>()

    var dpadUp: IrButton? = null
    var dpadDown: IrButton? = null
    var dpadLeft: IrButton? = null
    var dpadRight: IrButton? = null
    var dpadOk: IrButton? = null
    var volUp: IrButton? = null
    var volDown: IrButton? = null
    var channelUp: IrButton? = null
    var channelDown: IrButton? = null
    var mute: IrButton? = null

    buttons.forEach { btn ->
        val raw = (btn.name ?: "").trim()
        val lower = raw.lowercase()

        when {
            isPower(lower) -> power += btn
            volUp == null && isVolumeUp(lower) -> volUp = btn
            volDown == null && isVolumeDown(lower) -> volDown = btn
            channelUp == null && isChannelUp(lower) -> channelUp = btn
            channelDown == null && isChannelDown(lower) -> channelDown = btn
            mute == null && isMute(lower) -> mute = btn
            dpadUp == null && isDpadUp(lower) -> dpadUp = btn
            dpadDown == null && isDpadDown(lower) -> dpadDown = btn
            dpadLeft == null && isDpadLeft(lower) -> dpadLeft = btn
            dpadRight == null && isDpadRight(lower) -> dpadRight = btn
            dpadOk == null && isDpadOk(lower) -> dpadOk = btn
            isNumeric(lower) -> numeric += btn
            isMedia(lower) -> media += btn
            isNavigation(lower) -> navigation += btn
            isWeird(lower, raw) -> weird += btn
            else -> other += btn
        }
    }

    return IrRemoteGroups(
        power = power,
        dpadUp = dpadUp,
        dpadDown = dpadDown,
        dpadLeft = dpadLeft,
        dpadRight = dpadRight,
        dpadOk = dpadOk,
        volUp = volUp,
        volDown = volDown,
        channelUp = channelUp,
        channelDown = channelDown,
        mute = mute,
        numeric = numeric.sortedBy { normalizeNumericOrder(it.name ?: "") },
        media = media,
        navigation = navigation,
        other = other,
        weird = weird
    )
}

private fun prettyLabel(res: Resources, button: IrButton): String {
    val raw = button.name?.trim().orEmpty()
    if (raw.isBlank()) return res.getString(R.string.ir_button_unnamed)
    return raw
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun normalizeNumericOrder(name: String): Int {
    val low = name.lowercase()
    val m = Regex("([0-9])").find(low) ?: return Int.MAX_VALUE
    return m.value.toIntOrNull() ?: Int.MAX_VALUE
}

private fun containsAny(text: String, keys: List<String>): Boolean {
    return keys.any { text.contains(it) }
}

private fun isPower(lower: String): Boolean =
    containsAny(lower, listOf("power", "on_off", "on/off", "standby", "выкл", "off"))

private fun isVolumeUp(lower: String): Boolean =
    containsAny(lower, listOf("vol+", "volume+", "volume_up", "vol_up", "vol up", "v+"))

private fun isVolumeDown(lower: String): Boolean =
    containsAny(lower, listOf("vol-", "volume-", "volume_down", "vol_down", "vol down", "v-"))

private fun isChannelUp(lower: String): Boolean =
    containsAny(lower, listOf("ch+", "channel+", "channel_up", "ch_up", "prog+", "p+"))

private fun isChannelDown(lower: String): Boolean =
    containsAny(lower, listOf("ch-", "channel-", "channel_down", "ch_down", "prog-", "p-"))

private fun isMute(lower: String): Boolean =
    containsAny(lower, listOf("mute", "silence"))

private fun isDpadUp(lower: String): Boolean =
    lower == "up" || containsAny(lower, listOf("arrow_up", "cursor_up", "key_up", " up"))

private fun isDpadDown(lower: String): Boolean =
    lower == "down" || containsAny(lower, listOf("arrow_down", "cursor_down", "key_down", " down"))

private fun isDpadLeft(lower: String): Boolean =
    lower == "left" || containsAny(lower, listOf("arrow_left", "cursor_left", "key_left", " left"))

private fun isDpadRight(lower: String): Boolean =
    lower == "right" || containsAny(lower, listOf("arrow_right", "cursor_right", "key_right", " right"))

private fun isDpadOk(lower: String): Boolean =
    containsAny(lower, listOf("ok", "enter", "select", "confirm", "center", "centre"))

private fun isNumeric(lower: String): Boolean {
    val compact = lower.replace("_", "").replace("-", "").replace(" ", "")
    if (compact.length == 1 && compact[0].isDigit()) return true
    return Regex("^(num|digit|key)[0-9]$").matches(compact)
}

private fun isMedia(lower: String): Boolean =
    containsAny(lower, listOf("play", "pause", "stop", "rew", "ff", "forward", "backward", "next", "prev", "record"))

private fun isNavigation(lower: String): Boolean =
    containsAny(lower, listOf("menu", "home", "back", "return", "exit", "guide", "input", "source", "info", "list"))

private fun isWeird(lower: String, raw: String): Boolean {
    if (raw.isBlank()) return true
    return containsAny(lower, listOf("weird", "unknown", "unnamed", "custom_", "btn_"))
}

private fun iconForRemoteButton(button: IrButton): ImageVector? {
    val lower = button.name?.trim()?.lowercase().orEmpty()
    return when {
        containsAny(lower, listOf("menu")) -> Icons.Default.Menu
        containsAny(lower, listOf("home")) -> Icons.Default.Home
        containsAny(lower, listOf("back", "return")) -> Icons.AutoMirrored.Filled.ArrowBack
        containsAny(lower, listOf("info")) -> Icons.Default.Info
        containsAny(lower, listOf("list", "guide")) -> Icons.Default.ListIcon
        containsAny(lower, listOf("ok", "enter", "select", "confirm", "center", "centre")) -> Icons.Default.Check
        containsAny(lower, listOf("play")) -> Icons.Default.PlayArrow
        containsAny(lower, listOf("pause")) -> Icons.Default.Pause
        containsAny(lower, listOf("stop")) -> Icons.Default.Stop
        containsAny(lower, listOf("rew", "rewind", "backward")) -> Icons.Default.FastRewind
        containsAny(lower, listOf("ff", "forward")) -> Icons.Default.FastForward
        containsAny(lower, listOf("next")) -> Icons.Default.SkipNext
        containsAny(lower, listOf("prev", "previous")) -> Icons.Default.SkipPrevious
        containsAny(lower, listOf("mute")) -> Icons.Default.VolumeMute
        containsAny(lower, listOf("settings", "setup", "option", "options")) -> Icons.Default.Settings
        else -> null
    }
}

private fun vibrateRemoteButton(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (!vibrator.hasVibrator()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(24, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(24)
    }
}

@Composable
private fun SimpleListScreen(
    items: List<String>,
    bottomPadding: Dp = irListsBottomPadding,
    onItemClick: (String) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { value ->
                MaterialCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(value) },
                    accentColor = accent,
                    cornerRadius = 12.dp
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleMessageScreen(message: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}


data class IrStormCommand(
    val name: String,
    val frequency: Int,
    val pattern: IntArray
)

private const val IR_STORM_TV_ALL_KEY = "__TV_ALL__"

object IrStormRepository {

    fun getTvAssetPaths(context: Context): List<String> {
        val flIndex = IrRepository.getFlipperIndex(context)
        val paths = mutableListOf<String>()
        for ((category, models) in flIndex) {
            if (!category.lowercase().contains("tv") && !category.lowercase().contains("television")) {
                continue
            }
            paths.addAll(models.values)
        }
        return paths
    }

    fun pickShutdownButton(buttons: List<IrButton>): IrButton? {
        for (btn in buttons) {
            val label = btn.name?.lowercase() ?: continue
            if (label.contains("power") || label.contains("выкл") || label.contains("off")) {
                return btn
            }
        }
        return buttons.firstOrNull()
    }

    fun getSelectableCategories(context: Context): List<String> {
        val res = context.resources
        val roots = IrRepository.getFlipperIndex(context)
            .keys
            .map { it.substringBefore('/') }
            .distinct()
            .sortedBy { localizeFlipperCategory(res, it) }
        return listOf(IR_STORM_TV_ALL_KEY) + roots
    }

    fun categoryLabel(context: Context, categoryKey: String): String {
        val res = context.resources
        return if (categoryKey == IR_STORM_TV_ALL_KEY) res.getString(R.string.ir_tv_short) else localizeFlipperCategory(res, categoryKey.substringBefore('/'))
    }

    private fun isTvCategory(category: String): Boolean {
        val lower = category.lowercase()
        return lower.contains("tv") || lower.contains("television")
    }

    private fun isPowerFamily(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("power") || lower.contains("off") || lower.contains("on") || lower.contains("выкл") || lower.contains("вкл")
    }

    fun buildCommandsForCategory(context: Context, categoryKey: String): List<Pair<String, IrButton>> {
        val flIndex = IrRepository.getFlipperIndex(context)
        if (categoryKey == IR_STORM_TV_ALL_KEY) {
            val commands = mutableListOf<Pair<String, IrButton>>()
            for ((category, models) in flIndex) {
                if (!isTvCategory(category)) continue
                for ((modelName, assetPath) in models) {
                    val buttons = IrRepository.loadButtons(context, assetPath)
                    val picked = pickShutdownButton(buttons) ?: continue
                    commands += "$modelName • ${picked.name ?: "Power"}" to picked
                }
            }
            return commands
        }

        val categoryRoot = categoryKey.substringBefore('/')
        val categoryModels = linkedMapOf<String, String>()
        for ((key, modelMap) in flIndex) {
            val root = key.substringBefore('/')
            if (root != categoryRoot) continue
            for ((modelName, assetPath) in modelMap) {
                categoryModels["$root/$modelName"] = assetPath
            }
        }
        if (categoryModels.isEmpty()) return emptyList()
        val allButtons = mutableListOf<Pair<String, IrButton>>()
        for ((modelName, assetPath) in categoryModels) {
            val buttons = IrRepository.loadButtons(context, assetPath)
            val pickedButtons = buttons.filter { btn ->
                val name = btn.name ?: return@filter false
                isPowerFamily(name)
            }
            val finalButtons = if (pickedButtons.isNotEmpty()) pickedButtons else listOfNotNull(pickShutdownButton(buttons))
            for (btn in finalButtons) {
                allButtons += "$modelName • ${btn.name ?: "signal"}" to btn
            }
        }


        if (allButtons.size < 20) {
            val allSignals = mutableListOf<Pair<String, IrButton>>()
            for ((modelName, assetPath) in categoryModels) {
                val buttons = IrRepository.loadButtons(context, assetPath)
                for (btn in buttons) {
                    allSignals += "$modelName • ${btn.name ?: "signal"}" to btn
                }
            }
            return allSignals
        }

        return allButtons
    }




    fun countTargetsForCategory(context: Context, categoryKey: String): Int {
        val flIndex = IrRepository.getFlipperIndex(context)
        if (categoryKey == IR_STORM_TV_ALL_KEY) {
            return flIndex.filterKeys { isTvCategory(it) }.values.sumOf { it.size }
        }
        val root = categoryKey.substringBefore('/')
        return flIndex.filterKeys { it.substringBefore('/') == root }.values.sumOf { it.size }
    }




    fun forEachTarget(
        context: Context,
        categoryKey: String,
        onTarget: (displayName: String, btn: IrButton) -> Boolean,
    ) {
        val flIndex = IrRepository.getFlipperIndex(context)
        if (categoryKey == IR_STORM_TV_ALL_KEY) {
            for ((category, models) in flIndex) {
                if (!isTvCategory(category)) continue
                for ((modelName, assetPath) in models) {
                    val buttons = IrRepository.loadButtons(context, assetPath)
                    val picked = pickShutdownButton(buttons) ?: continue
                    if (!onTarget("$modelName • ${picked.name ?: "Power"}", picked)) return
                }
            }
            return
        }
        val categoryRoot = categoryKey.substringBefore('/')
        val categoryModels = linkedMapOf<String, String>()
        for ((key, modelMap) in flIndex) {
            if (key.substringBefore('/') != categoryRoot) continue
            for ((modelName, assetPath) in modelMap) {
                categoryModels["$categoryRoot/$modelName"] = assetPath
            }
        }

        var powerHits = 0
        for ((modelName, assetPath) in categoryModels) {
            val buttons = IrRepository.loadButtons(context, assetPath)
            val picked = buttons.filter { isPowerFamily(it.name ?: "") }
                .ifEmpty { listOfNotNull(pickShutdownButton(buttons)) }
            for (btn in picked) {
                powerHits++
                if (!onTarget("$modelName • ${btn.name ?: "signal"}", btn)) return
            }
        }

        if (powerHits < 20) {
            for ((modelName, assetPath) in categoryModels) {
                val buttons = IrRepository.loadButtons(context, assetPath)
                for (btn in buttons) {
                    if (isPowerFamily(btn.name ?: "")) continue
                    if (!onTarget("$modelName • ${btn.name ?: "signal"}", btn)) return
                }
            }
        }
    }
}


@Composable
fun IrExpressivePowerButton(
    active: Boolean,
    progress: Float?,
    accentColor: Color,
    activeTint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            WavyCircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                progress = progress,
                color = accentColor,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size * 0.62f)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.22f)),
            )
        }

        DolphyIconButton(
            onClick = onClick,
            enabled = enabled,
            liquidTint = accentColor,
            modifier = Modifier
                .size(size * 0.62f)
                .clip(CircleShape)
                .background(
                    if (active) activeTint.copy(alpha = 0.18f) else Color.Transparent,
                ),
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(size * 0.35f),
                tint = when {
                    isLiquidGlassChrome() -> Color.Black
                    active -> activeTint
                    else -> Color.White
                },
            )
        }
    }
}

@Composable
fun IRStormScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val accentColor = MaterialTheme.colorScheme.primary

    var isRunning by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var currentCommand by remember { mutableStateOf<IrStormCommand?>(null) }
    var progress by remember { mutableStateOf(0) }
    var totalCommands by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf(context.getString(R.string.ir_storm_ready)) }
    var stormJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var selectedCategory by remember { mutableStateOf(IR_STORM_TV_ALL_KEY) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var categoryOptions by remember { mutableStateOf(listOf(IR_STORM_TV_ALL_KEY)) }

    LaunchedEffect(Unit) {
        statusMessage = context.getString(R.string.ir_storm_ready)
        categoryOptions = withContext(Dispatchers.IO) {
            IrStormRepository.getSelectableCategories(context)
        }
    }


    fun doIrStorm() {
        if (stormJob != null) return
        isRunning = true
        isLoading = false
        progress = 0
        totalCommands = 0
        currentCommand = null
        statusMessage = context.getString(R.string.ir_storm_sending)

        stormJob = scope.launch(Dispatchers.IO) {
            try {
                val estimated = IrStormRepository.countTargetsForCategory(context, selectedCategory)
                withContext(Dispatchers.Main) {
                    totalCommands = estimated.coerceAtLeast(1)
                }
                var index = 0
                IrStormRepository.forEachTarget(context, selectedCategory) { displayName, btn ->
                    if (!isRunning) return@forEachTarget false
                    index++
                    val i = index

                    scope.launch(Dispatchers.Main) {
                        progress = i
                        if (totalCommands < i) totalCommands = i
                        currentCommand = IrStormCommand(displayName, btn.frequency, btn.pattern)
                    }
                    transmitIr(context, btn)
                    Thread.sleep(8)
                    isRunning
                }
                withContext(Dispatchers.Main) {
                    isRunning = false
                    isLoading = false
                    statusMessage = context.getString(R.string.ir_storm_done)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isRunning = false
                    isLoading = false
                    statusMessage = context.getString(
                        R.string.ir_storm_error,
                        e.message ?: context.getString(R.string.universal_unknown)
                    )
                }
            } finally {
                stormJob = null
            }
        }
    }


    fun startIrStorm() {
        doIrStorm()
    }


    fun stopIrStorm() {
        Log.i("IrStorm", "Stopping IR storm...")
        isRunning = false
        statusMessage = context.getString(R.string.ir_storm_stopped)
        stormJob?.cancel()
        stormJob = null
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        SectionTopBar(
            title = stringResource(R.string.ir_storm),
            onBack = if (navController != null) ({ navController.popBackStack() }) else null,
            transparent = true
        )


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(accentColor.copy(alpha = 0.12f))
                .clickable {
                    if (!isRunning && !isLoading) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        categoryMenuExpanded = !categoryMenuExpanded
                    }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = IrStormRepository.categoryLabel(context, selectedCategory),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Icon(
                imageVector = if (categoryMenuExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = accentColor
            )
        }

        if (categoryMenuExpanded) {
            ExpressiveSegmentedCardList(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    itemsIndexed(categoryOptions) { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCategory = option
                                    categoryMenuExpanded = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = IrStormRepository.categoryLabel(context, option),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (option == selectedCategory) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (option == selectedCategory) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = accentColor
                                )
                            }
                        }
                        if (index < categoryOptions.size - 1) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp).height(0.5.dp).fillMaxWidth().background(MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                }
            }
        }


        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(32.dp))


            IrExpressivePowerButton(
                active = isRunning || isLoading,
                progress = if (totalCommands > 0) progress.toFloat() / totalCommands else null,
                accentColor = accentColor,
                activeTint = Color.Red,
                enabled = !isLoading || isRunning,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isRunning) stopIrStorm() else startIrStorm()
                },
            )

            Spacer(modifier = Modifier.height(48.dp))


            DolphyLinearProgressIndicator(
                progress = if (totalCommands > 0) progress.toFloat() / totalCommands else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = accentColor,
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(16.dp))


            Text(
                text = "$progress / $totalCommands",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))


            if (currentCommand != null) {
                Text(
                    text = stringResource(R.string.ir_current_signal),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = currentCommand!!.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))


            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = accentColor
            )
        }
    }
}

@Composable
fun IRJammerScreen(navController: NavController? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accentColor = MaterialTheme.colorScheme.primary
    val jammerAssetPath = "Flipper-IRDB-main/ИК глушилка/IRjammer.ir"

    var buttons by remember { mutableStateOf<List<IrButton>?>(null) }
    var sourceLabel by remember { mutableStateOf("IRjammer.ir") }
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {

            val desktop = File("C:/Users/loush/projects/Dolphy/Flipper/IRjammer.ir")
            val fromFile = if (desktop.isFile) IrRepository.parseIrFile(desktop) else emptyList()
            if (fromFile.isNotEmpty()) {
                desktop.name to fromFile
            } else {
                jammerAssetPath.substringAfterLast('/') to IrRepository.loadButtons(context, jammerAssetPath)
            }
        }
        sourceLabel = result.first
        buttons = result.second
    }

    var isSending by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf(context.getString(R.string.ir_storm_ready)) }
    var sentCommands by remember { mutableStateOf(0) }
    var progressPercent by remember { mutableStateOf(0) }
    var jammerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionTopBar(title = stringResource(R.string.ir_jammer), onBack = if (navController != null) ({ navController.popBackStack() }) else null, transparent = true)
        Text(
            text = stringResource(R.string.ir_jammer_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        if (buttons == null) {
            LoadingScreen()
            return@Column
        }

        if (buttons!!.isEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.ir_file_not_found, sourceLabel),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(1.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IrExpressivePowerButton(
                        active = isSending,
                        progress = if (isSending) progressPercent / 100f else null,
                        accentColor = accentColor,
                        activeTint = accentColor,
                        enabled = buttons!!.isNotEmpty(),
                        onClick = {
                            if (isSending) {
                                jammerJob?.cancel()
                                jammerJob = null
                                isSending = false
                                statusMessage = context.getString(R.string.ir_storm_stopped)
                            } else if (buttons!!.isNotEmpty()) {
                                isSending = true
                                statusMessage = context.getString(R.string.ir_jammer_sending_signals)
                                sentCommands = 0
                                progressPercent = 0
                                jammerJob = scope.launch(Dispatchers.Default) {
                                    try {
                                        val list = buttons!!
                                        val total = list.size.coerceAtLeast(1)
                                        var cycle = 0

                                        while (kotlinx.coroutines.currentCoroutineContext().isActive && isSending) {
                                            cycle++
                                            for ((index, btn) in list.withIndex()) {
                                                if (!kotlinx.coroutines.currentCoroutineContext().isActive || !isSending) break
                                                withContext(Dispatchers.IO) {
                                                    transmitIr(context, btn)
                                                }
                                                withContext(Dispatchers.Main) {
                                                    sentCommands = (cycle - 1) * total + index + 1
                                                    progressPercent = (((index + 1) * 100f) / total).toInt().coerceIn(0, 100)
                                                    statusMessage = context.getString(R.string.ir_jammer_sending_signals) +
                                                        " · цикл $cycle"
                                                }
                                                delay(8)
                                            }
                                        }
                                    } catch (_: kotlinx.coroutines.CancellationException) {
                                    } finally {
                                        withContext(Dispatchers.Main) {
                                            isSending = false
                                            jammerJob = null
                                            statusMessage = context.getString(R.string.ir_storm_stopped)
                                        }
                                    }
                                }
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = accentColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "$sentCommands / ${buttons!!.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DolphyLinearProgressIndicator(
                    progress = progressPercent / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = accentColor,
                    trackColor = Color.Gray.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
        }
    }
}





