package com.droid.dolphy.plugin

import android.content.Context

data class PluginContext(
    val androidContext: Context,
    val pluginId: String,
    val navigateToScreen: (String) -> Unit
)

