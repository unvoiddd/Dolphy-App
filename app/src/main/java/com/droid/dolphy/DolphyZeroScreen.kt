package com.droid.dolphy

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.hid.HidKeyboardActivity

private data class ZeroMenu(
    val title: String,
    val sourcePath: String,
    val items: List<ZeroMenuItem>
)

private data class ZeroMenuItem(
    val glyph: String,
    val title: String,
    val action: ZeroAction
)

private sealed interface ZeroAction {
    data class OpenMenu(val menu: ZeroMenu) : ZeroAction
    data class Navigate(val route: String) : ZeroAction
    data object OpenHid : ZeroAction
    data class ShowMessage(val text: String) : ZeroAction
    data object Back : ZeroAction
}

@Composable
fun DolphyZeroScreen(navController: NavController) {
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val settingsMenu = remember {
        ZeroMenu(
            title = context.getString(R.string.zero_settings),
            sourcePath = "applications/settings/application.fam",
            items = listOf(
                ZeroMenuItem("PP", "Passport", ZeroAction.Navigate("settings")),
                ZeroMenuItem("SY", "System", ZeroAction.Navigate("settings")),
                ZeroMenuItem("CL", "Clock", ZeroAction.Navigate("settings")),
                ZeroMenuItem("AB", "About", ZeroAction.Navigate("settings")),
                ZeroMenuItem("BT", "Bluetooth", ZeroAction.Navigate("bluetooth")),
                ZeroMenuItem("PW", "Power", ZeroAction.Navigate("settings")),
                ZeroMenuItem("ST", "Storage", ZeroAction.Navigate("settings"))
            )
        )
    }
    val appsMenu = remember {
        ZeroMenu(
            title = context.getString(R.string.zero_apps),
            sourcePath = "applications/services/loader/loader.h (LOADER_APPLICATIONS_NAME)",
            items = listOf(
                ZeroMenuItem("RM", "Remote", ZeroAction.OpenHid),
                ZeroMenuItem("TV", "IR TV", ZeroAction.Navigate("ir_tv_home")),
                ZeroMenuItem("IR", "IR Storm", ZeroAction.Navigate("ir_storm")),
                ZeroMenuItem("IJ", "IR Jammer", ZeroAction.Navigate("ir_jammer")),
                ZeroMenuItem("AR", "Archive", ZeroAction.Navigate("user_ir_remotes")),
                ZeroMenuItem("JS", "JS Runner", ZeroAction.ShowMessage("JS Runner не реализован в Android-версии")),
                ZeroMenuItem("UP", "Updater", ZeroAction.ShowMessage("Updater доступен только в firmware"))
            )
        )
    }
    val mainMenu = remember {
        ZeroMenu(
            title = context.getString(R.string.zero_main_apps),
            sourcePath = "applications/main/application.fam",
            items = listOf(
                ZeroMenuItem("GP", "GPIO", ZeroAction.Navigate("bluetooth")),
                ZeroMenuItem("IR", "Infrared", ZeroAction.Navigate("ir_flipper_home")),
                ZeroMenuItem("RF", "125 kHz RFID", ZeroAction.Navigate("nfc_tools")),
                ZeroMenuItem("NF", "NFC", ZeroAction.Navigate("nfc_tools")),
                ZeroMenuItem("U2", "U2F", ZeroAction.ShowMessage("U2F не поддерживается Android-версией")),
                ZeroMenuItem("AR", "Archive", ZeroAction.Navigate("user_ir_remotes"))
            )
        )
    }
    val rootMenu = remember(mainMenu, settingsMenu, appsMenu) {
        ZeroMenu(
            title = context.getString(R.string.zero_loader),
            sourcePath = "applications/services/loader/loader_menu.c",
            items = listOf(

                ZeroMenuItem("IR", "Infrared", ZeroAction.Navigate("ir_flipper_home")),
                ZeroMenuItem("NF", "NFC", ZeroAction.Navigate("nfc_tools")),
                ZeroMenuItem("BL", "BLE Spam", ZeroAction.Navigate("bluetooth")),
                ZeroMenuItem("HD", "HID Remote", ZeroAction.OpenHid),
                ZeroMenuItem("AR", "Archive", ZeroAction.Navigate("user_ir_remotes")),
                ZeroMenuItem("ST", "Settings", ZeroAction.OpenMenu(settingsMenu)),
                ZeroMenuItem("AP", "Apps", ZeroAction.OpenMenu(appsMenu)),
                ZeroMenuItem("MN", "Main", ZeroAction.OpenMenu(mainMenu))
            )
        )
    }

    val menuStack = remember { mutableStateListOf(rootMenu) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isLocked by remember { mutableStateOf(true) }

    fun clampSelection() {
        val max = menuStack.last().items.lastIndex.coerceAtLeast(0)
        selectedIndex = selectedIndex.coerceIn(0, max)
    }

    fun goBack() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if(menuStack.size > 1) {
            menuStack.removeLast()
            selectedIndex = 0
        } else {
            navController.popBackStack()
        }
    }

    fun openAction(action: ZeroAction) {
        if(isLocked) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        when(action) {
            is ZeroAction.OpenMenu -> {
                menuStack.add(action.menu)
                selectedIndex = 0
            }
            is ZeroAction.Navigate -> navController.navigate(action.route) { launchSingleTop = true }
            ZeroAction.OpenHid -> context.startActivity(Intent(context, HidKeyboardActivity::class.java))
            is ZeroAction.ShowMessage -> {
                Toast.makeText(context, action.text, Toast.LENGTH_SHORT).show()
            }
            ZeroAction.Back -> goBack()
        }
    }

    fun moveSelection(delta: Int) {
        if(isLocked) return
        val count = menuStack.last().items.size
        if(count == 0) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        selectedIndex = (selectedIndex + delta).floorMod(count)
    }

    clampSelection()
    val currentMenu = menuStack.last()

    MaterialBackground(accentColor = accent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            DolphyIconButton(onClick = { goBack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.58f)
                        .clip(RoundedCornerShape(36.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TopActionButton(
                                icon = Icons.Default.CameraAlt,
                                label = "Apps",
                                accent = accent,
                                onClick = { if(!isLocked) openAction(ZeroAction.OpenMenu(appsMenu)) }
                            )
                            TopActionButton(
                                icon = Icons.Default.LockOpen,
                                label = if(isLocked) "Unlock" else "Lock",
                                accent = accent,
                                onClick = {
                                    isLocked = !isLocked
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        MiniFirmwareScreen(
                            accent = accent,
                            menu = currentMenu,
                            selectedIndex = selectedIndex,
                            isLocked = isLocked,
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .weight(1f)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DPad(
                                accent = accent,
                                onUp = { moveSelection(-1) },
                                onDown = { moveSelection(1) },
                                onLeft = { if(isLocked) Unit else goBack() },
                                onRight = { if(!isLocked) openAction(currentMenu.items[selectedIndex].action) },
                                onCenter = {
                                    if(isLocked) {
                                        isLocked = false
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } else {
                                        openAction(currentMenu.items[selectedIndex].action)
                                    }
                                }
                            )
                            CircleActionButton(
                                icon = Icons.AutoMirrored.Filled.Reply,
                                accent = accent,
                                size = 54.dp,
                                onClick = { goBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniFirmwareScreen(
    accent: Color,
    menu: ZeroMenu,
    selectedIndex: Int,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val visibleCount = 5
    val startIndex = (selectedIndex - 2).coerceAtLeast(0)
    val visibleItems = menu.items.drop(startIndex).take(visibleCount)
    val hasAbove = startIndex > 0
    val hasBelow = (startIndex + visibleItems.size) < menu.items.size

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.68f))
            .border(2.dp, accent, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if(isLocked) {
                Text(
                    text = "LOCKED",
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Flipper Zero style lockscreen",
                    color = accent.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Press CENTER to unlock",
                    color = accent.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Only available features launch.\nHardware-only modules stay disabled.",
                    color = accent.copy(alpha = 0.65f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall
                )
                return@Column
            }

            Text(
                text = menu.title,
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = menu.sourcePath,
                color = accent.copy(alpha = 0.65f),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            if(hasAbove) {
                Text(
                    text = "^ more",
                    color = accent.copy(alpha = 0.65f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            visibleItems.forEachIndexed { localIndex, item ->
                val absoluteIndex = startIndex + localIndex
                val selected = absoluteIndex == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if(selected) accent else Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if(selected) "> [${item.glyph}] ${item.title}" else "  [${item.glyph}] ${item.title}",
                        color = if(selected) Color.Black else accent,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if(selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            if(hasBelow) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "v more",
                    color = accent.copy(alpha = 0.65f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun TopActionButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircleActionButton(
            icon = icon,
            accent = accent,
            size = 56.dp,
            onClick = onClick
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = accent.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun DPad(
    accent: Color,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onCenter: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(156.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.95f))
            .border(1.dp, accent.copy(alpha = 0.7f), CircleShape)
    ) {
        CircleActionButton(
            icon = Icons.Default.KeyboardArrowUp,
            accent = Color.Black,
            size = 44.dp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
            onClick = onUp
        )
        CircleActionButton(
            icon = Icons.Default.KeyboardArrowDown,
            accent = Color.Black,
            size = 44.dp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            onClick = onDown
        )
        CircleActionButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            accent = Color.Black,
            size = 44.dp,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
            onClick = onLeft
        )
        CircleActionButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            accent = Color.Black,
            size = 44.dp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
            onClick = onRight
        )
        CircleActionButton(
            icon = Icons.Default.RadioButtonChecked,
            accent = Color.Black,
            size = 54.dp,
            modifier = Modifier.align(Alignment.Center),
            onClick = onCenter
        )
    }
}

@Composable
private fun CircleActionButton(
    icon: ImageVector,
    accent: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if(accent == Color.Black) Color.Transparent else accent.copy(alpha = 0.18f)
            )
            .border(1.dp, accent.copy(alpha = 0.7f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size((size.value * 0.5f).dp)
        )
    }
}

private fun Int.floorMod(divisor: Int): Int {
    if(divisor <= 0) return 0
    val result = this % divisor
    return if(result < 0) result + divisor else result
}

