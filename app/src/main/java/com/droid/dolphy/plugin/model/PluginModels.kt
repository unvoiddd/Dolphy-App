package com.droid.dolphy.plugin.model

import androidx.compose.runtime.Composable

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String = "1.0",
    val description: String = "",
    val author: String = "",
    
    val isLibrary: Boolean = false,
    
    val isDesignLibrary: Boolean = false,
    val icon: String = "extension",
    val dependencies: List<String> = emptyList(),
)

data class PluginPreview(
    val manifest: PluginManifest,
    val fileName: String,
    val sizeBytes: Long,
    val runtime: String,
    val installed: Boolean,
    val capabilities: List<String> = emptyList(),
)





object OtherSections {
    const val INFRARED = "INFRARED"
    const val BLUETOOTH = "BLUETOOTH"
    const val OTHER = "ПРОЧЕЕ"
    const val PLUGINS = "ПЛАГИНЫ"

    val BUILTIN: Set<String> = setOf(INFRARED, BLUETOOTH, OTHER, PLUGINS)




    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return PLUGINS
        val t = raw.trim()
        return when (t.lowercase()) {
            "infrared", "ir", "инфракрас", "инфракрасный", "инфракрасные" -> INFRARED
            "bluetooth", "bt", "ble", "блютуз", "bluetooths" -> BLUETOOTH
            "other", "misc", "прочее", "network", "сеть" -> OTHER
            "plugins", "plugin", "плагины", "плагин" -> PLUGINS

            else -> t
        }
    }

    fun isBuiltin(section: String): Boolean = section in BUILTIN
}

data class OtherCardContribution(
    val pluginId: String,
    val section: String,
    val title: String,
    val description: String = "",
    val icon: String = "extension",
    val iconTintArgb: Long? = null,
    val screenId: String = "main",
    val order: Int = 0,
)

sealed class SettingsItemContribution {
    abstract val pluginId: String

    data class Header(
        override val pluginId: String,
        val title: String,
    ) : SettingsItemContribution()

    data class SwitchItem(
        override val pluginId: String,
        val key: String,
        val title: String,
        val subtitle: String = "",
        val defaultValue: Boolean = false,
    ) : SettingsItemContribution()

    data class SliderItem(
        override val pluginId: String,
        val key: String,
        val title: String,
        val subtitle: String = "",
        val min: Float = 0f,
        val max: Float = 100f,
        val defaultValue: Float = 50f,
        val steps: Int = 0,
    ) : SettingsItemContribution()

    data class NavItem(
        override val pluginId: String,
        val title: String,
        val subtitle: String = "",
        val icon: String = "extension",
        val screenId: String = "main",
    ) : SettingsItemContribution()

    data class CardItem(
        override val pluginId: String,
        val title: String,
        val subtitle: String = "",
        val icon: String = "extension",
        val screenId: String? = null,
    ) : SettingsItemContribution()
}

data class SettingsSectionContribution(
    val pluginId: String,
    val title: String,
    val items: List<SettingsItemContribution>,
    val order: Int = 0,
)

data class LoadedPlugin(
    val manifest: PluginManifest,
    val sourceFile: java.io.File,
    val sourceCode: String,
    val enabled: Boolean = true,
    val runtime: String = "python",
    val pinned: Boolean = false,
)

data class PluginScreenContribution(
    val pluginId: String,
    val routePattern: String,
    val screenId: String,
    val mode: String = "overlay",
    val priority: Int = 0,
)

data class PluginServiceContribution(
    val pluginId: String,
    val serviceId: String,
    val priority: Int = 0,
)

data class PluginActionHookContribution(
    val pluginId: String,
    val actionPattern: String,
    val priority: Int = 0,
)

data class PluginBleModeContribution(
    val pluginId: String,
    val modeId: String,
    val title: String,
    val description: String = "",
    val icon: String = "bluetooth_searching",
    val order: Int = 0,
)


fun interface PluginScreenContent {
    @Composable
    fun Content(onBack: () -> Unit)
}

