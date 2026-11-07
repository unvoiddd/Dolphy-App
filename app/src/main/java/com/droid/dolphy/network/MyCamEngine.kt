package com.droid.dolphy.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URI
import java.net.URL
import java.util.Base64
import java.util.Locale

/**
 */
object MyCamEngine {

    sealed interface Status {
        data object Searching : Status
        data class Requesting(val ip: String) : Status
        data object CheckingPassword : Status
        data object NoReply : Status
        data object NextDevice : Status
    }

    sealed interface CameraTarget {
        data class Mjpeg(val url: String, val authHeader: String?) : CameraTarget
        data class Snapshot(val url: String, val authHeader: String?) : CameraTarget
        data class Rtsp(val url: String) : CameraTarget
    }

    data class ScanResult(
        val target: CameraTarget?,
        val cameraSeen: Boolean,
        val hostsResponded: Int,
    )

    data class Credentials(val user: String, val password: String) {
        val basicHeader: String
            get() = "Basic " + Base64.getEncoder()
                .encodeToString("$user:$password".toByteArray(Charsets.UTF_8))
        val label: String
            get() = "$user / ${if (password.isEmpty()) "(empty password)" else password}"
    }

    val CREDENTIALS = listOf(
        Credentials("admin", ""),
        Credentials("admin", "123456"),
        Credentials("admin", "admin"),
        Credentials("admin", "12345"),
        Credentials("admin", "888888"),
        Credentials("admin", "password"),
    )

    private const val PORT_TIMEOUT_MS = 600
    private const val HTTP_TIMEOUT_MS = 5000
    private const val SCAN_PARALLELISM = 32

    private val HTTP_PORTS = listOf(80, 8080, 81, 88, 8000, 8081, 8001)
    private val RTSP_PORTS = listOf(554, 8554, 10554)

    private val MJPEG_PATHS = listOf(
        "/mjpg/video.mjpg", "/video.mjpg", "/mjpeg/video.mjpg", "/videostream.cgi",
        "/cgi-bin/videostream.cgi", "/video.cgi", "/webcam.mjpg", "/cam.mjpg",
        "/mjpeg/1", "/mjpeg", "/stream/video/mjpeg", "/live/video", "/video",
        "/api/video.mjpg", "/webcam.cgi?action=stream", "/cgi-bin/mjpeg",
        "/videostream", "/stream.mjpg", "/jview.htm", "/cmd?vjpeg",
    )

    private val SNAPSHOT_PATHS = listOf(
        "/snapshot.cgi", "/image/jpeg.cgi", "/webcam.jpg", "/snapshot.jpg",
        "/jpg/image.jpg", "/cgi-bin/snapshot.cgi", "/onvif-http/snapshot",
        "/tmpfs/snap.jpg", "/api/snapshot", "/webcapture.jpg?command=snap&channel=1",
        "/cgi-bin/jpg/image.cgi", "/snap.jpg", "/image.jpg", "/tmpfs/auto.jpg",
    )

    private val RTSP_PATHS = listOf(
        "/", "/h264", "/stream1", "/stream", "/live/ch1", "/live/main", "/ch0_0.h264",
        "/cam/realmonitor?channel=1&subtype=0", "/Streaming/Channels/101",
        "/live/ch00_0", "/h264_stream", "/media.amp", "/onvif1", "/live",
    )

    private val CAMERA_KEYWORDS = listOf(
        "ip camera", "ipcam", "webcam", "netcam", "network camera", "camera web",
        "hikvision", "dahua", "foscam", "vivotek", "reolink", "amcrest", "onvif",
        "rtsp", "mjpeg", "videostream", "surveillance", "cctv", "wyze", "ezviz",
        "imou", "hiseeu", "sv3c", "video server", "dvr", "nvr", "камера",
    )

    private val ROUTER_MARKERS = listOf(
        "openwrt", "dd-wrt", "маршрутизатор", "wireless router", "adsl", "sagemcom",
        "keenetic", "routerlogin", "tplinkwifi", "miwifi", "pppoe", "wan settings",
    )

