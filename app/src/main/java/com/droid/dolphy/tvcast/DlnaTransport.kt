package com.droid.dolphy.tvcast

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.SocketTimeoutException
import javax.xml.parsers.DocumentBuilderFactory

data class DlnaRenderer(val name: String, val controlUrl: String, val serviceType: String)

class DlnaTransport(private val context: Context) {
    suspend fun discover(onRenderer: (DlnaRenderer) -> Unit) {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("dolphy-dlna")?.apply { setReferenceCounted(false); acquire() }
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val request = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n")
                    .toByteArray(Charsets.US_ASCII)
                socket.send(DatagramPacket(request, request.size, InetAddress.getByName("239.255.255.250"), 1900))
                val seen = mutableSetOf<String>()
                val deadline = System.nanoTime() + 4_000_000_000L
                while (System.nanoTime() < deadline) {
                    currentCoroutineContext().ensureActive()
                    val packet = DatagramPacket(ByteArray(8192), 8192)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                    val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val location = response.lineSequence().firstOrNull { it.startsWith("location:", ignoreCase = true) }
                        ?.substringAfter(':')?.trim() ?: continue
                    if (!seen.add(location)) continue
                    runCatching { describe(location) }.getOrNull()?.let(onRenderer)
                }
            }
        } finally { if (lock?.isHeld == true) lock.release() }
    }

    private fun describe(location: String): DlnaRenderer? {
        val url = URL(location)
        val address = InetAddress.getByName(url.host)
        require(address.isSiteLocalAddress || address.isLinkLocalAddress)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 2000
        connection.readTimeout = 2000
        connection.instanceFollowRedirects = false
        try {
            check(connection.responseCode in 200..299)
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            val document = connection.inputStream.use { factory.newDocumentBuilder().parse(it) }
            val name = document.getElementsByTagNameNS("*", "friendlyName").item(0)?.textContent ?: url.host
            val base = document.getElementsByTagNameNS("*", "URLBase").item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
                ?.let(::URL) ?: url
            val services = document.getElementsByTagNameNS("*", "service")
            for (i in 0 until services.length) {
                val service = services.item(i) as? org.w3c.dom.Element ?: continue
                val type = service.getElementsByTagNameNS("*", "serviceType").item(0)?.textContent ?: continue
                if (!type.startsWith("urn:schemas-upnp-org:service:AVTransport:")) continue
                val control = service.getElementsByTagNameNS("*", "controlURL").item(0)?.textContent?.trim() ?: continue
                val endpoint = URL(base, control)
                require(endpoint.host == url.host)
                return DlnaRenderer(name, endpoint.toString(), type)
            }
            return null
        } finally { connection.disconnect() }
    }

    fun play(renderer: DlnaRenderer, mediaUrl: String) {
        soap(renderer, "SetAVTransportURI", "<InstanceID>0</InstanceID><CurrentURI>${escape(mediaUrl)}</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>")
        soap(renderer, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    private fun soap(renderer: DlnaRenderer, action: String, body: String) {
        val connection = URL(renderer.controlUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 3000
        connection.readTimeout = 5000
        connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
        connection.setRequestProperty("SOAPACTION", "\"${renderer.serviceType}#$action\"")
        connection.doOutput = true
        try {
            val xml = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>" +
                "<u:$action xmlns:u=\"${renderer.serviceType}\">$body</u:$action></s:Body></s:Envelope>"
            connection.outputStream.use { it.write(xml.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode in 200..299) { "${renderer.name}: HTTP ${connection.responseCode}" }
        } finally { connection.disconnect() }
    }
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
