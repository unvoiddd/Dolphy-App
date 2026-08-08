package com.droid.dolphy.plugin.bridge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.droid.dolphy.RootUtils
import com.droid.dolphy.ShizukuHelper
import com.droid.dolphy.plugin.PluginManager
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import android.bluetooth.le.ScanFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.os.ParcelUuid




class PluginDeviceApis(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val bleCallbacks = ConcurrentHashMap<String, ScanCallback>()
    private val bleBatchers = ConcurrentHashMap<String, BleBatcher>()
    @Volatile private var lastWifiScanAt = 0L

    private val wifiManager: WifiManager?
        get() = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val btAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter


    fun wifiIsEnabled(): Boolean = try {
        wifiManager?.isWifiEnabled == true
    } catch (_: Exception) {
        false
    }





    @SuppressLint("MissingPermission")
    fun wifiScanResultsJson(maxResults: Int = DEFAULT_WIFI_MAX, minRssi: Int? = null): String {
        val wm = wifiManager ?: return "[]"
        return try {
            val cap = when {
                maxResults <= 0 -> DEFAULT_WIFI_MAX
                else -> maxResults.coerceAtMost(200)
            }
            @Suppress("DEPRECATION")
            val sorted = wm.scanResults.orEmpty()
                .asSequence()
                .filter { minRssi == null || it.level >= minRssi }
                .sortedByDescending { it.level }
                .take(cap)
            val arr = JSONArray()
            sorted.forEach { r ->
                arr.put(
                    JSONObject()
                        .put("ssid", r.SSID ?: "")
                        .put("bssid", r.BSSID ?: "")
                        .put("rssi", r.level)
                        .put("frequency", r.frequency)
                        .put("capabilities", r.capabilities ?: "")
                        .put("channelWidth", if (Build.VERSION.SDK_INT >= 23) r.channelWidth else -1)
                )
            }
            arr.toString()
        } catch (e: Exception) {
            Log.w(TAG, "wifiScanResults", e)
            "[]"
        }
    }





    @SuppressLint("MissingPermission")
    fun wifiStartScan(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!force && now - lastWifiScanAt < WIFI_SCAN_MIN_INTERVAL_MS) {
            return false
        }
        return try {
            @Suppress("DEPRECATION")
            val ok = wifiManager?.startScan() == true
            if (ok) lastWifiScanAt = now
            ok
        } catch (e: Exception) {
            Log.w(TAG, "wifiStartScan", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun wifiConnectionInfoJson(): String {
        val wm = wifiManager ?: return "{}"
        return try {
            @Suppress("DEPRECATION")
            val info = wm.connectionInfo
            val dhcp = wm.dhcpInfo
            JSONObject()
                .put("ssid", info?.ssid?.trim('"') ?: "")
                .put("bssid", info?.bssid ?: "")
                .put("rssi", info?.rssi ?: 0)
                .put("linkSpeed", info?.linkSpeed ?: 0)
                .put("ip", intToIp(info?.ipAddress ?: 0))
                .put("gateway", intToIp(dhcp?.gateway ?: 0))
                .put("netmask", intToIp(dhcp?.netmask ?: 0))
                .put("dns1", intToIp(dhcp?.dns1 ?: 0))
                .put("frequency", if (Build.VERSION.SDK_INT >= 21) info?.frequency ?: 0 else 0)
                .toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    fun wifiOpenSettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    
    @SuppressLint("MissingPermission")
    fun wifiSetEnabled(enabled: Boolean): Boolean {
        return try {
            @Suppress("DEPRECATION")
            if (wifiManager?.setWifiEnabled(enabled) == true) return true
            if (RootUtils.isRooted()) {
                val (code, _) = RootUtils.executeRootCommand(
                    if (enabled) "svc wifi enable" else "svc wifi disable",
                )
                return code == 0
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun wifiConfiguredNetworksJson(): String {
        return try {
            @Suppress("DEPRECATION", "MissingPermission")
            val list = wifiManager?.configuredNetworks.orEmpty()
            val arr = JSONArray()
            list.forEach { c ->
                arr.put(
                    JSONObject()
                        .put("networkId", c.networkId)
                        .put("ssid", c.SSID?.trim('"') ?: "")
                        .put("bssid", c.BSSID ?: "")
                        .put("status", c.status)
                        .put("priority", c.priority),
                )
            }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    @SuppressLint("MissingPermission")
    fun wifiIs5GHzBandSupported(): Boolean = try {
        if (Build.VERSION.SDK_INT >= 21) wifiManager?.is5GHzBandSupported == true else false
    } catch (_: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun wifiIsP2pSupported(): Boolean = try {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI_DIRECT)
    } catch (_: Exception) {
        false
    }

    fun btIsEnabled(): Boolean = try {
        btAdapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }

    fun btState(): Int = try {
        btAdapter?.state ?: BluetoothAdapter.STATE_OFF
    } catch (_: Exception) {
        -1
    }

    fun btLeScannerAvailable(): Boolean = try {
        btAdapter?.bluetoothLeScanner != null
    } catch (_: Exception) {
        false
    }

    fun btLeAdvertiserAvailable(): Boolean = try {
        btAdapter?.bluetoothLeAdvertiser != null
    } catch (_: Exception) {
        false
    }

    fun btSupportsMultipleAdvertisement(): Boolean = try {
        btAdapter?.isMultipleAdvertisementSupported == true
    } catch (_: Exception) {
        false
    }

    fun btSupportsOffloadedFiltering(): Boolean = try {
        btAdapter?.isOffloadedFilteringSupported == true
    } catch (_: Exception) {
        false
    }

    fun btSupportsOffloadedScanBatching(): Boolean = try {
        btAdapter?.isOffloadedScanBatchingSupported == true
    } catch (_: Exception) {
        false
    }

    fun btCapabilitiesJson(): String {
        return JSONObject()
            .put("enabled", btIsEnabled())
            .put("state", btState())
            .put("leScanner", btLeScannerAvailable())
            .put("leAdvertiser", btLeAdvertiserAvailable())
            .put("multipleAdvertisement", btSupportsMultipleAdvertisement())
            .put("offloadedFiltering", btSupportsOffloadedFiltering())
            .put("offloadedScanBatching", btSupportsOffloadedScanBatching())
            .put("name", try {
                @Suppress("MissingPermission")
                btAdapter?.name ?: ""
            } catch (_: Exception) {
                ""
            })
            .toString()
    }

    fun nfcCapabilitiesJson(): String {
        val adapter = NfcAdapter.getDefaultAdapter(context)
        return JSONObject()
            .put("available", adapter != null)
            .put("enabled", try {
                adapter?.isEnabled == true
            } catch (_: Exception) {
                false
            })
            .put("readerMode", Build.VERSION.SDK_INT >= 19)
            .put("hce", context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_NFC_HOST_CARD_EMULATION))
            .put("beam", false)
            .toString()
    }

    
    fun nfcRootSetEnabled(enabled: Boolean, callback: (String) -> Unit) {
        if (!rootAvailable()) {
            mainHandler.post {
                callback(JSONObject().put("ok", false).put("error", "no_root").toString())
            }
            return
        }
        val cmd = if (enabled) {
            "svc nfc enable; cmd nfc enable; settings put global nfc_on 1"
        } else {
            "svc nfc disable; cmd nfc disable; settings put global nfc_on 0"
        }
        rootCmd(cmd, callback)
    }

    fun rootWifiSetEnabled(enabled: Boolean, callback: (String) -> Unit) {
        rootCmd(if (enabled) "svc wifi enable" else "svc wifi disable", callback)
    }

    fun rootBtSetEnabled(enabled: Boolean, callback: (String) -> Unit) {
        rootCmd(if (enabled) "svc bluetooth enable" else "svc bluetooth disable", callback)
    }

    fun rootWifiStatus(callback: (String) -> Unit) =
        rootCmd("dumpsys wifi 2>&1 | head -n 80; cmd wifi status 2>&1 | head -n 40", callback)

    fun rootBtStatus(callback: (String) -> Unit) =
        rootCmd("dumpsys bluetooth_manager 2>&1 | head -n 80", callback)

    fun rootNfcStatus(callback: (String) -> Unit) =
        rootCmd("dumpsys nfc 2>&1 | head -n 80; settings get global nfc_on", callback)

    fun rootIwlistScan(iface: String, callback: (String) -> Unit) =
        rootCmd(
            "iw dev ${shellQuote(iface)} scan 2>&1 | head -n 200 || iwlist ${shellQuote(iface)} scan 2>&1 | head -n 200",
            callback,
        )

    fun rootHciconfig(callback: (String) -> Unit) =
        rootCmd("hciconfig -a 2>&1; btmgmt info 2>&1 | head -n 40", callback)











    @SuppressLint("MissingPermission")
    fun bleStartScan(
        scanId: String,
        onDeviceJs: (String) -> Unit,
        batchMs: Long = BLE_BATCH_MS,
        maxDevices: Int = BLE_MAX_DEVICES,
    ): Boolean {
        val scanner = btAdapter?.bluetoothLeScanner ?: return false
        stopBleScan(scanId)
        val batcher = BleBatcher(
            batchMs = batchMs.coerceIn(150L, 2000L),
            maxDevices = maxDevices.coerceIn(16, 256),
            mainHandler = mainHandler,
            onFlush = { list ->

                list.forEach { onDeviceJs(it) }
            },
        )
        bleBatchers[scanId] = batcher
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val address = result.device?.address ?: return
                    batcher.offer(address, bleScanResultToJson(result))
                } catch (e: Exception) {
                    Log.w(TAG, "ble result", e)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "ble scan failed $errorCode")
            }
        }
        bleCallbacks[scanId] = cb
        return try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setReportDelay(0L)
                .build()
            scanner.startScan(null, settings, cb)
            true
        } catch (e: Exception) {
            Log.w(TAG, "bleStartScan", e)
            bleCallbacks.remove(scanId)
            bleBatchers.remove(scanId)?.cancel()
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan(scanId: String) {
        bleBatchers.remove(scanId)?.cancel()
        val cb = bleCallbacks.remove(scanId) ?: return
        try {
            btAdapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: Exception) {
        }
    }

    fun stopAllBleScans() {
        bleCallbacks.keys.toList().forEach { stopBleScan(it) }
    }




    private class BleBatcher(
        private val batchMs: Long,
        private val maxDevices: Int,
        private val mainHandler: Handler,
        private val onFlush: (List<String>) -> Unit,
    ) {
        private val pending = LinkedHashMap<String, String>()
        private val known = LinkedHashMap<String, String>()
        private var flushPosted = false
        private val flushRunnable = Runnable { flush() }

        @Synchronized
        fun offer(address: String, json: String) {
            val prev = known[address]

            if (prev == json) return
            known[address] = json
            while (known.size > maxDevices) {
                val first = known.entries.firstOrNull()?.key ?: break
                known.remove(first)
            }
            pending[address] = json
            if (!flushPosted) {
                flushPosted = true
                mainHandler.postDelayed(flushRunnable, batchMs)
            }
        }

        @Synchronized
        private fun flush() {
            flushPosted = false
            if (pending.isEmpty()) return
            val list = pending.values.toList()
            pending.clear()
            onFlush(list)
        }

        fun cancel() {
            mainHandler.removeCallbacks(flushRunnable)
            synchronized(this) {
                pending.clear()
                known.clear()
                flushPosted = false
            }
        }
    }


    fun nfcIsAvailable(): Boolean = NfcAdapter.getDefaultAdapter(context) != null

    fun nfcIsEnabled(): Boolean = try {
        NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
    } catch (_: Exception) {
        false
    }

    fun nfcOpenSettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }


    fun irStatusJson(): String {
        return try {
            JSONObject()
                .put("stormRunning", try { com.droid.dolphy.IrWidgetRuntime.isStormRunning() } catch (_: Throwable) { false })
                .put("jammerRunning", try { com.droid.dolphy.IrWidgetRuntime.isJammerRunning() } catch (_: Throwable) { false })
                .put("hasConsumerIr", context.packageManager.hasSystemFeature("android.hardware.consumerir"))
                .toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    fun irToggleStorm(): Boolean = try {
        try { com.droid.dolphy.IrWidgetRuntime.toggleStorm() } catch (t: Throwable) {
            Log.w(TAG, "toggleStorm", t)
        }
        true
    } catch (_: Exception) {
        false
    }

    fun irToggleJammer(): Boolean = try {
        try { com.droid.dolphy.IrWidgetRuntime.toggleJammer() } catch (t: Throwable) {
            Log.w(TAG, "toggleJammer", t)
        }
        true
    } catch (_: Exception) {
        false
    }


    fun networkActiveJson(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(net)
            JSONObject()
                .put("hasInternet", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
                .put("wifi", caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
                .put("cellular", caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
                .put("vpn", caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
                .toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    fun httpRequest(
        method: String,
        url: String,
        body: String?,
        headersJson: String?,
        callback: (String) -> Unit,
    ) {
        io.execute {
            val result = try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method.uppercase()
                    connectTimeout = 15000
                    readTimeout = 20000
                    doInput = true
                    if (!headersJson.isNullOrBlank()) {
                        val obj = JSONObject(headersJson)
                        obj.keys().forEach { k -> setRequestProperty(k, obj.getString(k)) }
                    }
                    if (body != null && method.uppercase() != "GET" && method.uppercase() != "HEAD") {
                        doOutput = true
                        outputStream.use { it.write(body.toByteArray()) }
                    }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.readText() ?: ""
                JSONObject()
                    .put("ok", code in 200..299)
                    .put("code", code)
                    .put("body", text.take(500_000))
                    .toString()
            } catch (e: Exception) {
                JSONObject()
                    .put("ok", false)
                    .put("code", -1)
                    .put("body", e.message ?: "error")
                    .toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun httpDownload(
        url: String,
        destFile: java.io.File,
        headersJson: String?,
        maxBytes: Long = 50_000_000L,
        callback: (String) -> Unit,
    ) {
        io.execute {
            val result = try {
                destFile.parentFile?.mkdirs()
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20000
                    readTimeout = 60000
                    doInput = true
                    instanceFollowRedirects = true
                    if (!headersJson.isNullOrBlank()) {
                        val obj = JSONObject(headersJson)
                        obj.keys().forEach { k -> setRequestProperty(k, obj.getString(k)) }
                    }
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    JSONObject().put("ok", false).put("code", code).put("error", "http_$code").toString()
                } else {
                    var total = 0L
                    var tooLarge = false
                    conn.inputStream.use { input ->
                        destFile.outputStream().use { out ->
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                total += n
                                if (total > maxBytes) {
                                    tooLarge = true
                                    break
                                }
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    if (tooLarge) {
                        try {
                            destFile.delete()
                        } catch (_: Exception) {
                        }
                        JSONObject()
                            .put("ok", false)
                            .put("error", "file_too_large")
                            .put("maxBytes", maxBytes)
                            .toString()
                    } else {
                        JSONObject()
                            .put("ok", true)
                            .put("code", code)
                            .put("size", destFile.length())
                            .put("path", destFile.absolutePath)
                            .toString()
                    }
                }
            } catch (e: Exception) {
                try {
                    destFile.delete()
                } catch (_: Exception) {
                }
                JSONObject().put("ok", false).put("error", e.message ?: "download_failed").toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun tcpReachable(host: String, port: Int, timeoutMs: Int, callback: (Boolean) -> Unit) {
        io.execute {
            val ok = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), timeoutMs.coerceIn(200, 10000))
                    true
                }
            } catch (_: Exception) {
                false
            }
            mainHandler.post { callback(ok) }
        }
    }

    fun pingHost(host: String, timeoutMs: Int, callback: (String) -> Unit) {
        io.execute {
            val result = try {
                val start = System.currentTimeMillis()
                val ok = java.net.InetAddress.getByName(host).isReachable(timeoutMs.coerceIn(200, 10000))
                val ms = System.currentTimeMillis() - start
                JSONObject().put("ok", ok).put("host", host).put("ms", ms).toString()
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("host", host).put("error", e.message).toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun portScan(host: String, ports: IntArray, timeoutMs: Int, callback: (String) -> Unit) {
        io.execute {
            val open = JSONArray()
            val closed = JSONArray()
            val limit = ports.take(64)
            for (port in limit) {
                val ok = try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(host, port), timeoutMs.coerceIn(100, 3000))
                        true
                    }
                } catch (_: Exception) {
                    false
                }
                if (ok) open.put(port) else closed.put(port)
            }
            mainHandler.post {
                callback(
                    JSONObject()
                        .put("ok", true)
                        .put("host", host)
                        .put("open", open)
                        .put("closed", closed)
                        .put("scanned", limit.size)
                        .toString(),
                )
            }
        }
    }


    fun rootAvailable(): Boolean = try {
        RootUtils.isRooted()
    } catch (t: Throwable) {
        Log.w(TAG, "rootAvailable", t)
        false
    }

    fun rootExec(command: String, callback: (String) -> Unit) {
        io.execute {
            val json = try {
                val (code, out) = RootUtils.executeRootCommand(command)
                JSONObject()
                    .put("ok", code == 0)
                    .put("code", code)
                    .put("out", out)
                    .put("err", "")
                    .toString()
            } catch (t: Throwable) {
                Log.w(TAG, "rootExec", t)
                JSONObject()
                    .put("ok", false)
                    .put("code", -1)
                    .put("out", "")
                    .put("err", t.message ?: t.javaClass.simpleName)
                    .toString()
            }
            mainHandler.post { callback(json) }
        }
    }

    
    fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun rootCmd(cmd: String, callback: (String) -> Unit) = rootExec(cmd, callback)

    fun rootId(callback: (String) -> Unit) = rootCmd("id; whoami; echo ---; which su; ls -l \$(which su) 2>/dev/null", callback)

    fun rootWhich(bin: String, callback: (String) -> Unit) =
        rootCmd("which ${shellQuote(bin)}; type ${shellQuote(bin)} 2>/dev/null; command -v ${shellQuote(bin)}", callback)

    fun rootExists(path: String, callback: (String) -> Unit) =
        rootCmd(
            "if [ -e ${shellQuote(path)} ]; then echo EXISTS; [ -d ${shellQuote(path)} ] && echo DIR || echo FILE; " +
                "ls -ld ${shellQuote(path)} 2>/dev/null; else echo MISSING; fi",
            callback,
        )

    fun rootList(path: String, callback: (String) -> Unit) =
        rootCmd("ls -la ${shellQuote(path)} 2>&1", callback)

    fun rootStat(path: String, callback: (String) -> Unit) =
        rootCmd(
            "stat ${shellQuote(path)} 2>/dev/null || ls -ld ${shellQuote(path)}; " +
                "echo SIZE=\$(wc -c < ${shellQuote(path)} 2>/dev/null || echo 0)",
            callback,
        )

    fun rootReadText(path: String, maxBytes: Int = 2_000_000, callback: (String) -> Unit) {
        val cap = maxBytes.coerceIn(1, 5_000_000)
        rootCmd("head -c $cap ${shellQuote(path)} 2>&1", callback)
    }

    fun rootReadBase64(path: String, maxBytes: Int = 2_000_000, callback: (String) -> Unit) {
        val cap = maxBytes.coerceIn(1, 5_000_000)
        rootCmd(
            "if [ ! -f ${shellQuote(path)} ]; then echo __ERR__=not_file; exit 1; fi; " +
                "SZ=\$(wc -c < ${shellQuote(path)}); if [ \"\$SZ\" -gt $cap ]; then echo __ERR__=too_large; exit 1; fi; " +
                "base64 ${shellQuote(path)} 2>/dev/null || toybox base64 ${shellQuote(path)} 2>/dev/null",
            callback,
        )
    }

    fun rootWriteText(path: String, content: String, callback: (String) -> Unit) {
        val b64 = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        rootCmd(
            "echo ${shellQuote(b64)} | base64 -d > ${shellQuote(path)} 2>/dev/null || " +
                "echo ${shellQuote(b64)} | toybox base64 -d > ${shellQuote(path)}; " +
                "echo DONE; ls -l ${shellQuote(path)}",
            callback,
        )
    }

    fun rootWriteBase64(path: String, b64: String, callback: (String) -> Unit) {
        val clean = b64.replace("\n", "").replace("\r", "")
        rootCmd(
            "echo ${shellQuote(clean)} | base64 -d > ${shellQuote(path)} 2>/dev/null || " +
                "echo ${shellQuote(clean)} | toybox base64 -d > ${shellQuote(path)}; " +
                "echo DONE; ls -l ${shellQuote(path)}",
            callback,
        )
    }

    fun rootMkdir(path: String, callback: (String) -> Unit) =
        rootCmd("mkdir -p ${shellQuote(path)}; ls -ld ${shellQuote(path)}", callback)

    fun rootDelete(path: String, recursive: Boolean, callback: (String) -> Unit) {
        val flag = if (recursive) "-rf" else "-f"
        rootCmd("rm $flag ${shellQuote(path)}; echo DONE", callback)
    }

    fun rootCopy(src: String, dst: String, callback: (String) -> Unit) =
        rootCmd("cp -a ${shellQuote(src)} ${shellQuote(dst)}; ls -l ${shellQuote(dst)}", callback)

    fun rootMove(src: String, dst: String, callback: (String) -> Unit) =
        rootCmd("mv ${shellQuote(src)} ${shellQuote(dst)}; ls -l ${shellQuote(dst)}", callback)

    fun rootChmod(mode: String, path: String, callback: (String) -> Unit) =
        rootCmd("chmod ${shellQuote(mode)} ${shellQuote(path)}; ls -l ${shellQuote(path)}", callback)

    fun rootChown(owner: String, path: String, callback: (String) -> Unit) =
        rootCmd("chown ${shellQuote(owner)} ${shellQuote(path)}; ls -l ${shellQuote(path)}", callback)

    fun rootGetprop(key: String?, callback: (String) -> Unit) {
        if (key.isNullOrBlank()) rootCmd("getprop", callback)
        else rootCmd("getprop ${shellQuote(key)}", callback)
    }

    fun rootSetprop(key: String, value: String, callback: (String) -> Unit) =
        rootCmd("setprop ${shellQuote(key)} ${shellQuote(value)}; getprop ${shellQuote(key)}", callback)

    fun rootPackages(filter: String?, callback: (String) -> Unit) {
        val f = filter?.trim().orEmpty()
        if (f.isEmpty()) rootCmd("pm list packages 2>&1 | head -n 500", callback)
        else rootCmd("pm list packages 2>&1 | grep -i ${shellQuote(f)} | head -n 200", callback)
    }

    fun rootPmPath(pkg: String, callback: (String) -> Unit) =
        rootCmd("pm path ${shellQuote(pkg)}; dumpsys package ${shellQuote(pkg)} 2>/dev/null | head -n 40", callback)

    fun rootPmInstall(apkPath: String, callback: (String) -> Unit) =
        rootCmd("pm install -r ${shellQuote(apkPath)} 2>&1", callback)

    fun rootPmUninstall(pkg: String, callback: (String) -> Unit) =
        rootCmd("pm uninstall ${shellQuote(pkg)} 2>&1", callback)

    fun rootPmDisable(pkg: String, callback: (String) -> Unit) =
        rootCmd("pm disable-user ${shellQuote(pkg)} 2>&1 || pm disable ${shellQuote(pkg)} 2>&1", callback)

    fun rootPmEnable(pkg: String, callback: (String) -> Unit) =
        rootCmd("pm enable ${shellQuote(pkg)} 2>&1", callback)

    fun rootPmClear(pkg: String, callback: (String) -> Unit) =
        rootCmd("pm clear ${shellQuote(pkg)} 2>&1", callback)

    fun rootPmGrant(pkg: String, permission: String, callback: (String) -> Unit) =
        rootCmd("pm grant ${shellQuote(pkg)} ${shellQuote(permission)} 2>&1", callback)

    fun rootPmRevoke(pkg: String, permission: String, callback: (String) -> Unit) =
        rootCmd("pm revoke ${shellQuote(pkg)} ${shellQuote(permission)} 2>&1", callback)

    fun rootForceStop(pkg: String, callback: (String) -> Unit) =
        rootCmd("am force-stop ${shellQuote(pkg)} 2>&1; echo DONE", callback)

    fun rootAmStart(componentOrAction: String, callback: (String) -> Unit) =
        rootCmd("am start ${shellQuote(componentOrAction)} 2>&1", callback)

    fun rootAmBroadcast(action: String, callback: (String) -> Unit) =
        rootCmd("am broadcast -a ${shellQuote(action)} 2>&1", callback)

    fun rootSettingsGet(namespace: String, key: String, callback: (String) -> Unit) =
        rootCmd("settings get ${shellQuote(namespace)} ${shellQuote(key)} 2>&1", callback)

    fun rootSettingsPut(namespace: String, key: String, value: String, callback: (String) -> Unit) =
        rootCmd("settings put ${shellQuote(namespace)} ${shellQuote(key)} ${shellQuote(value)} 2>&1", callback)

    fun rootRemount(rw: Boolean, callback: (String) -> Unit) {
        val mode = if (rw) "rw" else "ro"
        rootCmd(
            "mount -o remount,$mode /system 2>&1; mount -o remount,$mode / 2>&1; " +
                "mount | grep -E ' /system | / ' | head -n 10",
            callback,
        )
    }

    fun rootMount(callback: (String) -> Unit) = rootCmd("mount 2>&1 | head -n 80", callback)

    fun rootDf(callback: (String) -> Unit) = rootCmd("df -h 2>&1", callback)

    fun rootPs(filter: String?, callback: (String) -> Unit) {
        val f = filter?.trim().orEmpty()
        if (f.isEmpty()) rootCmd("ps -A 2>/dev/null | head -n 120 || ps 2>&1 | head -n 120", callback)
        else rootCmd("ps -A 2>/dev/null | grep -i ${shellQuote(f)} | head -n 80 || ps | grep -i ${shellQuote(f)}", callback)
    }

    fun rootKill(target: String, callback: (String) -> Unit) {
        val t = target.trim()
        val cmd = if (t.toIntOrNull() != null) {
            "kill -9 $t 2>&1; echo DONE"
        } else {
            "pkill -9 ${shellQuote(t)} 2>&1 || killall ${shellQuote(t)} 2>&1; echo DONE"
        }
        rootCmd(cmd, callback)
    }

    fun rootDmesg(lines: Int, callback: (String) -> Unit) {
        val n = lines.coerceIn(10, 500)
        rootCmd("dmesg 2>/dev/null | tail -n $n", callback)
    }

    fun rootLogcat(lines: Int, callback: (String) -> Unit) {
        val n = lines.coerceIn(10, 500)
        rootCmd("logcat -d -t $n 2>&1", callback)
    }

    fun rootServiceList(callback: (String) -> Unit) =
        rootCmd("service list 2>&1 | head -n 100", callback)

    fun rootIptables(args: String, callback: (String) -> Unit) =
        rootCmd("iptables $args 2>&1", callback)

    fun rootIp(args: String, callback: (String) -> Unit) =
        rootCmd("ip $args 2>&1", callback)

    fun rootIfconfig(callback: (String) -> Unit) =
        rootCmd("ifconfig 2>/dev/null || ip addr 2>&1", callback)

    fun rootSysctl(key: String?, value: String?, callback: (String) -> Unit) {
        when {
            key.isNullOrBlank() -> rootCmd("sysctl -a 2>&1 | head -n 80", callback)
            value == null -> rootCmd("sysctl ${shellQuote(key)} 2>&1", callback)
            else -> rootCmd("sysctl -w ${shellQuote("$key=$value")} 2>&1", callback)
        }
    }

    fun rootReboot(mode: String?, callback: (String) -> Unit) {
        val m = mode?.trim()?.lowercase().orEmpty()
        val cmd = when (m) {
            "recovery" -> "reboot recovery"
            "bootloader", "fastboot" -> "reboot bootloader"
            "soft", "userspace" -> "setprop ctl.restart zygote 2>&1 || killall system_server 2>&1"
            "download" -> "reboot download"
            else -> "reboot"
        }
        rootCmd(cmd, callback)
    }

    fun rootPullToFile(remotePath: String, localFile: java.io.File, callback: (String) -> Unit) {
        io.execute {
            val result = try {
                localFile.parentFile?.mkdirs()
                val q = shellQuote(remotePath)
                val (code, out) = RootUtils.executeRootCommand(
                    "if [ ! -f $q ]; then echo __ERR__=not_file; exit 1; fi; " +
                        "base64 $q 2>/dev/null || toybox base64 $q 2>/dev/null",
                )
                if (code != 0 || out.contains("__ERR__=")) {
                    JSONObject().put("ok", false).put("code", code).put("out", out).toString()
                } else {
                    val b64 = out.lines().filter { !it.startsWith("__") && it.isNotBlank() }.joinToString("")
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    localFile.writeBytes(bytes)
                    JSONObject()
                        .put("ok", true)
                        .put("size", localFile.length())
                        .put("local", localFile.absolutePath)
                        .put("remote", remotePath)
                        .toString()
                }
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message).toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun rootPushFromFile(localFile: java.io.File, remotePath: String, callback: (String) -> Unit) {
        io.execute {
            val result = try {
                if (!localFile.isFile) {
                    JSONObject().put("ok", false).put("error", "local_missing").toString()
                } else if (localFile.length() > 8_000_000) {
                    JSONObject().put("ok", false).put("error", "too_large").toString()
                } else {
                    val b64 = android.util.Base64.encodeToString(localFile.readBytes(), android.util.Base64.NO_WRAP)
                    val (code, out) = RootUtils.executeRootCommand(
                        "echo ${shellQuote(b64)} | base64 -d > ${shellQuote(remotePath)} 2>/dev/null || " +
                            "echo ${shellQuote(b64)} | toybox base64 -d > ${shellQuote(remotePath)}; " +
                            "ls -l ${shellQuote(remotePath)}",
                    )
                    JSONObject().put("ok", code == 0).put("code", code).put("out", out).toString()
                }
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message).toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun rootExecScript(script: String, callback: (String) -> Unit) {
        val b64 = android.util.Base64.encodeToString(script.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        rootCmd(
            "TMP=/data/local/tmp/dolphy_plugin_$$.sh; " +
                "echo ${shellQuote(b64)} | base64 -d > \$TMP 2>/dev/null || echo ${shellQuote(b64)} | toybox base64 -d > \$TMP; " +
                "chmod 755 \$TMP; sh \$TMP; RC=\$?; rm -f \$TMP; exit \$RC",
            callback,
        )
    }


    fun shizukuAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) {
        false
    }

    fun shizukuHasPermission(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    fun shizukuRequestPermission() {
        try {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(PluginManager.SHIZUKU_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "shizukuRequestPermission", e)
        }
    }

    fun shizukuExec(command: String, callback: (String) -> Unit) {
        io.execute {
            val result = try {
                ShizukuHelper.runShellCommandWithOutput(command)
            } catch (t: Throwable) {
                Log.w(TAG, "shizukuExec", t)
                JSONObject()
                    .put("code", -1)
                    .put("out", "")
                    .put("err", t.message ?: t.javaClass.simpleName)
                    .toString()
            }
            mainHandler.post { callback(result) }
        }
    }


    fun shellExecSmart(command: String, callback: (String) -> Unit) {
        when {
            shizukuHasPermission() -> shizukuExec(command, callback)
            rootAvailable() -> rootExec(command, callback)
            else -> mainHandler.post {
                callback(
                    JSONObject()
                        .put("code", -1)
                        .put("out", "")
                        .put("err", "No Shizuku permission and no root")
                        .put("via", "none")
                        .toString()
                )
            }
        }
    }

    fun deviceInfoJson(): String {
        return JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("package", context.packageName)
            .toString()
    }

    private fun intToIp(value: Int): String {
        if (value == 0) return ""
        return "${value and 0xff}.${value shr 8 and 0xff}.${value shr 16 and 0xff}.${value shr 24 and 0xff}"
    }


    fun rootSelinux(callback: (String) -> Unit) = rootCmd("getenforce 2>&1", callback)

    fun rootSelinuxSet(mode: String, callback: (String) -> Unit) =
        rootCmd("setenforce ${shellQuote(mode)} 2>&1; getenforce", callback)

    fun rootMagiskModules(callback: (String) -> Unit) =
        rootCmd("ls /data/adb/modules/ 2>/dev/null | head -n 200", callback)

    fun rootMagiskToggleModule(name: String, enable: Boolean, callback: (String) -> Unit) {
        val path = "/data/adb/modules/${shellQuote(name)}"
        if (enable) rootCmd("rm -f $path/disable 2>&1; echo DONE", callback)
        else rootCmd("touch $path/disable 2>&1; echo DONE", callback)
    }

    fun rootMagiskInstallModule(zipPath: String, callback: (String) -> Unit) =
        rootCmd("magisk --install-module ${shellQuote(zipPath)} 2>&1", callback)

    fun rootBuildPropGet(key: String, callback: (String) -> Unit) =
        rootCmd("grep ${shellQuote(key)} /system/build.prop 2>&1", callback)

    fun rootBuildPropSet(key: String, value: String, callback: (String) -> Unit) =
        rootCmd(
            "mount -o remount,rw /system 2>/dev/null; " +
            "sed -i 's/^${shellQuote(key)}=.*/${shellQuote("$key=$value")}/' /system/build.prop 2>&1 || " +
            "echo ${shellQuote("$key=$value")} >> /system/build.prop; " +
            "mount -o remount,ro /system 2>/dev/null; " +
            "grep ${shellQuote(key)} /system/build.prop", callback)

    fun rootMacSpoof(iface: String, newMac: String, callback: (String) -> Unit) =
        rootCmd(
            "ip link set ${shellQuote(iface)} down 2>&1; " +
            "ip link set ${shellQuote(iface)} address ${shellQuote(newMac)} 2>&1; " +
            "ip link set ${shellQuote(iface)} up 2>&1; " +
            "ip link show ${shellQuote(iface)} | head -n 2", callback)

    fun rootDumpsys(service: String, callback: (String) -> Unit) =
        rootCmd("dumpsys ${shellQuote(service)} 2>&1 | head -n 500", callback)

    fun rootInputTap(x: Int, y: Int, callback: (String) -> Unit) =
        rootCmd("input tap $x $y 2>&1; echo DONE", callback)

    fun rootInputSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int, callback: (String) -> Unit) =
        rootCmd("input swipe $x1 $y1 $x2 $y2 $durationMs 2>&1; echo DONE", callback)

    fun rootInputText(text: String, callback: (String) -> Unit) =
        rootCmd("input text ${shellQuote(text)} 2>&1; echo DONE", callback)

    fun rootInputKeyevent(keycode: Int, callback: (String) -> Unit) =
        rootCmd("input keyevent $keycode 2>&1; echo DONE", callback)

    fun rootScreencap(destPath: String, callback: (String) -> Unit) =
        rootCmd("screencap -p ${shellQuote(destPath)} 2>&1; ls -l ${shellQuote(destPath)} 2>&1", callback)

    fun rootScreenrecord(destPath: String, durationSec: Int, callback: (String) -> Unit) {
        val dur = durationSec.coerceIn(1, 180)
        rootCmd("screenrecord --time-limit $dur ${shellQuote(destPath)} &\necho PID=\$!; echo STARTED", callback)
    }

    fun rootWmSize(callback: (String) -> Unit) = rootCmd("wm size 2>&1", callback)
    fun rootWmDensity(callback: (String) -> Unit) = rootCmd("wm density 2>&1", callback)

    fun rootWmSizeSet(w: Int, h: Int, callback: (String) -> Unit) =
        rootCmd("wm size ${w}x${h} 2>&1; wm size", callback)
    fun rootWmDensitySet(d: Int, callback: (String) -> Unit) =
        rootCmd("wm density $d 2>&1; wm density", callback)
    fun rootWmSizeReset(callback: (String) -> Unit) =
        rootCmd("wm size reset 2>&1; wm size", callback)
    fun rootWmDensityReset(callback: (String) -> Unit) =
        rootCmd("wm density reset 2>&1; wm density", callback)

    fun rootInitDList(callback: (String) -> Unit) =
        rootCmd("ls -la /system/etc/init.d/ 2>/dev/null || ls -la /data/adb/service.d/ 2>/dev/null || echo 'no init.d'", callback)

    fun rootInitDRun(script: String, callback: (String) -> Unit) =
        rootCmd("sh ${shellQuote(script)} 2>&1", callback)


    @Volatile var lastNfcTag: Tag? = null
        private set
    @Volatile private var nfcTagCallback: ((String) -> Unit)? = null

    
    fun onNfcTagDiscovered(tag: Tag) {
        lastNfcTag = tag
        val id = tag.id
        val techList = tag.techList.toList()
        val json = JSONObject()
            .put("uid", bytesToHex(id))
            .put("techList", JSONArray(techList))
            .put("hasMifareClassic", techList.contains("android.nfc.tech.MifareClassic"))
            .put("hasMifareUltralight", techList.contains("android.nfc.tech.MifareUltralight"))
            .put("hasNdef", techList.contains("android.nfc.tech.Ndef"))
            .put("hasNfcA", techList.contains("android.nfc.tech.NfcA"))
            .put("hasIsoDep", techList.contains("android.nfc.tech.IsoDep"))
        try {
            val nfcA = NfcA.get(tag)
            nfcA?.connect()
            json.put("atqa", bytesToHex(nfcA?.atqa ?: ByteArray(0)))
            json.put("sak", nfcA?.sak?.toInt() ?: 0)
            json.put("maxTransceiveLength", nfcA?.maxTransceiveLength ?: 0)
            nfcA?.close()
        } catch (_: Exception) {}
        mainHandler.post { nfcTagCallback?.invoke(json.toString()) }
    }

    fun nfcSetTagCallback(cb: ((String) -> Unit)?) { nfcTagCallback = cb }

    fun nfcLastTagJson(): String {
        val tag = lastNfcTag ?: return JSONObject().put("available", false).toString()
        return JSONObject()
            .put("available", true)
            .put("uid", bytesToHex(tag.id))
            .put("techList", JSONArray(tag.techList.toList()))
            .toString()
    }

    fun nfcReadNdef(callback: (String) -> Unit) {
        val tag = lastNfcTag
        if (tag == null) { mainHandler.post { callback(JSONObject().put("ok", false).put("error", "no_tag").toString()) }; return }
        io.execute {
            val result = try {
                val ndef = Ndef.get(tag) ?: throw Exception("not_ndef")
                ndef.connect()
                val msg = ndef.cachedNdefMessage ?: ndef.ndefMessage
                val records = JSONArray()
                msg?.records?.forEach { r ->
                    records.put(JSONObject()
                        .put("tnf", r.tnf)
                        .put("type", bytesToHex(r.type))
                        .put("payload", bytesToHex(r.payload))
                        .put("text", try { parseNdefTextPayload(r) } catch (_: Exception) { "" })
                        .put("uri", try { r.toUri()?.toString() ?: "" } catch (_: Exception) { "" }))
                }
                ndef.close()
                JSONObject().put("ok", true).put("records", records)
                    .put("type", ndef.type ?: "").put("maxSize", ndef.maxSize)
                    .put("isWritable", ndef.isWritable).toString()
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message).toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun nfcWriteNdef(textOrUri: String, isUri: Boolean, callback: (String) -> Unit) {
        val tag = lastNfcTag
        if (tag == null) { mainHandler.post { callback(JSONObject().put("ok", false).put("error", "no_tag").toString()) }; return }
        io.execute {
            val result = try {
                val record = if (isUri) {
                    NdefRecord.createUri(textOrUri)
                } else {
                    NdefRecord.createTextRecord("en", textOrUri)
                }
                val msg = NdefMessage(arrayOf(record))
                val ndef = Ndef.get(tag) ?: throw Exception("not_ndef")
                ndef.connect()
                if (!ndef.isWritable) throw Exception("not_writable")
                if (ndef.maxSize < msg.toByteArray().size) throw Exception("too_large")
                ndef.writeNdefMessage(msg)
                ndef.close()
                JSONObject().put("ok", true).put("bytesWritten", msg.toByteArray().size).toString()
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message).toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun nfcWriteNdefRaw(recordsJson: String, callback: (String) -> Unit) {
        val tag = lastNfcTag
        if (tag == null) { mainHandler.post { callback(JSONObject().put("ok", false).put("error", "no_tag").toString()) }; return }
        io.execute {
            val result = try {
                val arr = JSONArray(recordsJson)
                val records = mutableListOf<NdefRecord>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    when {
                        o.has("text") -> records.add(
                            NdefRecord.createTextRecord(
                                o.optString("lang", "en"),
                                o.getString("text"),
                            ),
                        )
                        o.has("uri") -> records.add(NdefRecord.createUri(o.getString("uri")))
                        o.has("mime") -> records.add(
                            NdefRecord.createMime(
                                o.getString("mime"),
                                hexToBytes(o.optString("payload", "")) ?: ByteArray(0),
                            ),
                        )
                        else -> {
                            val tnfShort: Short = o.optInt(
                                "tnf",
                                NdefRecord.TNF_WELL_KNOWN.toInt(),
                            ).toShort()
                            val type = hexToBytes(o.optString("type", "")) ?: ByteArray(0)
                            val id = hexToBytes(o.optString("id", "")) ?: ByteArray(0)
                            val payload = hexToBytes(o.optString("payload", "")) ?: ByteArray(0)
                            @Suppress("DEPRECATION")
                            records.add(NdefRecord(tnfShort, type, id, payload))
                        }
                    }
                }
                val msg = NdefMessage(records.toTypedArray())
                val ndef = Ndef.get(tag) ?: throw Exception("not_ndef")
                ndef.connect()
                ndef.writeNdefMessage(msg)
                ndef.close()
                JSONObject().put("ok", true).put("bytesWritten", msg.toByteArray().size).toString()
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message).toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun nfcTransceive(techType: String, hexCmd: String, callback: (String) -> Unit) {
        val tag = lastNfcTag
        if (tag == null) { mainHandler.post { callback(JSONObject().put("ok", false).put("error", "no_tag").toString()) }; return }
        val cmdBytes = hexToBytes(hexCmd)
        if (cmdBytes == null) { mainHandler.post { callback(JSONObject().put("ok", false).put("error", "bad_hex").toString()) }; return }
        io.execute {
            val result = try {
                val response = when (techType.lowercase()) {
                    "nfca" -> { val t = NfcA.get(tag) ?: throw Exception("no_nfca"); t.connect(); val r = t.transceive(cmdBytes); t.close(); r }
                    "isodep" -> { val t = IsoDep.get(tag) ?: throw Exception("no_isodep"); t.connect(); t.timeout = 5000; val r = t.transceive(cmdBytes); t.close(); r }
                    else -> throw Exception("unsupported_tech: $techType")
                }
                JSONObject().put("ok", true).put("response", bytesToHex(response)).put("size", response.size).toString()
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message).toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun nfcMifareClassicRead(sector: Int, keyHex: String?, keyType: String, callback: (String) -> Unit) {
        val tag = lastNfcTag
        if (tag == null) { mainHandler.post { callback(JSONObject().put("ok", false).put("error", "no_tag").toString()) }; return }
        io.execute {
            val result = try {
                val mfc = MifareClassic.get(tag) ?: throw Exception("not_mifare_classic")
                mfc.connect()
                val key = if (keyHex != null && keyHex.length == 12) hexToBytes(keyHex)!! else MifareClassic.KEY_DEFAULT
                val auth = if (keyType.uppercase() == "B") mfc.authenticateSectorWithKeyB(sector, key)
                else mfc.authenticateSectorWithKeyA(sector, key)
                if (!auth) throw Exception("auth_failed")
                val blocks = JSONArray()
                val firstBlock = mfc.sectorToBlock(sector)
                val blockCount = mfc.getBlockCountInSector(sector)
                for (i in 0 until blockCount) {
                    val data = mfc.readBlock(firstBlock + i)
                    blocks.put(JSONObject().put("block", firstBlock + i).put("hex", bytesToHex(data)))
                }
                mfc.close()
                JSONObject().put("ok", true).put("sector", sector).put("blocks", blocks)
                    .put("sectorCount", mfc.sectorCount).put("size", mfc.size).toString()
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message).toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun nfcMifareClassicWrite(sector: Int, blockIndex: Int, dataHex: String, keyHex: String?, keyType: String, callback: (String) -> Unit) {
        val tag = lastNfcTag
        if (tag == null) { mainHandler.post { callback(JSONObject().put("ok", false).put("error", "no_tag").toString()) }; return }
        val data = hexToBytes(dataHex)
        if (data == null || data.size != 16) { mainHandler.post { callback(JSONObject().put("ok", false).put("error", "data_must_be_16_bytes").toString()) }; return }
        io.execute {
            val result = try {
                val mfc = MifareClassic.get(tag) ?: throw Exception("not_mifare_classic")
                mfc.connect()
                val key = if (keyHex != null && keyHex.length == 12) hexToBytes(keyHex)!! else MifareClassic.KEY_DEFAULT
                val auth = if (keyType.uppercase() == "B") mfc.authenticateSectorWithKeyB(sector, key)
                else mfc.authenticateSectorWithKeyA(sector, key)
                if (!auth) throw Exception("auth_failed")
                mfc.writeBlock(blockIndex, data)
                mfc.close()
                JSONObject().put("ok", true).put("block", blockIndex).toString()
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message).toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun nfcMifareUltralightRead(startPage: Int, callback: (String) -> Unit) {
        val tag = lastNfcTag
        if (tag == null) { mainHandler.post { callback(JSONObject().put("ok", false).put("error", "no_tag").toString()) }; return }
        io.execute {
            val result = try {
                val mul = MifareUltralight.get(tag) ?: throw Exception("not_mifare_ultralight")
                mul.connect()
                val data = mul.readPages(startPage)
                mul.close()
                JSONObject().put("ok", true).put("page", startPage).put("hex", bytesToHex(data)).put("size", data.size).toString()
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message).toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    fun nfcMifareUltralightWrite(page: Int, dataHex: String, callback: (String) -> Unit) {
        val tag = lastNfcTag
        if (tag == null) { mainHandler.post { callback(JSONObject().put("ok", false).put("error", "no_tag").toString()) }; return }
        val data = hexToBytes(dataHex)
        if (data == null || data.size != 4) { mainHandler.post { callback(JSONObject().put("ok", false).put("error", "data_must_be_4_bytes").toString()) }; return }
        io.execute {
            val result = try {
                val mul = MifareUltralight.get(tag) ?: throw Exception("not_mifare_ultralight")
                mul.connect()
                mul.writePage(page, data)
                mul.close()
                JSONObject().put("ok", true).put("page", page).toString()
            } catch (e: Exception) {
                JSONObject().put("ok", false).put("error", e.message).toString()
            }
            mainHandler.post { callback(result) }
        }
    }

    private fun parseNdefTextPayload(record: NdefRecord): String {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN) return ""
        val payload = record.payload
        if (payload.isEmpty()) return ""
        val langLen = (payload[0].toInt() and 0x3F)
        return if (payload.size > 1 + langLen) String(payload, 1 + langLen, payload.size - 1 - langLen, Charsets.UTF_8) else ""
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }

    private fun hexToBytes(hex: String): ByteArray? {
        val clean = hex.replace(" ", "").replace(":", "")
        if (clean.length % 2 != 0) return null
        return try { ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() } } catch (_: Exception) { null }
    }


    @Volatile private var wifiP2pManager: WifiP2pManager? = null
    @Volatile private var wifiP2pChannel: WifiP2pManager.Channel? = null

    private fun ensureP2p(): Pair<WifiP2pManager, WifiP2pManager.Channel>? {
        if (wifiP2pManager == null) {
            wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        }
        if (wifiP2pChannel == null) {
            wifiP2pChannel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
        }
        val mgr = wifiP2pManager ?: return null
        val ch = wifiP2pChannel ?: return null
        return mgr to ch
    }

    @SuppressLint("MissingPermission")
    fun wifiP2pDiscover(callback: (String) -> Unit) {
        val (mgr, ch) = ensureP2p() ?: run {
            mainHandler.post { callback(JSONObject().put("ok", false).put("error", "no_p2p").toString()) }; return
        }
        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                mainHandler.postDelayed({
                    mgr.requestPeers(ch) { peers ->
                        val arr = JSONArray()
                        peers?.deviceList?.forEach { d ->
                            arr.put(JSONObject()
                                .put("name", d.deviceName)
                                .put("address", d.deviceAddress)
                                .put("status", d.status)
                                .put("primaryType", d.primaryDeviceType ?: ""))
                        }
                        callback(JSONObject().put("ok", true).put("peers", arr).toString())
                    }
                }, 2000)
            }
            override fun onFailure(reason: Int) {
                mainHandler.post { callback(JSONObject().put("ok", false).put("error", "discover_failed").put("reason", reason).toString()) }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun wifiP2pConnect(address: String, callback: (String) -> Unit) {
        val (mgr, ch) = ensureP2p() ?: run {
            mainHandler.post { callback(JSONObject().put("ok", false).put("error", "no_p2p").toString()) }; return
        }
        val config = WifiP2pConfig().apply { deviceAddress = address }
        mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                mainHandler.post { callback(JSONObject().put("ok", true).put("address", address).toString()) }
            }
            override fun onFailure(reason: Int) {
                mainHandler.post { callback(JSONObject().put("ok", false).put("error", "connect_failed").put("reason", reason).toString()) }
            }
        })
    }

    fun wifiP2pDisconnect(callback: (String) -> Unit) {
        val (mgr, ch) = ensureP2p() ?: run {
            mainHandler.post { callback(JSONObject().put("ok", false).put("error", "no_p2p").toString()) }; return
        }
        mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { mainHandler.post { callback(JSONObject().put("ok", true).toString()) } }
            override fun onFailure(reason: Int) { mainHandler.post { callback(JSONObject().put("ok", false).put("reason", reason).toString()) } }
        })
    }

    @SuppressLint("MissingPermission")
    fun wifiP2pGroupInfo(callback: (String) -> Unit) {
        val (mgr, ch) = ensureP2p() ?: run {
            mainHandler.post { callback(JSONObject().put("ok", false).toString()) }; return
        }
        mgr.requestGroupInfo(ch) { group ->
            val json = if (group != null) {
                val clients = JSONArray()
                group.clientList?.forEach { c ->
                    clients.put(JSONObject().put("name", c.deviceName).put("address", c.deviceAddress))
                }
                JSONObject()
                    .put("ok", true)
                    .put("networkName", group.networkName ?: "")
                    .put("passphrase", group.passphrase ?: "")
                    .put("isGroupOwner", group.isGroupOwner)
                    .put("ownerAddress", group.owner?.deviceAddress ?: "")
                    .put("ownerName", group.owner?.deviceName ?: "")
                    .put("clients", clients)
            } else JSONObject().put("ok", false).put("error", "no_group")
            mainHandler.post { callback(json.toString()) }
        }
    }


    @SuppressLint("MissingPermission")
    fun bleStartScanFiltered(
        pluginId: String,
        filterName: String?,
        filterAddress: String?,
        filterServiceUuid: String?,
        onResult: (String) -> Unit,
        batchMs: Long = BLE_BATCH_MS,
        maxDevices: Int = BLE_MAX_DEVICES,
    ): Boolean {
        val scanner = btAdapter?.bluetoothLeScanner ?: return false
        stopBleScan(pluginId)
        val filters = mutableListOf<ScanFilter>()
        val fb = ScanFilter.Builder()
        if (!filterName.isNullOrEmpty()) fb.setDeviceName(filterName)
        if (!filterAddress.isNullOrEmpty()) {
            try {
                fb.setDeviceAddress(filterAddress)
            } catch (_: Exception) {
            }
        }
        if (!filterServiceUuid.isNullOrEmpty()) {
            try {
                fb.setServiceUuid(ParcelUuid(java.util.UUID.fromString(filterServiceUuid)))
            } catch (_: Exception) {
            }
        }
        filters.add(fb.build())

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()

        val batcher = BleBatcher(
            batchMs = batchMs.coerceIn(150L, 2000L),
            maxDevices = maxDevices.coerceIn(16, 256),
            mainHandler = mainHandler,
            onFlush = { list -> list.forEach { onResult(it) } },
        )
        bleBatchers[pluginId] = batcher

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val d = result.device ?: return
                    val address = d.address ?: return
                    val record = result.scanRecord
                    val mfg = JSONArray()
                    record?.manufacturerSpecificData?.let { sparse ->
                        for (i in 0 until sparse.size()) {
                            val id = sparse.keyAt(i)
                            val data = sparse.valueAt(i) ?: continue
                            mfg.put(
                                JSONObject()
                                    .put("id", id)
                                    .put("hex", bytesToHex(data)),
                            )
                        }
                    }
                    val svcUuids = JSONArray()
                    record?.serviceUuids?.forEach { svcUuids.put(it.uuid.toString()) }
                    val json = JSONObject()
                        .put("name", d.name ?: record?.deviceName ?: "")
                        .put("address", address)
                        .put("rssi", result.rssi)
                        .put("connectable", if (Build.VERSION.SDK_INT >= 26) result.isConnectable else true)
                        .put("txPower", if (Build.VERSION.SDK_INT >= 26) result.txPower else record?.txPowerLevel ?: 0)
                        .put("serviceUuids", svcUuids)
                        .put("manufacturerData", mfg)
                        .put("raw", record?.bytes?.let { bytesToHex(it) } ?: "")
                        .toString()
                    batcher.offer(address, json)
                } catch (e: Exception) {
                    Log.w(TAG, "ble filtered result", e)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "bleStartScanFiltered failed $errorCode")
            }
        }
        bleCallbacks[pluginId] = cb
        return try {
            scanner.startScan(filters, settings, cb)
            true
        } catch (e: Exception) {
            Log.w(TAG, "bleStartScanFiltered", e)
            bleCallbacks.remove(pluginId)
            bleBatchers.remove(pluginId)?.cancel()
            false
        }
    }

    
    @SuppressLint("MissingPermission")
    fun bleScanResultToJson(result: ScanResult): String {
        val d = result.device
        val address = d?.address ?: return "{}"
        val record = result.scanRecord
        val mfg = JSONArray()
        record?.manufacturerSpecificData?.let { sparse ->
            for (i in 0 until sparse.size()) {
                val id = sparse.keyAt(i)
                val data = sparse.valueAt(i) ?: continue
                mfg.put(JSONObject().put("id", id).put("hex", bytesToHex(data)))
            }
        }
        val svcUuids = JSONArray()
        record?.serviceUuids?.forEach { svcUuids.put(it.uuid.toString()) }
        return JSONObject()
            .put("name", d?.name ?: record?.deviceName ?: "")
            .put("address", address)
            .put("rssi", result.rssi)
            .put("connectable", if (Build.VERSION.SDK_INT >= 26) result.isConnectable else true)
            .put("txPower", if (Build.VERSION.SDK_INT >= 26) result.txPower else record?.txPowerLevel ?: 0)
            .put("serviceUuids", svcUuids)
            .put("manufacturerData", mfg)
            .put("raw", record?.bytes?.let { bytesToHex(it) } ?: "")
            .toString()
    }

    companion object {
        private const val TAG = "PluginDeviceApis"
        private const val DEFAULT_WIFI_MAX = 40
        private const val WIFI_SCAN_MIN_INTERVAL_MS = 8_000L
        private const val BLE_BATCH_MS = 400L
        private const val BLE_MAX_DEVICES = 80
    }
}

