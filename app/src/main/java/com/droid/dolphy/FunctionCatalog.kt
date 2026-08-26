package com.droid.dolphy

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.droid.dolphy.hid.HidKeyboardActivity
import com.droid.dolphy.plugin.PluginIcons
import com.droid.dolphy.plugin.PluginRegistry
import com.droid.dolphy.plugin.model.OtherSections

data class FunctionDestination(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val route: String,
    val section: String,
    val requiresRoot: Boolean = false,
)

@Composable
fun functionDestinationSections(): List<Pair<String, List<FunctionDestination>>> {
    val revision by PluginRegistry.revision.collectAsState()
    val pluginCards by PluginRegistry.otherCards.collectAsState()
    val bySection = androidx.compose.runtime.remember(revision, pluginCards) { PluginRegistry.otherBySection() }

    fun plugins(section: String): List<FunctionDestination> {
        return bySection[section].orEmpty().map { card ->
            FunctionDestination(
                icon = PluginIcons.resolve(card.icon),
                title = card.title,
                description = card.description,
                route = "plugin/${card.pluginId}/${card.screenId}",
                section = section,
            )
        }
    }

    val sections = mutableListOf<Pair<String, List<FunctionDestination>>>()
    sections += OtherSections.INFRARED to listOf(
        FunctionDestination(Icons.Default.Computer, stringResource(R.string.ir_flipper_remotes), stringResource(R.string.other_ir_flipper_desc) + " + Телевизоры", "other/ir_flipper_home", OtherSections.INFRARED),
        FunctionDestination(Icons.Default.Warning, stringResource(R.string.ir_storm), stringResource(R.string.other_ir_storm_desc), "other/ir_storm", OtherSections.INFRARED),
        FunctionDestination(Icons.Default.WifiTethering, stringResource(R.string.ir_jammer), stringResource(R.string.other_ir_jammer_desc), "other/ir_jammer", OtherSections.INFRARED),
        FunctionDestination(Icons.Default.Tv, stringResource(R.string.ir_universal_remotes), stringResource(R.string.other_ir_universal_desc), "other/universal_remotes_home", OtherSections.INFRARED),
    ) + plugins(OtherSections.INFRARED)
    sections += OtherSections.BLUETOOTH to listOf(
        FunctionDestination(Icons.Default.BluetoothAudio, stringResource(R.string.audio_scanner_title), stringResource(R.string.audio_scanner_desc), "other/audio_scanner", OtherSections.BLUETOOTH),
        FunctionDestination(Icons.Default.ElectricScooter, stringResource(R.string.scooter_hack_title), stringResource(R.string.scooter_hack_card_desc), "other/scooter_hack", OtherSections.BLUETOOTH),
        FunctionDestination(Icons.Default.Bluetooth, "NRF Scanner", stringResource(R.string.nrf_scanner_description), "other/nrf_scanner", OtherSections.BLUETOOTH),
        FunctionDestination(Icons.Default.Chat, "Dolphy Chat", stringResource(R.string.other_dolphy_chat_desc), "other/dolphy_chat_global", OtherSections.BLUETOOTH),
        FunctionDestination(Icons.Default.Keyboard, stringResource(R.string.other_hid), stringResource(R.string.other_hid_desc), "hid", OtherSections.BLUETOOTH),
        FunctionDestination(Icons.Default.BluetoothDisabled, "Bluetooth Jammer", "L2CAP flood attack", "other/bluetooth_jammer", OtherSections.BLUETOOTH),
    ) + plugins(OtherSections.BLUETOOTH)
    sections += OtherSections.OTHER to listOf(
        FunctionDestination(Icons.Outlined.Nfc, stringResource(R.string.other_nfc), stringResource(R.string.other_nfc_desc), "other/nfc_tools", OtherSections.OTHER),
        FunctionDestination(Icons.Default.Terminal, stringResource(R.string.other_bad_usb), stringResource(R.string.other_bad_usb_desc), "other/bad_usb", OtherSections.OTHER, requiresRoot = true),
        FunctionDestination(Icons.Default.QrCodeScanner, stringResource(R.string.other_qr_tools), stringResource(R.string.other_qr_tools_desc), "other/qr_tools", OtherSections.OTHER),
        FunctionDestination(Icons.Filled.WifiOff, "WI-FI Attacks", stringResource(R.string.network_hub_card_description), "other/network_diagnostic_hub", OtherSections.OTHER),
        FunctionDestination(Icons.Default.Cast, stringResource(R.string.smarttv_cast_title), stringResource(R.string.smarttv_cast_card_description), "other/smarttv_cast", OtherSections.OTHER),
        FunctionDestination(Icons.Default.Router, stringResource(R.string.lan_tools_title), stringResource(R.string.lan_tools_subtitle), "other/lan_scanner", OtherSections.OTHER),
    ) + plugins(OtherSections.OTHER)

    val pluginSection = plugins(OtherSections.PLUGINS)
    if (pluginSection.isNotEmpty()) sections += OtherSections.PLUGINS to pluginSection
    bySection.keys.filter { !OtherSections.isBuiltin(it) }.sorted().forEach { section ->
        sections += section to plugins(section)
    }
    return sections.filter { it.second.isNotEmpty() }
}

fun openFunctionDestination(destination: FunctionDestination, navController: NavController, context: android.content.Context) {
    if (destination.route == "hid") {
        context.startActivity(Intent(context, HidKeyboardActivity::class.java))
    } else {
        navController.navigate(destination.route)
    }
}
