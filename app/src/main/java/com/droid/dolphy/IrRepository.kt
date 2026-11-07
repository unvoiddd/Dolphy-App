package com.droid.dolphy

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.size

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
