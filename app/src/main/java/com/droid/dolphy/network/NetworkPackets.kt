

package com.droid.dolphy.network

import androidx.compose.foundation.layout.size
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

internal fun dhcpIntToIpv4(value: Int): String? {
    if (value == 0) return null
    return try {
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        InetAddress.getByAddress(bytes).hostAddress
    } catch (_: Exception) {
        null
    }
}

internal fun getHubLocalIpv4(): String? {
    return try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (intf in interfaces) {
            if (!intf.isUp || intf.isLoopback) continue
            for (addr in Collections.list(intf.inetAddresses)) {
                if (addr.isLoopbackAddress) continue
                val host = addr.hostAddress ?: continue
                if (host.contains(':')) continue
                return host
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

internal fun readArpTable(): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    try {
        BufferedReader(FileReader("/proc/net/arp")).use { reader ->
            reader.readLine()
            var line = reader.readLine()
            while (line != null) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val ip = parts[0]
                    val mac = parts[3].uppercase(Locale.US)
                    if (mac.matches(Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")) && mac != "00:00:00:00:00:00") {
                        out += ip to mac
                    }
                }
                line = reader.readLine()
            }
        }
    } catch (_: Exception) {
    }
    return out
}

internal fun quickPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
    return runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, port), timeoutMs)
            true
        }
    }.getOrDefault(false)
}


internal fun buildDnsQuery(hostname: String, id: Int = (System.nanoTime() and 0xFFFF).toInt()): ByteArray {
    val header = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
    header.putShort((id and 0xFFFF).toShort())
    header.putShort(0x0100.toShort())
    header.putShort(1)
    header.putShort(0)
    header.putShort(0)
    header.putShort(0)
    val qname = java.io.ByteArrayOutputStream()
    hostname.split('.').forEach { label ->
        if (label.isEmpty()) return@forEach
        val bytes = label.toByteArray(Charsets.US_ASCII).take(63).toByteArray()
        qname.write(bytes.size)
        qname.write(bytes)
    }
    qname.write(0)
    val tail = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
    val qtype = if ((id and 1) == 0) 1 else 28
    tail.putShort(qtype.toShort())
    tail.putShort(1)
    return header.array() + qname.toByteArray() + tail.array()
}


internal fun buildRandomRecursiveDnsQuery(random: java.util.Random): ByteArray {
    val id = random.nextInt(0x10000)
    val label = buildString(16) {
        repeat(10 + random.nextInt(6)) {
            append(('a'.code + random.nextInt(26)).toChar())
        }
    }
    val num = random.nextInt(1_000_000)
    val roots = arrayOf(
        "com", "net", "org", "ru", "io", "info", "xyz", "online",
        "google.com", "cloudflare.com", "amazon.com", "microsoft.com",
        "yandex.ru", "vk.com", "apple.com",
    )
    val root = roots[random.nextInt(roots.size)]
    val host = when (random.nextInt(4)) {
        0 -> "$label$num.$root"
        1 -> "$label.$num.$root"
        2 -> "cdn-$label-$num.$root"
        else -> "r$num-$label.$root"
    }
    return buildDnsQuery(host, id)
}


internal fun buildDhcpDiscover(): ByteArray {
    val buf = ByteArray(300)
    buf[0] = 1
    buf[1] = 1
    buf[2] = 6
    val xid = (System.nanoTime() and 0xFFFFFFFFL).toInt()
    buf[4] = (xid ushr 24).toByte()
    buf[5] = (xid ushr 16).toByte()
    buf[6] = (xid ushr 8).toByte()
    buf[7] = xid.toByte()
    buf[10] = 0x80.toByte()
    val rnd = java.util.Random()
    for (i in 0 until 6) buf[28 + i] = rnd.nextInt(256).toByte()
    buf[28] = ((buf[28].toInt() and 0xFE) or 0x02).toByte()
    buf[236] = 99
    buf[237] = 130.toByte()
    buf[238] = 83
    buf[239] = 99
    buf[240] = 53
    buf[241] = 1
    buf[242] = 1
    buf[243] = 55
    buf[244] = 4
    buf[245] = 1
    buf[246] = 3
    buf[247] = 6
    buf[248] = 15
    buf[249] = 255.toByte()
    return buf
}

private const val MAX_HELD_FDS = 700
private val heldFdCount = AtomicInteger(0)

internal fun tryAcquireFd(): Boolean {
    while (true) {
        val current = heldFdCount.get()
        if (current >= MAX_HELD_FDS) return false
        if (heldFdCount.compareAndSet(current, current + 1)) return true
    }
}

internal fun releaseFd() {
    heldFdCount.updateAndGet { maxOf(0, it - 1) }
}

internal fun buildSsdpMsearch(st: String = "ssdp:all"): ByteArray {
    return ("M-SEARCH * HTTP/1.1\r\n" +
        "HOST: 239.255.255.250:1900\r\n" +
        "MAN: \"ssdp:discover\"\r\n" +
        "MX: 2\r\n" +
        "ST: $st\r\n\r\n").toByteArray(Charsets.US_ASCII)
}

internal fun buildMdnsServiceQuery(id: Int): ByteArray {
    val header = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
    header.putShort((id and 0xFFFF).toShort())
    header.putShort(0)
    header.putShort(1)
    header.putShort(0)
    header.putShort(0)
    header.putShort(0)
    val qname = byteArrayOf(
        9, '_'.code.toByte(), 's'.code.toByte(), 'e'.code.toByte(), 'r'.code.toByte(),
        'v'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(), 'e'.code.toByte(),
        's'.code.toByte(),
        7, '_'.code.toByte(), 'd'.code.toByte(), 'n'.code.toByte(), 's'.code.toByte(),
        '-'.code.toByte(), 's'.code.toByte(), 'd'.code.toByte(),
        4, '_'.code.toByte(), 'u'.code.toByte(), 'd'.code.toByte(), 'p'.code.toByte(),
        5, 'l'.code.toByte(), 'o'.code.toByte(), 'c'.code.toByte(), 'a'.code.toByte(),
        'l'.code.toByte(),
        0,
    )
    val tail = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
    tail.putShort(12)
    tail.putShort(1)
    return header.array() + qname + tail.array()
}

internal fun buildSnmpGetNext(requestId: Int): ByteArray {
    return byteArrayOf(
        0x30.toByte(), 0x26.toByte(),
        0x02, 0x01, 0x01,
        0x04, 0x06, 0x70, 0x75, 0x62, 0x6C, 0x69, 0x63,
        0xA1.toByte(), 0x19.toByte(),
        0x02, 0x04,
        (requestId ushr 24).toByte(), (requestId ushr 16).toByte(),
        (requestId ushr 8).toByte(), requestId.toByte(),
        0x02, 0x01, 0x00,
        0x02, 0x01, 0x00,
        0x30, 0x0B,
        0x30, 0x09,
        0x06, 0x07, 0x2B, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01,
        0x05, 0x00,
    )
}
