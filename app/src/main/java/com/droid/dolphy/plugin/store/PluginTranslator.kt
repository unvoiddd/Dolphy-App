package com.droid.dolphy.plugin.store

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap


object PluginTranslator {
    private const val TAG = "PluginTranslator"
    private const val PREFS = "plugin_translate"
    private const val KEY_CACHE = "cache_v2"
    private const val KEY_LEGACY_CACHE = "cache"
    private const val MAX_BODY = 2 * 1024 * 1024

    private const val MYMEMORY_CHUNK = 250

    private const val GOOGLE_MAX_CHARS = 1500

    private const val REQUEST_PACE_MS = 300L

    private const val FAILURE_RETRY_MS = 60_000L

    private lateinit var appContext: Context

    private val cache = ConcurrentHashMap<String, String>()

    private val failures = ConcurrentHashMap<String, Long>()

    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<String?>>()

    private val requestMutex = Mutex()

    @Volatile private var initialized = false

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true
        if (prefs().contains(KEY_LEGACY_CACHE)) {
            prefs().edit().remove(KEY_LEGACY_CACHE).apply()
        }
        loadPersistedCache()
    }

    private fun loadPersistedCache() {
        runCatching {
            val raw = prefs().getString(KEY_CACHE, null) ?: return
            val obj = JSONObject(raw)
            obj.keys().forEach { key -> cache[key] = obj.optString(key) }
        }.onFailure { Log.w(TAG, "translate cache restore failed", it) }
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun deviceLang(): String =
        if (Locale.getDefault().language.equals("ru", ignoreCase = true)) "ru" else "en"

    fun cachedOnly(text: String, target: String = deviceLang()): String? {
        if (text.isBlank()) return null
        return cache["$target|${text.trim()}"]
    }

    suspend fun translate(text: String, target: String = deviceLang()): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        val key = "$target|$trimmed"
        cache[key]?.let { return it }

        inFlight[key]?.let { return runCatching { it.await() }.getOrNull() }
        val deferred = CompletableDeferred<String?>()
        val raced = inFlight.putIfAbsent(key, deferred)
        if (raced != null) return runCatching { raced.await() }.getOrNull()

        try {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requestMutex.withLock {
                        cache[key]?.let { return@withLock it }
                        val failedAt = failures[key]
                        if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_RETRY_MS) {
                            return@withLock null
                        }
                        delay(REQUEST_PACE_MS)
                        val translated = runCatching { translateViaMyMemory(trimmed, target) }
                            .getOrNull()
                            ?: runCatching { translateViaGoogle(trimmed, target) }.getOrNull()
                        if (translated.isNullOrBlank()) {
                            failures[key] = System.currentTimeMillis()
                        } else {
                            failures.remove(key)
                            cache[key] = translated
                            persistCache()
                        }
                        translated
                    }
                }
            }.getOrElse {
                Log.w(TAG, "translate failed", it)
                null
            }
            deferred.complete(result)
            return result
        } finally {
            inFlight.remove(key)
        }
    }

    private fun translateViaMyMemory(text: String, target: String): String? {
        val chunks = chunkedForTranslation(text, MYMEMORY_CHUNK)
        val parts = ArrayList<String?>(chunks.size)
        chunks.forEachIndexed { index, chunk ->
            if (index > 0) Thread.sleep(REQUEST_PACE_MS)
            val url = "https://api.mymemory.translated.net/get" +
                "?q=${encode(chunk)}&langpair=Autodetect|${encode(target)}"
            val obj = JSONObject(httpGet(url))
            val translated = obj.optJSONObject("responseData")?.optString("translatedText").orEmpty()
            val ok = obj.optInt("responseStatus", -1) in 200..299 &&
                translated.isNotBlank() &&
                !translated.startsWith("MYMEMORY WARNING")
            parts.add(if (ok) translated else null)
        }
        if (parts.any { it == null }) return null
        return parts.joinToString(" ").trim().ifBlank { null }
    }

    private fun translateViaGoogle(text: String, target: String): String? {
        val q = if (text.length > GOOGLE_MAX_CHARS) text.take(GOOGLE_MAX_CHARS) else text
        val url = "https://translate.googleapis.com/translate_a/single" +
            "?client=gtx&sl=auto&tl=${encode(target)}&dt=t&ie=UTF-8&oe=UTF-8&q=${encode(q)}"
        val arr = JSONArray(httpGet(url))
        val segments = arr.optJSONArray(0) ?: return null
        val sb = StringBuilder()
        for (i in 0 until segments.length()) {
            sb.append(segments.optJSONArray(i)?.optString(0).orEmpty())
        }
        return sb.toString().trim().ifBlank { null }
    }

    private fun chunkedForTranslation(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        for (word in text.split(' ')) {
            if (cur.isNotEmpty() && cur.length + 1 + word.length > max) {
                out.add(cur.toString())
                cur.setLength(0)
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(word)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun persistCache() {
        runCatching {
            val obj = JSONObject()
            cache.forEach { (k, v) -> obj.put(k, v) }
            prefs().edit().putString(KEY_CACHE, obj.toString()).apply()
        }
    }


    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", "Dolphy-PluginTranslator")
        }
        return readResponse(conn)
    }

    private fun readResponse(conn: HttpURLConnection): String {
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val out = ByteArrayOutputStream()
            stream?.use { input ->
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    total += r
                    if (total > MAX_BODY) break
                    out.write(buf, 0, r)
                }
            }
            val text = out.toByteArray().toString(Charsets.UTF_8)
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
