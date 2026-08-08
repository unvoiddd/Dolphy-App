package com.droid.dolphy.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext







object CameraNetworkScanner {
    private const val TAG = "CameraNetworkScanner"
    private const val MULTICAST_ADDRESS = "239.255.255.250"
    private const val MULTICAST_PORT = 3702
    private const val DATAGRAM_SIZE = 64 * 1024


    private val STRONG_CAMERA_PORTS = intArrayOf(
        554,
        8554,
        10554,
        37777,
        34567,
    )


    private val PRECHECK_PORTS = intArrayOf(554, 8554, 37777, 34567)

    data class FoundCamera(
        val ip: String,
        val name: String,
        val brand: String,
        val mac: String?,
        val source: String,
        val openPorts: List<Int> = emptyList(),
        val endpoint: String? = null,
    )

    data class ScanProgress(
        val fraction: Float,
        val stage: String,
        val foundCount: Int,
    )

    fun probeXml(messageId: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
            xmlns:a="http://schemas.xmlsoap.org/ws/2004/08/addressing"
            xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
            xmlns:dp0="http://www.onvif.org/ver10/network/wsdl">
          <s:Header>
            <a:Action s:mustUnderstand="1">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>
            <a:MessageID>uuid:$messageId</a:MessageID>
            <a:ReplyTo>
              <a:Address>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</a:Address>
            </a:ReplyTo>
            <a:To s:mustUnderstand="1">urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>
          </s:Header>
          <s:Body>
            <d:Probe>
              <d:Types>dp0:NetworkVideoTransmitter</d:Types>
            </d:Probe>
          </s:Body>
        </s:Envelope>
    """.trimIndent()

    suspend fun scan(
        context: Context,
        onProgress: (ScanProgress) -> Unit,
        onCamera: (FoundCamera) -> Unit = {},
    ): List<FoundCamera> = withContext(Dispatchers.IO) {
        val found = ConcurrentHashMap<String, FoundCamera>()
        fun emit(cam: FoundCamera) {
            if (!isValidCameraCandidate(cam)) return
            val prev = found[cam.ip]
            if (prev == null || cam.source == "ONVIF" || (prev.mac == null && cam.mac != null)) {
                found[cam.ip] = cam
                onCamera(found[cam.ip]!!)
            }
        }

        onProgress(ScanProgress(0.02f, "Подготовка сети…", 0))
        val subnet = localSubnetPrefix()
        val self = localIp()
        val multicastLock = try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wm?.createMulticastLock("DolphyOnvifCam")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {
            null
        }

        try {
            onProgress(ScanProgress(0.08f, "ONVIF WS-Discovery…", found.size))
            runOnvifDiscovery(retryCount = 2) { ip, name, brand, endpoint ->
                if (ip == self) return@runOnvifDiscovery
                val mac = getMacFromArp(ip)
                emit(
                    FoundCamera(
                        ip = ip,
                        name = name.ifBlank { "ONVIF Camera" },
                        brand = brand.ifBlank { lookupBrand(mac) ?: "ONVIF" },
                        mac = mac,
                        source = "ONVIF",
                        endpoint = endpoint,
                    )
                )
                onProgress(ScanProgress(0.2f, "ONVIF: ${found.size}", found.size))
            }

            onProgress(ScanProgress(0.28f, "Поиск RTSP / NVR портов…", found.size))

            if (subnet != null && coroutineContext.isActive) {
                val hosts = (1..254).map { "$subnet.$it" }
                var done = 0
                coroutineScope {
                    hosts.chunked(24).forEach { chunk ->
                        if (!coroutineContext.isActive) return@coroutineScope
                        chunk.map { ip ->
                            async {
                                if (ip == self) return@async

                                if (!anyPortOpen(ip, PRECHECK_PORTS, timeoutMs = 120)) return@async
                                val open = probeStrongPorts(ip)
                                if (open.isEmpty()) return@async

                                if (!confirmCameraHost(ip, open)) return@async
                                val mac = getMacFromArp(ip)
                                val brand = detectHttpBrandStrict(ip)
                                    ?: lookupBrand(mac)
                                    ?: guessBrandFromPorts(open)
                                val name = detectHttpTitleStrict(ip)
                                    ?: "$brand Camera"
                                emit(
                                    FoundCamera(
                                        ip = ip,
                                        name = name,
                                        brand = brand,
                                        mac = mac,
                                        source = when {
                                            554 in open || 8554 in open -> "RTSP"
                                            37777 in open -> "Dahua"
                                            34567 in open -> "Hikvision"
                                            else -> "PORT"
                                        },
                                        openPorts = open,
                                    )
                                )
                            }
                        }.awaitAll()
                        done += chunk.size
                        val frac = 0.28f + (done.toFloat() / hosts.size) * 0.62f
                        onProgress(
                            ScanProgress(
                                frac.coerceIn(0f, 0.92f),
                                "Проверено $done / ${hosts.size}…",
                                found.size,
                            )
                        )
                    }
                }
            }

            onProgress(ScanProgress(0.95f, "Уточнение MAC…", found.size))
            delay(300)
            found.values.toList().forEach { cam ->
                val mac = cam.mac ?: getMacFromArp(cam.ip)
                if (mac != null && cam.mac != mac) {
                    val brand = when {
                        cam.brand == "ONVIF" || cam.brand == "IP Camera" ->
                            lookupBrand(mac) ?: cam.brand
                        else -> cam.brand
                    }
                    found[cam.ip] = cam.copy(mac = mac, brand = brand)
                }
            }

            val cleaned = found.values.filter { isValidCameraCandidate(it) }
                .sortedBy { it.ip }
            found.clear()
            cleaned.forEach { found[it.ip] = it }
            onProgress(ScanProgress(1f, "Готово", found.size))
        } finally {
            try {
                if (multicastLock?.isHeld == true) multicastLock.release()
            } catch (_: Exception) {
            }
        }
        found.values.sortedBy { it.ip }
    }




    private fun isValidCameraCandidate(cam: FoundCamera): Boolean {
        if (cam.ip.isBlank()) return false

        if (cam.source == "ONVIF") {
            val ep = cam.endpoint?.lowercase().orEmpty()
            if (ep.isBlank()) return false
            if (!ep.contains("onvif") && !ep.contains("device_service") && !ep.startsWith("http")) {
                return false
            }
            return true
        }

        if (cam.openPorts.none { it in STRONG_CAMERA_PORTS }) return false

        return cam.openPorts.any { it == 554 || it == 8554 || it == 37777 || it == 34567 }
    }

    private fun runOnvifDiscovery(
        retryCount: Int,
        onDevice: (ip: String, name: String, brand: String, endpoint: String?) -> Unit,
    ) {
        var socket: MulticastSocket? = null
        try {
            val group = InetAddress.getByName(MULTICAST_ADDRESS)
            socket = MulticastSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 2000
            }
            try {
                socket.bind(InetSocketAddress(0))
            } catch (_: Exception) {
                return
            }
            try {
                socket.joinGroup(InetSocketAddress(group, 0), null)
            } catch (_: Exception) {
                @Suppress("DEPRECATION")
                try {
                    socket.joinGroup(group)
                } catch (_: Exception) {
                }
            }

            val msgId = UUID.randomUUID().toString()
            val bytes = probeXml(msgId).toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(bytes, bytes.size, group, MULTICAST_PORT)
            repeat(1 + retryCount) {
                try {
                    socket.send(packet)
                } catch (e: Exception) {
                    Log.w(TAG, "probe send", e)
                }
            }

            val deadline = System.currentTimeMillis() + 4000
            val buffer = ByteArray(DATAGRAM_SIZE)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val recv = DatagramPacket(buffer, buffer.size)
                    socket.receive(recv)
                    val xml = String(recv.data, recv.offset, recv.length, Charsets.UTF_8)
                    if (!isStrictOnvifProbeMatch(xml)) continue
                    val ip = recv.address?.hostAddress ?: continue
                    val cleanIp = ip.removePrefix("/").substringBefore("%")
                    val xaddrs = extractTag(xml, "XAddrs") ?: extractTag(xml, "d:XAddrs")
                    val scopes = extractTag(xml, "Scopes") ?: extractTag(xml, "d:Scopes") ?: ""
                    val endpoint = xaddrs?.split(Regex("\\s+"))
                        ?.firstOrNull { it.startsWith("http", ignoreCase = true) }
                        ?: continue

                    val epLow = endpoint.lowercase()
                    if (!epLow.contains("onvif") && !epLow.contains("device_service") &&
                        !epLow.contains("media") && !xml.contains("NetworkVideoTransmitter", ignoreCase = true)
                    ) continue
                    val name = parseScopeName(scopes) ?: "ONVIF Camera"
                    val brand = parseScopeBrand(scopes) ?: "ONVIF"
                    onDevice(cleanIp, name, brand, endpoint)
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "onvif discovery", e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }


    private fun isStrictOnvifProbeMatch(xml: String): Boolean {
        val lower = xml.lowercase()
        if (!lower.contains("probematch")) return false
        if (!lower.contains("xaddrs")) return false

        if (lower.contains("ssdp:") && !lower.contains("onvif")) return false
        val videoHints = listOf(
            "networkvideotransmitter",
            "onvif",
            "device_service",
            "www.onvif.org",
            "tds:device",
            "videotransmitter",
        )
        return videoHints.any { it in lower }
    }

    private fun extractTag(xml: String, localName: String): String? {
        val re = Regex(
            """<(?:[\w.]+:)?$localName[^>]*>([^<]+)</(?:[\w.]+:)?$localName>""",
            RegexOption.IGNORE_CASE,
        )
        return re.find(xml)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun parseScopeName(scopes: String): String? {
        val nameRe = Regex("""onvif://www\.onvif\.org/name/([^/\s]+)""", RegexOption.IGNORE_CASE)
        nameRe.find(scopes)?.groupValues?.getOrNull(1)?.let {
            return java.net.URLDecoder.decode(it, "UTF-8")
        }
        return null
    }

