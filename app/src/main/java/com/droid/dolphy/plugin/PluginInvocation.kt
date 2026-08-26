package com.droid.dolphy.plugin

data class PluginServiceResult(
    val pluginId: String,
    val valueJson: String,
)

data class PluginActionDecision(
    val cancelled: Boolean = false,
    val handled: Boolean = false,
    val payloadJson: String,
    val resultJson: String? = null,
    val pluginId: String? = null,
)