    /**
     */
    suspend fun findCamera(
        onStatus: (Status) -> Unit,
        onLog: (String) -> Unit,
        forceAll: Boolean = false,
    ): ScanResult =
        withContext(Dispatchers.IO) { findCameraInternal(onStatus, onLog, forceAll) }

    private suspend fun findCameraInternal(
        onStatus: (Status) -> Unit,
        onLog: (String) -> Unit,
        forceAll: Boolean,
    ): ScanResult {
        var cameraSeen = false
        var hostsResponded = 0

        val localIp = localIpv4()
        if (localIp == null) {
            onLog("Failed to determine local IP — check the Wi-Fi connection")
            return ScanResult(null, false, 0)
        }
        val (addr, ifName) = localIp
        val prefix = addr.hostAddress?.substringBeforeLast('.')
        if (prefix == null) {
            onLog("Failed to determine the subnet")
            return ScanResult(null, false, 0)
        }
        onLog("Local IP: ${addr.hostAddress} ($ifName)")
        onLog("Subnet: $prefix.0/24, trying ${CREDENTIALS.size} default passwords")
        if (forceAll) onLog("Mode: checking all network devices")

        onStatus(Status.Searching)
        val openPorts = scanSubnet(prefix, onLog)
        if (openPorts.isEmpty()) {
            onLog("No devices with open ports found after two passes")
            onLog("Check that the phone is on the same Wi-Fi network as the cameras and that client isolation (AP isolation) is disabled")
            return ScanResult(null, false, 0)
        }
        onLog("Devices with open ports found: ${openPorts.size}")
        openPorts.forEach { (ip, ports) ->
            onLog("$ip: open ports ${ports.sorted().joinToString()}")
        }

        val hosts = openPorts.entries
            .map { it.key to it.value }
            .sortedWith(
                compareByDescending<Pair<String, List<Int>>> { it.second.any(HTTP_PORTS::contains) }
                    .thenBy { it.second.firstOrNull(HTTP_PORTS::contains) ?: 999 }
            )

        for ((ip, ports) in hosts) {
            onStatus(Status.Requesting(ip))
            val httpPort = ports.firstOrNull(HTTP_PORTS::contains)

            if (httpPort != null) {
                val baseUrl = "http://$ip:$httpPort"
                val page = httpGet("$baseUrl/", authHeader = null)
                if (page == null) {
                    onStatus(Status.NoReply)
                    onLog("$ip: no response to the request")
                    onStatus(Status.NextDevice)
                    continue
                }
                hostsResponded++
                if (!forceAll && !detectCamera(page)) {
                    onLog("$ip: device responded but does not look like a camera")
                    onStatus(Status.NextDevice)
                    continue
                }
                cameraSeen = true
                if (forceAll) {
                    onLog("$ip: forcing device check (HTTP $httpPort)")
                } else {
                    onLog("$ip: found a web camera (HTTP $httpPort)")
                }
                onStatus(Status.CheckingPassword)

                val target = probeCamera(ip, ports, baseUrl, page, onLog)
                if (target != null) return ScanResult(target, true, hostsResponded)

                onLog("$ip: failed to get video — no password worked or no stream found")
                onStatus(Status.NextDevice)
            } else {
                val rtspPort = ports.firstOrNull(RTSP_PORTS::contains) ?: continue
                cameraSeen = true
                onLog("$ip: RTSP port $rtspPort is open")
                onStatus(Status.CheckingPassword)
                val found = probeRtspOnly(ip, rtspPort, onLog)
                if (found != null) {
                    onLog("$ip: RTSP stream available (${found.second.label})")
                    return ScanResult(CameraTarget.Rtsp(found.first), true, hostsResponded)
                }
                onLog("$ip: RTSP stream unavailable with any password from the list")
                onStatus(Status.NextDevice)
            }
        }

        return ScanResult(null, cameraSeen, hostsResponded)
    }

