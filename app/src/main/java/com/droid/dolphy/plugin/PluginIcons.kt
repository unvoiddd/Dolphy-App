package com.droid.dolphy.plugin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.ui.graphics.vector.ImageVector

object PluginIcons {

    val knownNames: List<String> = listOf(
        "extension", "plugin", "bluetooth", "bt", "ble", "bluetooth_searching", "bluetooth_audio",
        "bluetooth_disabled", "wifi", "wifi_off", "wifi_tethering", "nfc", "ir", "infrared", "remote",
        "tv", "computer", "router", "lan", "network", "hub", "qr", "qr_code", "cast", "keyboard", "hid",
        "chat", "settings", "play", "stop", "refresh", "search", "delete", "add", "info", "warning",
        "error", "check", "close", "home", "folder", "file", "code", "terminal", "shell", "security",
        "root", "shizuku", "power", "link", "language", "web", "list", "tune", "speed", "sensors",
        "memory", "phone", "arrow_back", "back", "arrow_right", "send", "circle", "more",
    )

    fun resolve(name: String?): ImageVector {
        if (name.isNullOrBlank()) return Icons.Default.Extension
        return when (name.trim().lowercase().replace("-", "_").replace(" ", "_")) {
            "extension", "plugin" -> Icons.Default.Extension
            "bluetooth", "bt" -> Icons.Default.Bluetooth
            "bluetooth_searching", "ble" -> Icons.Default.BluetoothSearching
            "bluetooth_audio" -> Icons.Default.BluetoothAudio
            "bluetooth_disabled" -> Icons.Default.BluetoothDisabled
            "wifi", "wi_fi" -> Icons.Default.Wifi
            "wifi_off" -> Icons.Default.WifiOff
            "wifi_tethering" -> Icons.Default.WifiTethering
            "nfc" -> Icons.Outlined.Nfc
            "ir", "infrared", "remote", "tv" -> Icons.Default.Tv
            "computer" -> Icons.Default.Computer
            "router", "lan" -> Icons.Default.Router
            "network", "hub" -> Icons.Default.Hub
            "qr", "qr_code" -> Icons.Default.QrCodeScanner
            "cast" -> Icons.Default.Cast
            "keyboard", "hid" -> Icons.Default.Keyboard
            "chat" -> Icons.Default.Chat
            "settings" -> Icons.Default.Settings
            "play" -> Icons.Default.PlayArrow
            "stop" -> Icons.Default.Stop
            "refresh" -> Icons.Default.Refresh
            "search" -> Icons.Default.Search
            "delete", "trash" -> Icons.Default.Delete
            "add" -> Icons.Default.Add
            "info" -> Icons.Default.Info
            "warning" -> Icons.Default.Warning
            "error" -> Icons.Default.Error
            "check" -> Icons.Default.Check
            "close" -> Icons.Default.Close
            "home" -> Icons.Default.Home
            "folder" -> Icons.Default.Folder
            "file" -> Icons.Default.InsertDriveFile
            "code", "terminal", "shell" -> Icons.Default.Terminal
            "security", "root", "shizuku" -> Icons.Default.Security
            "power" -> Icons.Default.PowerSettingsNew
            "link" -> Icons.Default.Link
            "language", "web" -> Icons.Default.Language
            "list" -> Icons.Default.List
            "tune" -> Icons.Default.Tune
            "speed" -> Icons.Default.Speed
            "sensors" -> Icons.Default.Sensors
            "memory" -> Icons.Default.Memory
            "phone" -> Icons.Default.PhoneAndroid
            "arrow_back", "back" -> Icons.AutoMirrored.Filled.ArrowBack
            "arrow_right", "chevron_right" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
            "send" -> Icons.AutoMirrored.Filled.Send
            "circle" -> Icons.Default.Circle
            "more" -> Icons.Default.MoreHoriz
            else -> Icons.Default.Extension
        }
    }
}

