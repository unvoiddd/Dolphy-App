package com.droid.dolphy.plugin

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

data class PluginDownloadRequest(
    val id: Long,
    val pluginId: String,
    val pluginName: String,
    val fileCount: Int,
    val totalBytes: Long,
)

object PluginDownloadPolicy {
    private const val PREFS = "plugin_security"
    private const val KEY_ASK = "ask_before_download"
    private lateinit var appContext: Context
    private val pendingActions = linkedMapOf<Long, (Boolean) -> Unit>()
    private val queue = ArrayDeque<PluginDownloadRequest>()
    private val _pending = MutableStateFlow<PluginDownloadRequest?>(null)
    val pending: StateFlow<PluginDownloadRequest?> = _pending.asStateFlow()
    private val _askBeforeDownload = MutableStateFlow(true)
    val askBeforeDownload: StateFlow<Boolean> = _askBeforeDownload.asStateFlow()
    private var nextId = 1L

    fun initialize(context: Context) {
        appContext = context.applicationContext
        _askBeforeDownload.value = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASK, true)
    }

    fun setAskBeforeDownload(enabled: Boolean) {
        _askBeforeDownload.value = enabled
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASK, enabled).apply()
    }

    @Synchronized
    fun request(pluginId: String, fileCount: Int, totalBytes: Long, action: (Boolean) -> Unit) {
        if (!_askBeforeDownload.value) {
            action(true)
            return
        }
        val request = PluginDownloadRequest(
            id = nextId++,
            pluginId = pluginId,
            pluginName = PluginManager.getManifest(pluginId)?.name ?: pluginId,
            fileCount = fileCount.coerceAtLeast(1),
            totalBytes = totalBytes.coerceAtLeast(0L),
        )
        pendingActions[request.id] = action
        queue.addLast(request)
        publishNext()
    }

    @Synchronized
    fun resolve(id: Long, allowed: Boolean) {
        val action = pendingActions.remove(id)
        if (_pending.value?.id == id) _pending.value = null
        action?.invoke(allowed)
        publishNext()
    }

    @Synchronized
    private fun publishNext() {
        if (_pending.value != null) return
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (pendingActions.containsKey(next.id)) {
                _pending.value = next
                return
            }
        }
    }
}