    private suspend fun probeCamera(
        ip: String,
        ports: List<Int>,
        baseUrl: String,
        page: HttpResult,
        onLog: (String) -> Unit,
    ): CameraTarget? {
        val hints = extractStreamHints(page.body, baseUrl)
        val mjpegCandidates = (hints.mjpeg + MJPEG_PATHS).distinct()
        val snapshotCandidates = (hints.snapshot + SNAPSHOT_PATHS).distinct()

        for (path in mjpegCandidates) {
            val url = if (path.startsWith("http")) path else baseUrl + path
            if (isMjpegStream(url, null)) {
                onLog("$ip: found unauthenticated MJPEG stream $path")
                return CameraTarget.Mjpeg(url, null)
            }
        }
        for (path in snapshotCandidates) {
            val url = if (path.startsWith("http")) path else baseUrl + path
            if (isJpegSnapshot(url, null)) {
                onLog("$ip: unauthenticated snapshot stream available $path")
                return CameraTarget.Snapshot(url, null)
            }
        }

        for (cred in CREDENTIALS) {
            onLog("$ip: trying login ${cred.label}")
            val auth = cred.basicHeader

            for (path in mjpegCandidates) {
                val url = if (path.startsWith("http")) path else baseUrl + path
                if (isMjpegStream(url, auth)) {
                    onLog("$ip: password worked (${cred.label}), MJPEG stream $path")
                    return CameraTarget.Mjpeg(url, auth)
                }
            }

            for (path in snapshotCandidates) {
                val url = if (path.startsWith("http")) path else baseUrl + path
                if (isJpegSnapshot(url, auth)) {
                    onLog("$ip: password worked (${cred.label}), snapshot stream $path")
                    return CameraTarget.Snapshot(url, auth)
                }
            }

            for (raw in hints.rtsp) {
                val url = rtspUrlWithCredentials(raw, cred)
                when (rtspProbe(url, auth)) {
                    RtspProbe.OK -> {
                        onLog("$ip: password worked (${cred.label}), RTSP stream responds")
                        return CameraTarget.Rtsp(url)
                    }
                    RtspProbe.BAD_AUTH -> onLog("$ip: RTSP server rejected login ${cred.user} (401)")
                    RtspProbe.FAIL -> onLog("$ip: RTSP server did not respond to DESCRIBE")
                }
            }

            val rtspPort = ports.firstOrNull(RTSP_PORTS::contains)
            if (rtspPort != null) {
                for (path in RTSP_PATHS) {
                    val url = "rtsp://${cred.user}:${cred.password}@$ip:$rtspPort$path"
                    if (rtspProbe(url, auth) == RtspProbe.OK) {
                        onLog("$ip: password worked (${cred.label}), RTSP stream at path $path")
                        return CameraTarget.Rtsp(url)
                    }
                }
            }
        }

        return null
    }

    private fun probeRtspOnly(ip: String, port: Int, onLog: (String) -> Unit): Pair<String, Credentials>? {
        for (cred in CREDENTIALS) {
            onLog("$ip: trying login ${cred.label}")
            for (path in RTSP_PATHS) {
                val url = "rtsp://${cred.user}:${cred.password}@$ip:$port$path"
                if (rtspProbe(url, cred.basicHeader) == RtspProbe.OK) return url to cred
            }
        }
        return null
    }

