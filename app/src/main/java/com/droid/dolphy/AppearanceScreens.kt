@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.droid.dolphy

import android.bluetooth.le.*
import android.content.*
import android.os.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.droid.dolphy.nfc.ui.*
import com.droid.dolphy.qr.*

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AccentColorScreen(viewModel: SpamViewModel, onNavigateBack: () -> Unit) {
    val accentColor = MaterialTheme.colorScheme.primary
    val selectedAccentColor by viewModel.accentColor.collectAsState()
    val isAdaptive by viewModel.isAdaptiveColor.collectAsState()
    val context = LocalContext.current

    var showRgbDialog by remember { mutableStateOf(false) }
    var customR by remember(selectedAccentColor) { mutableStateOf((selectedAccentColor.red * 255).toInt().coerceIn(0, 255)) }
    var customG by remember(selectedAccentColor) { mutableStateOf((selectedAccentColor.green * 255).toInt().coerceIn(0, 255)) }
    var customB by remember(selectedAccentColor) { mutableStateOf((selectedAccentColor.blue * 255).toInt().coerceIn(0, 255)) }

    data class Theme(val name: String, val accent: Color, val gradientColors: List<Color>, val isAdaptive: Boolean = false)
    val baseThemes = listOf(
        Theme("Dolphy", OrangeAccent, listOf(OrangeAccent, Color(0xFFFF6600), Color(0xFFFF3300))),
        Theme("Red", RedThemeAccent, listOf(Color.Red, Color(0xFF990000), Color.Black)),
        Theme("Green", GreenThemeAccent, listOf(Color.Green, Color.Yellow, Color.Green)),
        Theme("Purple", PurpleThemeAccent, listOf(Color(0xFF8800FF), Color(0xFF4400FF), PurpleThemeAccent)),
        Theme("Cyan", CyanThemeAccent, listOf(Color.Cyan, Color.Blue, Color(0xFF0099FF)))
    )

    val themes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Theme("Adaptive", MaterialTheme.colorScheme.primary, listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary), true)) + baseThemes
    } else {
        baseThemes
    }

    Box(modifier = Modifier.fillMaxSize()) {

        MaterialBackground(accentColor = accentColor) {

        Column(modifier = Modifier.fillMaxSize()) {

            MaterialCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                accentColor = accentColor,
                cornerRadius = 12.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    DolphyIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accentColor)
                    }
                    Text(
                        text = stringResource(R.string.theme_selection_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    DolphyIconButton(
                        onClick = {
                            vibrate(context)
                            showRgbDialog = true
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Color", tint = accentColor)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(themes.size) { index ->
                val theme = themes[index]
                val isSelected = if (theme.isAdaptive) isAdaptive else (!isAdaptive && selectedAccentColor.toArgb() == theme.accent.toArgb())


                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = theme.accent,
                    cornerRadius = 12.dp
                ) {
                    AccentButton(
                        onClick = {
                            vibrate(context)
                            if (theme.isAdaptive) {
                                viewModel.setAdaptiveColor(true)
                            } else {
                                viewModel.setAccentColor(theme.accent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .border(if (isSelected) 3.dp else 0.dp, if (isSelected) theme.accent else Color.Transparent, RoundedCornerShape(12.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = theme.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextWhite
                            )

                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(24.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(theme.gradientColors),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                }
            }

            item {
                val isCustomSelected = !isAdaptive && themes.none { !it.isAdaptive && it.accent.toArgb() == selectedAccentColor.toArgb() }
                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = selectedAccentColor,
                    cornerRadius = 12.dp
                ) {
                    AccentButton(
                        onClick = {
                            vibrate(context)
                            showRgbDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .border(if (isCustomSelected) 3.dp else 0.dp, if (isCustomSelected) selectedAccentColor else Color.Transparent, RoundedCornerShape(12.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = if (isCustomSelected) selectedAccentColor else TextWhite)
                                Text(
                                    text = stringResource(R.string.settings_custom_color),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextWhite
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(24.dp)
                                    .background(
                                        color = if (isCustomSelected) selectedAccentColor else accentColor,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }
        }
    }

    if (showRgbDialog) {
        AlertDialog(
            onDismissRequest = { showRgbDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_custom_color),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val currentColor = Color(customR, customG, customB)
                val hexStr = String.format("#%02X%02X%02X", customR, customG, customB)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(currentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = hexStr,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Red", color = Color(0xFFFF5252), fontWeight = FontWeight.SemiBold)
                            Text("$customR", fontWeight = FontWeight.Bold)
                        }
                        DolphySlider(
                            value = customR.toFloat(),
                            onValueChange = { customR = it.toInt().coerceIn(0, 255) },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFF5252), activeTrackColor = Color(0xFFFF5252))
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Green", color = Color(0xFF69F0AE), fontWeight = FontWeight.SemiBold)
                            Text("$customG", fontWeight = FontWeight.Bold)
                        }
                        DolphySlider(
                            value = customG.toFloat(),
                            onValueChange = { customG = it.toInt().coerceIn(0, 255) },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF69F0AE), activeTrackColor = Color(0xFF69F0AE))
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Blue", color = Color(0xFF448AFF), fontWeight = FontWeight.SemiBold)
                            Text("$customB", fontWeight = FontWeight.Bold)
                        }
                        DolphySlider(
                            value = customB.toFloat(),
                            onValueChange = { customB = it.toInt().coerceIn(0, 255) },
                            valueRange = 0f..255f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF448AFF), activeTrackColor = Color(0xFF448AFF))
                        )
                    }
                }
            },
            confirmButton = {
                DolphyButton(
                    onClick = {
                        vibrate(context)
                        viewModel.setAdaptiveColor(false)
                        viewModel.setAccentColor(Color(customR, customG, customB))
                        showRgbDialog = false
                    }
                ) {
                    Text(stringResource(R.string.color_picker_select))
                }
            },
            dismissButton = {
                DolphyTextButton(onClick = { showRgbDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ThemeModeScreen(viewModel: SpamViewModel, onNavigateBack: () -> Unit) {
    val accentColor = MaterialTheme.colorScheme.primary
    val themeMode by viewModel.themeMode.collectAsState()
    val context = LocalContext.current


    data class ThemeModeOption(val mode: Int, val name: String, val description: String)
    val themeModes = listOf(
        ThemeModeOption(0, stringResource(R.string.settings_theme_auto), stringResource(R.string.theme_mode_auto_desc)),
        ThemeModeOption(1, stringResource(R.string.settings_theme_dark), stringResource(R.string.theme_mode_dark_desc)),
        ThemeModeOption(2, stringResource(R.string.settings_theme_light), stringResource(R.string.theme_mode_light_desc))
    )

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accentColor) {
            Column(modifier = Modifier.fillMaxSize()) {

                MaterialCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    accentColor = accentColor,
                    cornerRadius = 12.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    ) {
                        DolphyIconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accentColor)
                        }
                        Text(
                            text = stringResource(R.string.theme_selection_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(themeModes.size) { index ->
                        val option = themeModes[index]
                        val isSelected = themeMode == option.mode

                        MaterialCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                            cornerRadius = 12.dp
                        ) {
                            AccentButton(
                                onClick = {
                                    vibrate(context)
                                    viewModel.setThemeMode(option.mode)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .border(
                                        if (isSelected) 2.dp else 0.dp,
                                        if (isSelected) accentColor else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = option.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = accentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