    private fun parseScopeBrand(scopes: String): String? {
        val hw = Regex("""onvif://www\.onvif\.org/hardware/([^/\s]+)""", RegexOption.IGNORE_CASE)
        hw.find(scopes)?.groupValues?.getOrNull(1)?.let {
            return java.net.URLDecoder.decode(it, "UTF-8")
        }
        val known = listOf(
            "hikvision", "dahua", "axis", "hanwha", "bosch", "sony",
            "panasonic", "uniview", "reolink", "imou", "ezviz", "foscam", "amcrest", "vivotek",
        )
        val lower = scopes.lowercase()
        known.forEach { k ->
            if (k in lower) return k.replaceFirstChar { it.uppercase() }
        }
        return null
    }

    private fun anyPortOpen(ip: String, ports: IntArray, timeoutMs: Int): Boolean {
        for (port in ports) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), timeoutMs)
                    return true
                }
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun probeStrongPorts(ip: String): List<Int> {
        val open = mutableListOf<Int>()
        for (port in STRONG_CAMERA_PORTS) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), 200)
                    open += port
                }
            } catch (_: Exception) {
            }
        }
        return open.sorted()
    }





    private fun confirmCameraHost(ip: String, openPorts: List<Int>): Boolean {
        if (openPorts.any { it == 37777 || it == 34567 }) return true
        val rtspPort = openPorts.firstOrNull { it == 554 || it == 8554 || it == 10554 } ?: return false
        return looksLikeRtsp(ip, rtspPort)
    }

    private fun looksLikeRtsp(ip: String, port: Int): Boolean {
        return try {
            Socket().use { s ->
                s.soTimeout = 600
                s.connect(InetSocketAddress(ip, port), 300)
                val req = "OPTIONS rtsp://$ip:$port/ RTSP/1.0\r\nCSeq: 1\r\n\r\n"
                s.getOutputStream().write(req.toByteArray())
                s.getOutputStream().flush()
                val buf = ByteArray(256)
                val n = s.getInputStream().read(buf)
                if (n <= 0) return false
                val resp = String(buf, 0, n, Charsets.ISO_8859_1)
                resp.contains("RTSP", ignoreCase = true)
            }
        } catch (_: Exception) {



            false
        }
    }

    private fun detectHttpBrandStrict(ip: String): String? {

        for (port in listOf(80, 8080, 8000)) {
            try {
                if (!anyPortOpen(ip, intArrayOf(port), 150)) continue
                val conn = URL("http://$ip:$port/").openConnection() as HttpURLConnection
                conn.connectTimeout = 400
                conn.readTimeout = 400
                conn.instanceFollowRedirects = true
                val server = ((conn.getHeaderField("Server") ?: "") + " " +
                    (conn.getHeaderField("WWW-Authenticate") ?: "")).lowercase()
                val body = try {
                    conn.inputStream.bufferedReader().use { it.readText().take(2500).lowercase() }
                } catch (_: Exception) {
                    ""
                }
                conn.disconnect()
                val blob = "$server $body"
                mapOf(
                    "hikvision" to "Hikvision",
                    "dahua" to "Dahua",
                    "netsurveillance" to "Dahua",
                    "axis" to "Axis",
                    "reolink" to "Reolink",
                    "imou" to "Imou",
                    "ezviz" to "Ezviz",
                    "uniview" to "Uniview",
                    "amcrest" to "Amcrest",
                    "foscam" to "Foscam",
                    "vivotek" to "Vivotek",
                    "onvif" to "ONVIF",
                ).forEach { (k, v) -> if (k in blob) return v }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun detectHttpTitleStrict(ip: String): String? {
        for (port in listOf(80, 8080)) {
            try {
                if (!anyPortOpen(ip, intArrayOf(port), 150)) continue
                val conn = URL("http://$ip:$port/").openConnection() as HttpURLConnection
                conn.connectTimeout = 400
                conn.readTimeout = 400
                val body = conn.inputStream.bufferedReader().use { it.readText().take(3000) }
                conn.disconnect()
                val title = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.getOrNull(1)?.trim()?.take(64) ?: continue
                val t = title.lowercase()

                if (t.contains("router") || t.contains("login") && !t.contains("camera") &&
                    !t.contains("nvr") && !t.contains("dvr") && !t.contains("ipcam")
                ) continue
                if (t.contains("camera") || t.contains("nvr") || t.contains("dvr") ||
                    t.contains("ipcam") || t.contains("onvif") || t.contains("hikvision") ||
                    t.contains("dahua") || t.contains("reolink")
                ) return title
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun guessBrandFromPorts(ports: List<Int>): String = when {
        37777 in ports -> "Dahua"
        34567 in ports -> "Hikvision"
        554 in ports || 8554 in ports -> "IP Camera"
        else -> "IP Camera"
    }

    fun getMacFromArp(ip: String): String? {
        return try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine()
                var line = reader.readLine()
                while (line != null) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 4 && parts[0] == ip) {
                        val mac = parts[3].uppercase()
                        if (mac.matches(Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")) &&
                            mac != "00:00:00:00:00:00"
                        ) return mac
                    }
                    line = reader.readLine()
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun lookupBrand(mac: String?): String? {
        if (mac == null) return null
        val n = mac.uppercase().replace("-", ":")
        return cameraOui[n.take(8)]
    }

    private fun localIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (!ni.isUp || ni.isLoopback) return@forEach
                ni.inetAddresses.toList().forEach { addr ->
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains('.') == true) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun localSubnetPrefix(): String? {
        val ip = localIp() ?: return null
        val parts = ip.split(".")
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }

    private val cameraOui = mapOf(
        "00:11:5E" to "Hikvision", "00:16:38" to "Hikvision", "00:1D:A5" to "Hikvision",
        "00:1F:64" to "Hikvision", "00:21:B7" to "Hikvision", "00:23:C1" to "Hikvision",
        "28:57:BE" to "Hikvision", "44:19:B6" to "Hikvision", "54:C4:15" to "Hikvision",
        "BC:AD:28" to "Hikvision", "C0:56:E3" to "Hikvision", "E0:CA:3C" to "Hikvision",
        "00:11:22" to "Dahua", "00:0D:E0" to "Dahua", "3C:EF:8C" to "Dahua",
        "4C:11:BF" to "Dahua", "90:02:A9" to "Dahua", "A0:BD:1D" to "Dahua",
        "E4:24:6C" to "Dahua", "00:1E:C1" to "Axis", "00:40:8C" to "Axis",
        "AC:CC:8E" to "Axis", "B8:A4:4F" to "Axis", "00:12:12" to "Vivotek",
        "00:02:D1" to "Vivotek", "00:1B:A9" to "Bosch", "00:07:5F" to "Bosch",
    )
}


object CameraScanResultsStore {
    @Volatile
    var lastResults: List<CameraNetworkScanner.FoundCamera> = emptyList()
        private set

    @Volatile
    var lastScanAt: Long = 0L
        private set

    fun set(results: List<CameraNetworkScanner.FoundCamera>) {
        lastResults = results
        lastScanAt = System.currentTimeMillis()
    }

    fun clear() {
        lastResults = emptyList()
        lastScanAt = 0L
    }
}

