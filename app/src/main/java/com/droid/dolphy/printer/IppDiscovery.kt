package com.droid.dolphy.printer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

class IppDiscovery(
    context: Context,
    private val scope: CoroutineScope,
    private val onPrinter: (IppPrinter) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val listeners = ConcurrentHashMap<String, NsdManager.DiscoveryListener>()
    private val queue = Channel<Pair<String, NsdServiceInfo>>(Channel.UNLIMITED)
    private var resolverJob: Job? = null

    fun start() {
        stop()
        resolverJob = scope.launch(Dispatchers.Main.immediate) {
            for ((type, service) in queue) {
                resolve(type, service)?.let(onPrinter)
            }
        }
        listOf("_ipp._tcp.", "_ipps._tcp.").forEach { type ->
            val listener = createListener(type)
            listeners[type] = listener
            runCatching {
                manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            }.onFailure { onError(it.message ?: "NSD error") }
        }
    }

    fun stop() {
        listeners.values.forEach { listener -> runCatching { manager.stopServiceDiscovery(listener) } }
        listeners.clear()
        resolverJob?.cancel()
        resolverJob = null
    }

    private fun createListener(type: String) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            queue.trySend(type to serviceInfo)
        }
        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            onError("NSD $errorCode")
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
    }

    private suspend fun resolve(type: String, service: NsdServiceInfo): IppPrinter? {
        val deferred = CompletableDeferred<NsdServiceInfo?>()
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                deferred.complete(null)
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                deferred.complete(serviceInfo)
            }
        }
        runCatching { manager.resolveService(service, listener) }.onFailure { deferred.complete(null) }
        val resolved = withTimeoutOrNull(5_000) { deferred.await() } ?: return null
        val address = resolved.host?.hostAddress ?: return null
        val host = if (address.contains(':')) "[${address.replace("%", "%25")}]" else address
        val attributes = resolved.attributes
        val path = attributes["rp"]?.toString(Charsets.UTF_8)?.trim('/')?.ifBlank { "ipp/print" } ?: "ipp/print"
        val model = attributes["ty"]?.toString(Charsets.UTF_8)
        val location = attributes["note"]?.toString(Charsets.UTF_8)
        val scheme = if (type.startsWith("_ipps")) "ipps" else "ipp"
        val uri = "$scheme://$host:${resolved.port}/$path"
        return IppPrinter(
            name = model?.takeIf { it.isNotBlank() } ?: resolved.serviceName,
            uri = uri,
            location = location,
            model = model,
        )
    }
}

