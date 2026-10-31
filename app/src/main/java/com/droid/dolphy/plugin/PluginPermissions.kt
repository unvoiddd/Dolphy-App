package com.droid.dolphy.plugin

/**
 * Capabilities a plugin must request before using a bridge.
 *
 * This is *not* the Android permission system: these permissions gate Dolphy's
 * own bridge APIs (root shell, file sandbox, network egress, DEX loading, ...).
 * A plugin that never declares a capability keeps working with the remaining
 * UI and Android APIs, but dangerous bridges return `permission_denied` until
 * the manifest declares the matching entry.
 *
 * Declaration:
 *   Python: `__permissions__ = ["network", "files"]`
 *   JavaScript: the same `__permissions__` block, or automatic inference from
 *   the source file for legacy plugins.
 */
enum class PluginPermission(val token: String) {
    NETWORK("network"),
    FILES("files"),
    ROOT("root"),
    SHIZUKU("shizuku"),
    SHELL("shell"),
    DEX("dex"),
    DOWNLOAD("download"),
    BLUETOOTH("bluetooth"),
    WIFI("wifi"),
    INFRARED("infrared"),
    USB("usb"),
    HOOKS("hooks"),
    BLE_SPAM("ble_spam"),
    NFC("nfc"),
    ;

    companion object {
        private val byToken: Map<String, PluginPermission> =
            entries.associateBy { it.token }

        fun fromToken(raw: String): PluginPermission? =
            byToken[raw.trim().lowercase().replace('-', '_')]

        fun parse(tokens: Iterable<String>): Set<PluginPermission> =
            tokens.mapNotNull { fromToken(it) }.toSet()
    }
}

class PluginPermissionDeniedException(
    val pluginId: String,
    val permission: PluginPermission,
) : SecurityException("Plugin $pluginId is missing permission '${permission.token}'")

/**
 * Central declaration parser and guard used by every bridge entry point.
 */
object PluginPermissions {

    private val DECLARATION = Regex("(?ms)^\\s*__permissions__\\s*=\\s*\\[(.*?)]\\s*$")
    private val QUOTED = Regex("[\"']([^\"']+)[\"']")

    /**
     * Parses the explicit `__permissions__` block. Returns null when the plugin
     * does not declare anything, so callers can fall back to inference.
     */
    fun declared(source: String): Set<PluginPermission>? {
        val body = DECLARATION.find(source)?.groupValues?.getOrNull(1) ?: return null
        return PluginPermission.parse(QUOTED.findAll(body).map { it.groupValues[1] }.toList())
    }

    /**
     * Best-effort static inference for plugins written before permissions
     * existed. It intentionally over-approximates: a false positive only shows
     * an extra capability on the consent screen.
     */
    fun infer(source: String): Set<PluginPermission> {
        val checks = linkedMapOf(
            PluginPermission.DEX to listOf("load_dex_", "loadDex", "export_app_class", "find_java_class"),
            PluginPermission.DOWNLOAD to listOf("download_assets", "httpDownload", "http_download"),
            PluginPermission.NETWORK to listOf("httpRequest", "http_request", "tcpReachable", "portScan", "pingHost"),
            PluginPermission.FILES to listOf("filesWrite", "filesRead", "asset_path", "apply_asset"),
            PluginPermission.BLE_SPAM to listOf("register_ble_mode", "registerBleMode", "bleAdvertise", "advertis"),
            PluginPermission.BLUETOOTH to listOf("bleStartScan", "bleConnect", "bluetooth", "gatt"),
            PluginPermission.WIFI to listOf("wifi", "WifiManager", "networkSuggest"),
            PluginPermission.INFRARED to listOf("infrared", "irTransmit", "ir_remote"),
            PluginPermission.USB to listOf("UsbManager", "usb", "bad_hid", "bad_usb"),
            PluginPermission.ROOT to listOf("rootExec", "root_exec"),
            PluginPermission.SHIZUKU to listOf("shizukuExec", "shizuku_exec"),
            PluginPermission.SHELL to listOf("shellExec", "shell_exec", "smartExec"),
            PluginPermission.HOOKS to listOf("hook_screen", "hook_action", "hook_surface", "registerScreenHook"),
            PluginPermission.NFC to listOf("nfcRead", "nfcWrite", "nfc_transceive"),
        )
        val inferred = checks.filterValues { tokens ->
            tokens.any { source.contains(it, ignoreCase = true) }
        }.keys
        return inferred.toSet()
    }

    fun resolve(source: String): Set<PluginPermission> =
        declared(source) ?: infer(source)

    fun require(pluginId: String, granted: Set<PluginPermission>, permission: PluginPermission) {
        if (permission !in granted) throw PluginPermissionDeniedException(pluginId, permission)
    }

    fun has(granted: Set<PluginPermission>, permission: PluginPermission): Boolean =
        permission in granted
}
