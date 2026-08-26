package com.droid.dolphy.plugin

import com.droid.dolphy.plugin.model.PluginBleModeContribution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object PluginBleModeRegistry {
    private val _modes = MutableStateFlow<List<PluginBleModeContribution>>(emptyList())
    val modes: StateFlow<List<PluginBleModeContribution>> = _modes.asStateFlow()

    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    fun register(mode: PluginBleModeContribution): Boolean {
        if (mode.modeId.isBlank() || mode.title.isBlank()) return false
        _modes.update { list ->
            (list.filterNot { it.pluginId == mode.pluginId && it.modeId == mode.modeId } + mode)
                .sortedWith(compareBy<PluginBleModeContribution> { it.order }.thenBy { it.title })
        }
        return true
    }

    fun toggle(pluginId: String, modeId: String, delayMs: Int) {
        val mode = _modes.value.firstOrNull { it.pluginId == pluginId && it.modeId == modeId } ?: return
        val key = key(pluginId, modeId)
        if (key in _active.value) stop(mode) else start(mode, delayMs)
    }

    fun stopAll() {
        _modes.value.filter { key(it.pluginId, it.modeId) in _active.value }.forEach(::stop)
    }

    fun delayChanged(delayMs: Int) {
        _modes.value.filter { key(it.pluginId, it.modeId) in _active.value }.forEach { mode ->
            PluginManager.dispatchEventToPlugin(
                mode.pluginId,
                "ble_mode_delay_changed",
                mapOf("modeId" to mode.modeId, "delayMs" to delayMs),
            )
        }
    }

    fun clearPlugin(pluginId: String) {
        _modes.value.filter { it.pluginId == pluginId && key(it.pluginId, it.modeId) in _active.value }.forEach(::stop)
        _modes.update { list -> list.filterNot { it.pluginId == pluginId } }
        _active.update { keys -> keys.filterNot { it.startsWith("$pluginId::") }.toSet() }
    }

    fun clearAll() {
        stopAll()
        _modes.value = emptyList()
        _active.value = emptySet()
    }

    private fun start(mode: PluginBleModeContribution, delayMs: Int) {
        _active.update { it + key(mode.pluginId, mode.modeId) }
        PluginManager.dispatchEventToPlugin(
            mode.pluginId,
            "ble_mode_start",
            mapOf("modeId" to mode.modeId, "delayMs" to delayMs),
        )
    }

    private fun stop(mode: PluginBleModeContribution) {
        _active.update { it - key(mode.pluginId, mode.modeId) }
        PluginManager.dispatchEventToPlugin(mode.pluginId, "ble_mode_stop", mapOf("modeId" to mode.modeId))
    }

    private fun key(pluginId: String, modeId: String): String = "$pluginId::$modeId"
}