    private suspend fun scanSubnet(prefix: String, onLog: (String) -> Unit): Map<String, List<Int>> = coroutineScope {
        val semaphore = Semaphore(SCAN_PARALLELISM)
        val ports = (HTTP_PORTS + RTSP_PORTS).distinct()
        val ips = (1..254).map { "$prefix.$it" }

        suspend fun runPass(label: String): Map<String, List<Int>> {
            onLog(label)
            return ips.flatMap { ip ->
                ports.map { port ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            if (portOpen(ip, port)) ip to port else null
                        }
                    }
                }
            }.awaitAll().filterNotNull().groupBy({ it.first }, { it.second })
        }

        val first = runPass("Scanning 254 addresses × ${ports.size} ports…")
        if (first.isNotEmpty()) return@coroutineScope first
        runPass("First pass found nothing — rescanning…")
    }

    private fun portOpen(ip: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), PORT_TIMEOUT_MS); true }
    } catch (e: Exception) {
        false
    }

    private fun localIpv4(): Pair<Inet4Address, String>? {
        val nifs = NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .toList()
        val preferred = nifs.firstOrNull { it.name.startsWith("wlan") || it.name.startsWith("eth") }
        val ordered = listOfNotNull(preferred) + nifs.filter { it != preferred }
        for (nif in ordered) {
            for (addr in nif.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                    return addr to nif.name
                }
            }
        }
        return null
    }

    data class HttpResult(
        val code: Int,
        val server: String?,
        val wwwAuthenticate: String?,
        val contentType: String?,
        val body: String,
    )

    private fun httpGet(urlStr: String, authHeader: String?, maxBodyBytes: Int = 256 * 1024): HttpResult? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "CamCatch/1.0")
            if (authHeader != null) conn.setRequestProperty("Authorization", authHeader)
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val body = stream?.let { readLimited(it, maxBodyBytes) } ?: ByteArray(0)
            HttpResult(
                code = code,
                server = conn.getHeaderField("Server"),
                wwwAuthenticate = conn.getHeaderField("WWW-Authenticate"),
                contentType = conn.contentType,
                body = String(body, Charsets.UTF_8),
            )
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(max, 64 * 1024))
        val buf = ByteArray(8192)
        var total = 0
        while (total < max) {
            val n = input.read(buf, 0, minOf(buf.size, max - total))
            if (n == -1) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private fun isMjpegStream(urlStr: String, authHeader: String?): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "CamCatch/1.0")
            if (authHeader != null) conn.setRequestProperty("Authorization", authHeader)
            val code = conn.responseCode
            if (code !in 200..299) return false
            val ct = conn.contentType?.lowercase(Locale.ROOT) ?: return false
            ct.contains("multipart") || ct.contains("x-mixed-replace") || ct.contains("mjpeg")
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun isJpegSnapshot(urlStr: String, authHeader: String?): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "CamCatch/1.0")
            if (authHeader != null) conn.setRequestProperty("Authorization", authHeader)
            val code = conn.responseCode
            if (code !in 200..299) return false
            val bytes = readLimited(conn.inputStream, 4 * 1024 * 1024)
            bytes.size > 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private enum class RtspProbe { OK, BAD_AUTH, FAIL }

    private fun rtspProbe(url: String, authHeader: String): RtspProbe {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return RtspProbe.FAIL
            val port = if (uri.port > 0) uri.port else 554
            val path = when {
                uri.rawPath.isNullOrEmpty() -> "/"
                uri.rawQuery != null -> uri.rawPath + "?" + uri.rawQuery
                else -> uri.rawPath
            }
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 4000)
                socket.soTimeout = 4000
                val request = buildString {
                    append("DESCRIBE rtsp://").append(host).append(':').append(port).append(path)
                    append(" RTSP/1.0\r\n")
                    append("CSeq: 1\r\n")
                    append("Accept: application/sdp\r\n")
                    append("Authorization: ").append(authHeader).append("\r\n")
                    append("User-Agent: CamCatch/1.0\r\n")
                    append("\r\n")
                }
                socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                socket.getOutputStream().flush()
                val statusLine = socket.getInputStream().bufferedReader().readLine()
                    ?: return RtspProbe.FAIL
                when {
                    statusLine.contains(" 200") -> RtspProbe.OK
                    statusLine.contains(" 401") -> RtspProbe.BAD_AUTH
                    else -> RtspProbe.FAIL
                }
            }
        } catch (e: Exception) {
            RtspProbe.FAIL
        }
    }

    private fun rtspUrlWithCredentials(rawUrl: String, cred: Credentials): String {
        val rest = rawUrl.removePrefix("rtsp://").removePrefix("RTSP://")
        val hostPart = if (rest.contains('@')) rest.substringAfter('@') else rest
        return "rtsp://${cred.user}:${cred.password}@$hostPart"
    }

    private fun detectCamera(r: HttpResult): Boolean {
        val body = r.body.lowercase(Locale.ROOT)
        val server = r.server?.lowercase(Locale.ROOT) ?: ""
        val routerLike = ROUTER_MARKERS.any { body.contains(it) || server.contains(it) }
        if (routerLike) return false
        val title = body.substringAfter("<title", "").substringAfter('>', "").substringBefore('<')
        if (title.isNotBlank() && CAMERA_KEYWORDS.any { title.contains(it) }) return true
        if (CAMERA_KEYWORDS.any { server.contains(it) }) return true
        if (CAMERA_KEYWORDS.any { body.contains(it) }) return true
        if (r.code == 401) return true
        return false
    }

    private data class StreamHints(
        val mjpeg: List<String>,
        val snapshot: List<String>,
        val rtsp: List<String>,
    )

    private fun extractStreamHints(html: String, baseUrl: String): StreamHints {
        val linkAttrs = Regex("""(?:src|href|data-src|action)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { it.groupValues[1] }
        val rtspLinks = Regex("""["'](rtsp://[^"'\s<>]+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { it.groupValues[1] }
        val links = (linkAttrs + rtspLinks).toList()
        val resolved = links.mapNotNull { link ->
            try {
                URI(baseUrl).resolve(link.trim()).toString()
            } catch (e: Exception) {
                null
            }
        }
        return StreamHints(
            mjpeg = resolved.filter {
                it.contains("mjpg", true) || it.contains("mjpeg", true) ||
                    it.contains("videostream", true) || it.contains("video.cgi", true)
            },
            snapshot = resolved.filter {
                it.contains("snapshot", true) || it.contains("webcam", true) ||
                    it.endsWith(".jpg", true) || it.endsWith(".jpeg", true)
            },
            rtsp = links.filter { it.startsWith("rtsp://", true) },
        )
    }

    fun mjpegFrames(url: String, authHeader: String?): Flow<Bitmap> = flow {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "CamCatch/1.0")
            if (authHeader != null) conn.setRequestProperty("Authorization", authHeader)
            val input = BufferedInputStream(conn.inputStream, 128 * 1024)
            val frame = ByteArrayOutputStream(256 * 1024)
            var collecting = false
            var prev = -1
            while (true) {
                val b = input.read()
                if (b == -1) break
                if (!collecting) {
                    if (prev == 0xFF && b == 0xD8) {
                        collecting = true
                        frame.reset()
                        frame.write(0xFF)
                        frame.write(0xD8)
                    }
                } else {
                    frame.write(b)
                    if (prev == 0xFF && b == 0xD9) {
                        collecting = false
                        val bytes = frame.toByteArray()
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) emit(bmp)
                    }
                }
                prev = b
            }
        } finally {
            conn.disconnect()
        }
    }.conflate().flowOn(Dispatchers.IO)

    fun snapshotFrames(url: String, authHeader: String?, intervalMs: Long = 150): Flow<Bitmap> = flow {
        while (true) {
            var bmp: Bitmap? = null
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = HTTP_TIMEOUT_MS
                conn.readTimeout = HTTP_TIMEOUT_MS
                conn.instanceFollowRedirects = false
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "CamCatch/1.0")
                if (authHeader != null) conn.setRequestProperty("Authorization", authHeader)
                if (conn.responseCode in 200..299) {
                    val bytes = conn.inputStream.readBytes()
                    if (bytes.size > 4 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
            } catch (e: Exception) {
                bmp = null
            } finally {
                conn?.disconnect()
            }
            if (bmp != null) emit(bmp)
            delay(intervalMs)
        }
    }.conflate().flowOn(Dispatchers.IO)
}
