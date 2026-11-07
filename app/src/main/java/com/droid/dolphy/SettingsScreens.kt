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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.droid.dolphy.nfc.ui.*
import com.droid.dolphy.plugin.ui.PluginSettingsSections
import com.droid.dolphy.qr.*

@Composable
fun SettingsScreen(spamViewModel: SpamViewModel, dolphyViewModel: DolphyViewModel, navController: NavController) {
    val bottomScrollPadding = 132.dp
    val isDarkTheme by spamViewModel.isDarkTheme.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val selectedAccentColor by spamViewModel.accentColor.collectAsState()
    val flipperFontEnabled by spamViewModel.flipperFontEnabled.collectAsState()
    val uiScale by spamViewModel.uiScale.collectAsState()
    val cyclicDolphinAnimationEnabled by spamViewModel.cyclicDolphinAnimationEnabled.collectAsState()
    val dolphyState by dolphyViewModel.dolphyState.collectAsState()
    val context = LocalContext.current
    var showAuthDialog by remember { mutableStateOf(false) }

    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
    }
    val uriHandler = LocalUriHandler.current
    val appLanguage by spamViewModel.appLanguage.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {

        MaterialBackground(accentColor = accentColor) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = bottomScrollPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                    M3SegmentedListSectionHeader(title = "PASSPORT")


                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        contentPadding = 12.dp
                    ) {
                        DolphyScreen(dolphyViewModel, navController)
                    }
                }
            }


            item {
                Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                    M3SegmentedListSectionHeader(title = stringResource(R.string.settings_general).uppercase())

                    val generalItems = 4


                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(0, generalItems),
                        contentPadding = 16.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.settings_language),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                listOf(
                                    stringResource(R.string.language_russian) to "ru",
                                    "English" to "en",
                                ).forEachIndexed { index, (label, language) ->
                                    SegmentedButton(
                                        selected = appLanguage == language,
                                        onClick = {
                                            spamViewModel.setAppLanguage(language)
                                            applyInAppLocale(context)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }


                    val quickStartupEnabled by spamViewModel.quickStartupEnabled.collectAsState()
                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(1, generalItems),
                        contentPadding = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.settings_quick_startup),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            DolphySwitch(
                                checked = quickStartupEnabled,
                                onCheckedChange = { spamViewModel.setQuickStartupEnabled(it) }
                            )
                        }
                    }

                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(2, generalItems),
                        contentPadding = 0.dp,
                    ) {
                        SettingsItem(onClick = { navController.navigate("plugin_manager") }) {
                            Icon(Icons.Default.Extension, null, tint = accentColor, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_plugins), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.settings_plugins_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    val performanceModeEnabled by spamViewModel.performanceModeEnabled.collectAsState()
                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(3, generalItems),
                        contentPadding = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.settings_performance_mode),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    stringResource(R.string.settings_performance_mode_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DolphySwitch(
                                checked = performanceModeEnabled,
                                onCheckedChange = { spamViewModel.setPerformanceModeEnabled(it) }
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                    M3SegmentedListSectionHeader(title = stringResource(R.string.settings_personalisation).uppercase())
                    val liquidGlassEnabled by spamViewModel.liquidGlassEnabled.collectAsState()
                    val functionsMenuViewType by spamViewModel.functionsMenuViewType.collectAsState()
                    val functionsTileSize by spamViewModel.functionsTileSize.collectAsState()
                    val appStyleMode by spamViewModel.appStyleMode.collectAsState()
                    val interfaceItems = 5 + 1 + 1 + (if (functionsMenuViewType == 1) 1 else 0) + (if (liquidGlassEnabled) 1 else 0)

                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(0, interfaceItems),
                        contentPadding = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_flipper_font),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            DolphySwitch(
                                checked = flipperFontEnabled,
                                onCheckedChange = { spamViewModel.setFlipperFontEnabled(it) }
                            )
                        }
                    }


                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(1, interfaceItems),
                        contentPadding = 10.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.settings_ui_scale),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${(uiScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DolphySlider(
                                value = uiScale,
                                onValueChange = { spamViewModel.setUiScale(it) },
                                valueRange = 0.8f..1.2f,
                                steps = 7,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }


                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(2, interfaceItems),
                        contentPadding = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_cyclic_dolphin_animation),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            DolphySwitch(
                                checked = cyclicDolphinAnimationEnabled,
                                onCheckedChange = { spamViewModel.setCyclicDolphinAnimationEnabled(it) }
                            )
                        }
                    }


                    var themeMenuExpanded by remember { mutableStateOf(false) }
                    val themeMode by spamViewModel.themeMode.collectAsState()
                    val themeModeText = when (themeMode) {
                        1 -> stringResource(R.string.settings_theme_dark)
                        2 -> stringResource(R.string.settings_theme_light)
                        else -> stringResource(R.string.settings_theme_auto)
                    }
                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(3, interfaceItems),
                        contentPadding = 0.dp
                    ) {
                        Box {
                            SettingsItem(onClick = { themeMenuExpanded = true }) {
                                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(
                                    text = themeModeText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = themeMenuExpanded,
                                onDismissRequest = { themeMenuExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.settings_theme_auto)) }, onClick = { spamViewModel.setThemeMode(0); themeMenuExpanded = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.settings_theme_dark)) }, onClick = { spamViewModel.setThemeMode(1); themeMenuExpanded = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.settings_theme_light)) }, onClick = { spamViewModel.setThemeMode(2); themeMenuExpanded = false })
                            }
                        }
                    }


                    val isAdaptiveColor by spamViewModel.isAdaptiveColor.collectAsState()
                    val colorOptions = remember {
                        val base = listOf(
                            SettingColorOption("Dolphy", OrangeAccent),
                            SettingColorOption("Red", RedThemeAccent),
                            SettingColorOption("Green", GreenThemeAccent),
                            SettingColorOption("Purple", PurpleThemeAccent),
                            SettingColorOption("Cyan", CyanThemeAccent),
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            listOf(SettingColorOption("Adaptive", Color.Transparent, isAdaptive = true)) + base
                        } else {
                            base
                        }
                    }

                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(4, interfaceItems),
                        contentPadding = 12.dp,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_accent_color),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            AccentColorCarousel(
                                options = colorOptions,
                                isAdaptiveColor = isAdaptiveColor,
                                currentAccent = selectedAccentColor,
                                onSelect = { option ->
                                    if (option.isAdaptive) {
                                        spamViewModel.setAdaptiveColor(true)
                                    } else {
                                        spamViewModel.setAccentColor(option.color)
                                    }
                                },
                            )
                        }
                    }

                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(5, interfaceItems),
                        contentPadding = 12.dp,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.settings_functions_menu_view),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            val viewOptions = listOf(
                                stringResource(R.string.settings_functions_menu_containers),
                                stringResource(R.string.settings_functions_menu_tiles)
                            )
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                viewOptions.forEachIndexed { index, title ->
                                    SegmentedButton(
                                        selected = functionsMenuViewType == index,
                                        onClick = {
                                            if (functionsMenuViewType != index) {
                                                vibrate(context)
                                                spamViewModel.setFunctionsMenuViewType(index)
                                            }
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index, viewOptions.size),
                                        colors = SegmentedButtonDefaults.colors(
                                            activeContainerColor = MaterialTheme.colorScheme.primaryFixed,
                                            activeContentColor = MaterialTheme.colorScheme.onPrimaryFixed,
                                            activeBorderColor = MaterialTheme.colorScheme.primaryFixed,
                                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        icon = {},
                                        label = {
                                            Text(
                                                text = title,
                                                fontWeight = if (functionsMenuViewType == index) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (functionsMenuViewType == 1) {
                        MaterialCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = accentColor,
                            shape = getSegmentedShape(6, interfaceItems),
                            contentPadding = 10.dp
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.settings_functions_tile_size),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${functionsTileSize.toInt()} dp",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                DolphySlider(
                                    value = functionsTileSize,
                                    onValueChange = { spamViewModel.setFunctionsTileSize(it) },
                                    valueRange = 100f..200f,
                                    steps = 9,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentColor,
                                        activeTrackColor = accentColor,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(if (functionsMenuViewType == 1) 7 else 6, interfaceItems),
                        contentPadding = 12.dp,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Style,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.settings_app_style),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            val styleOptions = listOf(
                                stringResource(R.string.settings_app_style_old),
                                stringResource(R.string.settings_app_style_new)
                            )
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                styleOptions.forEachIndexed { index, title ->
                                    SegmentedButton(
                                        selected = appStyleMode == index,
                                        onClick = {
                                            if (appStyleMode != index) {
                                                vibrate(context)
                                                spamViewModel.setAppStyleMode(index)
                                                if (index == 1) {
                                                    navController.navigate("other") {
                                                        popUpTo("other") { inclusive = true }
                                                    }
                                                }
                                            }
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index, styleOptions.size),
                                        colors = SegmentedButtonDefaults.colors(
                                            activeContainerColor = MaterialTheme.colorScheme.primaryFixed,
                                            activeContentColor = MaterialTheme.colorScheme.onPrimaryFixed,
                                            activeBorderColor = MaterialTheme.colorScheme.primaryFixed,
                                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        icon = {},
                                        label = {
                                            Text(
                                                text = title,
                                                fontWeight = if (appStyleMode == index) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (liquidGlassEnabled) {
                    val liquidGlassButtonsPref by spamViewModel.liquidGlassButtons.collectAsState()
                    val liquidGlassTopBarsPref by spamViewModel.liquidGlassTopBars.collectAsState()
                    val liquidGlassNavPref by spamViewModel.liquidGlassNav.collectAsState()
                    var liquidGlassDetailsExpanded by remember { mutableStateOf(false) }

                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(if (functionsMenuViewType == 1) 8 else 7, interfaceItems),
                        contentPadding = 0.dp
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.settings_liquid_glass),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        stringResource(R.string.settings_liquid_glass_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = { liquidGlassDetailsExpanded = !liquidGlassDetailsExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (liquidGlassDetailsExpanded) {
                                            Icons.Default.KeyboardArrowUp
                                        } else {
                                            Icons.Default.KeyboardArrowDown
                                        },
                                        contentDescription = null,
                                        tint = accentColor,
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                DolphySwitch(
                                    checked = liquidGlassEnabled,
                                    onCheckedChange = { spamViewModel.setLiquidGlassEnabled(it) }
                                )
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = liquidGlassDetailsExpanded,
                                enter = androidx.compose.animation.expandVertically() +
                                    androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkVertically() +
                                    androidx.compose.animation.fadeOut(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
                                ) {
                                    val partialCount = 3
                                    val partialItems = listOf(
                                        Triple(
                                            stringResource(R.string.settings_liquid_glass_buttons),
                                            liquidGlassButtonsPref,
                                        ) { v: Boolean ->
                                            spamViewModel.setLiquidGlassButtons(v)
                                        },
                                        Triple(
                                            stringResource(R.string.settings_liquid_glass_topbars),
                                            liquidGlassTopBarsPref,
                                        ) { v: Boolean ->
                                            spamViewModel.setLiquidGlassTopBars(v)
                                        },
                                        Triple(
                                            stringResource(R.string.settings_liquid_glass_nav),
                                            liquidGlassNavPref,
                                        ) { v: Boolean ->
                                            spamViewModel.setLiquidGlassNav(v)
                                        },
                                    )
                                    partialItems.forEachIndexed { index, (title, checked, onChange) ->
                                        MaterialCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            accentColor = accentColor,
                                            shape = getSegmentedShape(index, partialCount),
                                            contentPadding = 0.dp,
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                                    .fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                DolphySwitch(
                                                    checked = checked && liquidGlassEnabled,
                                                    onCheckedChange = { onChange(it) },
                                                    enabled = liquidGlassEnabled,
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

            item {
                PluginSettingsSections(navController)
            }

            item {
                NavigationCustomizationSections(spamViewModel)
            }


            item {
                Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                    Text(
                        text = "ABOUT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = 0.dp
                    ) {
                        Column {
                            SettingsItem(onClick = { uriHandler.openUri("https://t.me/Dolphy_app_official") }) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = accentColor, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Dolphy dev", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            MaterialDivider()
                            SettingsItem(onClick = { uriHandler.openUri("https://github.com/unvoiddd/Dolphy-App") }) {
                                Icon(Icons.Default.Circle, null, tint = accentColor, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Github", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dolphy ($appVersion)\nLicensed under GNU GPL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }


            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Credits:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ZalexDev",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ars3nb",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Astrocodee",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Winterballs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Pinaplast",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }
    }
}
