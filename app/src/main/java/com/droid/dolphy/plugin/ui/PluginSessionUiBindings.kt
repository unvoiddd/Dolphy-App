package com.droid.dolphy.plugin.ui

import com.droid.dolphy.plugin.PluginSession
import com.droid.dolphy.plugin.model.PluginMediaRequest
import com.droid.dolphy.plugin.model.PluginPermissionRequest
import java.util.IdentityHashMap

internal data class PluginSessionUiBinding(
    val navigate: (String) -> Unit,
    val refresh: () -> Unit,
    val media: ((PluginMediaRequest) -> Unit)? = null,
    val permission: ((PluginPermissionRequest) -> Unit)? = null,
)

internal object PluginSessionUiBindings {
    private val bindings = IdentityHashMap<PluginSession, LinkedHashMap<Any, PluginSessionUiBinding>>()

    @Synchronized
    fun attach(session: PluginSession, owner: Any, binding: PluginSessionUiBinding) {
        bindings.getOrPut(session) { LinkedHashMap() }[owner] = binding
        apply(session)
    }

    @Synchronized
    fun detach(session: PluginSession, owner: Any) {
        val sessionBindings = bindings[session] ?: return
        sessionBindings.remove(owner)
        if (sessionBindings.isEmpty()) bindings.remove(session)
        apply(session)
    }

    private fun apply(session: PluginSession) {
        val values = bindings[session]?.values.orEmpty()
        session.navigateToScreen = values.lastOrNull()?.navigate
        session.requestUiRefresh = values.lastOrNull()?.refresh
        session.mediaRequestHandler = values.lastOrNull { it.media != null }?.media
        session.permissionRequestHandler = values.lastOrNull { it.permission != null }?.permission
    }
}
