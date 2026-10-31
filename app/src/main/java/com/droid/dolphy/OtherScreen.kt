package com.droid.dolphy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.droid.dolphy.plugin.PluginRegistry
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherScreen(navController: NavController, spamViewModel: SpamViewModel) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val baseSections = functionDestinationSections()
    var currentSections by remember(baseSections) { mutableStateOf(baseSections) }

    var draggingItem by remember { mutableStateOf<FunctionDestination?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var startItemCenter by remember { mutableStateOf(Offset.Zero) }

    val itemCenters = remember { mutableMapOf<String, Offset>() }
    val sectionHeaderCenters = remember { mutableMapOf<String, Offset>() }

    val functionsMenuViewType by spamViewModel.functionsMenuViewType.collectAsState()
    val functionsTileSize by spamViewModel.functionsTileSize.collectAsState()
    val appStyleMode by spamViewModel.appStyleMode.collectAsState()
    val pinnedRoutes by spamViewModel.pinnedCardRoutes.collectAsState()

    var menuExpandedKey by remember { mutableStateOf<String?>(null) }
    var infoDialogDestination by remember { mutableStateOf<FunctionDestination?>(null) }

    val flipperFontFamily = remember { FontFamily(Font(R.font.born2b_sporty_v2)) }

    val allDestinations = remember(currentSections) {
        currentSections.flatMap { it.second }.distinctBy { it.route }
    }

    val pinnedCards = remember(allDestinations, pinnedRoutes) {
        pinnedRoutes.mapNotNull { route -> allDestinations.find { it.route == route } }
    }

    fun handleDragMovement(delta: Offset) {
        val dragged = draggingItem ?: return
        dragOffset += delta
        val pointerCenter = startItemCenter + dragOffset

        var closestItemKey: String? = null
        var minDistance = Float.MAX_VALUE

        itemCenters.forEach { (key, center) ->
            val dist = hypot(pointerCenter.x - center.x, pointerCenter.y - center.y)
            if (dist < minDistance) {
                minDistance = dist
                closestItemKey = key
            }
        }

        val maxAllowedDist = with(density) { 140.dp.toPx() }
        if (closestItemKey != null && minDistance < maxAllowedDist) {
            var targetSection: String? = null
            var targetIndex = -1

            for ((secName, list) in currentSections) {
                val idx = list.indexOfFirst { "${secName}_${it.route}" == closestItemKey }
                if (idx >= 0) {
                    targetSection = secName
                    targetIndex = idx
                    break
                }
            }

            if (targetSection != null && targetIndex >= 0) {
                var currentSecName: String? = null
                var currentIdx = -1

                for ((secName, list) in currentSections) {
                    val idx = list.indexOfFirst { it.route == dragged.route }
                    if (idx >= 0) {
                        currentSecName = secName
                        currentIdx = idx
                        break
                    }
                }

                if (currentSecName != null && currentIdx >= 0) {
                    val isSameItem = (currentSecName == targetSection && currentIdx == targetIndex)
                    if (!isSameItem) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val updatedSections = currentSections.map { (s, list) ->
                            s to list.toMutableList()
                        }.toMutableList()

                        val sourceList = updatedSections.firstOrNull { it.first == currentSecName }?.second
                        val targetList = updatedSections.firstOrNull { it.first == targetSection }?.second

                        if (sourceList != null && targetList != null) {
                            val itemToMove = sourceList.removeAt(currentIdx)
                            val insertIdx = targetIndex.coerceIn(0, targetList.size)
                            targetList.add(insertIdx, itemToMove.copy(section = targetSection))
                            currentSections = updatedSections.map { it.first to it.second.toList() }
                        }
                    }
                }
            }
        } else {
            sectionHeaderCenters.forEach { (secName, center) ->
                val dist = hypot(pointerCenter.x - center.x, pointerCenter.y - center.y)
                if (dist < with(density) { 80.dp.toPx() }) {
                    var currentSecName: String? = null
                    var currentIdx = -1
                    for ((sec, list) in currentSections) {
                        val idx = list.indexOfFirst { it.route == dragged.route }
                        if (idx >= 0) {
                            currentSecName = sec
                            currentIdx = idx
                            break
                        }
                    }
                    if (currentSecName != null && currentSecName != secName && currentIdx >= 0) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val updatedSections = currentSections.map { (s, list) ->
                            s to list.toMutableList()
                        }.toMutableList()

                        val sourceList = updatedSections.firstOrNull { it.first == currentSecName }?.second
                        val targetList = updatedSections.firstOrNull { it.first == secName }?.second

                        if (sourceList != null && targetList != null) {
                            val itemToMove = sourceList.removeAt(currentIdx)
                            targetList.add(0, itemToMove.copy(section = secName))
                            currentSections = updatedSections.map { it.first to it.second.toList() }
                        }
                    }
                }
            }
        }
    }

    fun finishDrag() {
        val dragged = draggingItem ?: return
        for ((secName, list) in currentSections) {
            val idx = list.indexOfFirst { it.route == dragged.route }
            if (idx >= 0) {
                PluginRegistry.moveOtherCard(
                    pluginId = dragged.pluginId,
                    screenId = dragged.screenId,
                    title = dragged.title,
                    targetSection = secName,
                    newIndex = idx,
                    context = context
                )
                break
            }
        }
        draggingItem = null
        dragOffset = Offset.Zero
    }

    fun onCardClicked(item: FunctionDestination) {
        if (draggingItem == null) {
            vibrate(context)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            spamViewModel.recordCardUsage(item.route)
            openFunctionDestination(item, navController, context)
        }
    }

    MaterialBackground(accentColor = accent) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
            if (appStyleMode == 1) {
                if (functionsMenuViewType == 0) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                    ) {
                        item(key = "minimal_header") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 2.dp),
                            ) {
                                Text(
                                    text = "Dolphy",
                                    fontFamily = flipperFontFamily,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                                DolphyIconButton(
                                    onClick = {
                                        vibrate(context)
                                        navController.navigate("settings")
                                    },
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.nav_settings),
                                        tint = accent
                                    )
                                }
                            }
                        }

                        item(key = "minimal_dolphin") {
                            RandomExternalDolphinAnimation(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(155.dp)
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(20.dp))
                            )
                        }

                        if (pinnedCards.isNotEmpty()) {
                            val chunkedPinned = pinnedCards.chunked(2)
                            items(chunkedPinned.size, key = { "pinned_row_$it" }) { rowIndex ->
                                val pair = chunkedPinned[rowIndex]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        val item = pair[0]
                                        val cardKey = "pinned_${item.route}"
                                        RenderTileCard(
                                            item = item,
                                            tileSize = functionsTileSize,
                                            accent = accent,
                                            isPinned = true,
                                            isMenuExpanded = menuExpandedKey == cardKey,
                                            onMenuDismiss = { menuExpandedKey = null },
                                            onCardClick = { onCardClicked(item) },
                                            onCardLongPress = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                vibrate(context)
                                                menuExpandedKey = cardKey
                                            },
                                            onPinToggle = {
                                                vibrate(context)
                                                spamViewModel.unpinCard(item.route)
                                                menuExpandedKey = null
                                            },
                                            onInfoClick = {
                                                vibrate(context)
                                                infoDialogDestination = item
                                                menuExpandedKey = null
                                            }
                                        )
                                    }
                                    if (pair.size > 1) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            val item = pair[1]
                                            val cardKey = "pinned_${item.route}"
                                            RenderTileCard(
                                                item = item,
                                                tileSize = functionsTileSize,
                                                accent = accent,
                                                isPinned = true,
                                                isMenuExpanded = menuExpandedKey == cardKey,
                                                onMenuDismiss = { menuExpandedKey = null },
                                                onCardClick = { onCardClicked(item) },
                                                onCardLongPress = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    vibrate(context)
                                                    menuExpandedKey = cardKey
                                                },
                                                onPinToggle = {
                                                    vibrate(context)
                                                    spamViewModel.unpinCard(item.route)
                                                    menuExpandedKey = null
                                                },
                                                onInfoClick = {
                                                    vibrate(context)
                                                    infoDialogDestination = item
                                                    menuExpandedKey = null
                                                }
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        currentSections.forEach { (section, destinations) ->
                            item(key = "sec_header_$section") {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp, bottom = 2.dp)
                                        .onGloballyPositioned { coords ->
                                            val parent = coords.parentCoordinates
                                            if (parent != null) {
                                                val pos = coords.positionInParent()
                                                val size = coords.size
                                                sectionHeaderCenters[section] = Offset(pos.x + size.width / 2f, pos.y + size.height / 2f)
                                            }
                                        }
                                ) {
                                    M3SegmentedListSectionHeader(title = sectionTitle(section))
                                }
                            }

                            items(
                                count = destinations.size,
                                key = { index -> "${section}_${destinations[index].route}" }
                            ) { index ->
                                val item = destinations[index]
                                val itemKey = "${section}_${item.route}"
                                val isBeingDragged = draggingItem?.route == item.route
                                val isPinned = pinnedRoutes.contains(item.route)

                                Box {
                                    M3SegmentedListItem(
                                        index = index,
                                        count = destinations.size,
                                        headline = item.title,
                                        supporting = item.description.takeIf { it.isNotBlank() },
                                        leadingIcon = item.icon,
                                        showChevron = !item.requiresRoot,
                                        trailingContent = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isPinned) {
                                                    Icon(
                                                        imageVector = Icons.Default.PushPin,
                                                        contentDescription = "Pinned",
                                                        tint = accent,
                                                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                                    )
                                                }
                                                if (item.requiresRoot) {
                                                    RootBadge(accentColor = accent)
                                                    Spacer(Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.ChevronRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .animateItem()
                                            .onGloballyPositioned { coords ->
                                                val parent = coords.parentCoordinates
                                                if (parent != null && !isBeingDragged) {
                                                    val pos = coords.positionInParent()
                                                    val size = coords.size
                                                    itemCenters[itemKey] = Offset(pos.x + size.width / 2f, pos.y + size.height / 2f)
                                                }
                                            }
                                            .then(
                                                if (item.isPlugin) {
                                                    Modifier.pointerInput(item.route) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                vibrate(context)
                                                                draggingItem = item
                                                                dragOffset = Offset.Zero
                                                                startItemCenter = itemCenters[itemKey] ?: Offset.Zero
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                handleDragMovement(dragAmount)
                                                            },
                                                            onDragEnd = { finishDrag() },
                                                            onDragCancel = {
                                                                draggingItem = null
                                                                dragOffset = Offset.Zero
                                                            }
                                                        )
                                                    }
                                                } else {
                                                    Modifier.pointerInput(item.route) {
                                                        detectTapGestures(
                                                            onTap = { onCardClicked(item) },
                                                            onLongPress = {
                                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                vibrate(context)
                                                                menuExpandedKey = itemKey
                                                            }
                                                        )
                                                    }
                                                }
                                            ),
                                        onClick = { onCardClicked(item) }
                                    )

                                    DropdownMenu(
                                        expanded = menuExpandedKey == itemKey,
                                        onDismissRequest = { menuExpandedKey = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (isPinned) stringResource(R.string.plugin_unpin)
                                                    else stringResource(R.string.plugin_pin)
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                vibrate(context)
                                                if (isPinned) {
                                                    spamViewModel.unpinCard(item.route)
                                                } else {
                                                    spamViewModel.pinCard(item.route)
                                                }
                                                menuExpandedKey = null
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.plugin_about_title)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                vibrate(context)
                                                infoDialogDestination = item
                                                menuExpandedKey = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                    ) {
                        item(
                            key = "minimal_header",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 2.dp),
                            ) {
                                Text(
                                    text = "Dolphy",
                                    fontFamily = flipperFontFamily,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                                DolphyIconButton(
                                    onClick = {
                                        vibrate(context)
                                        navController.navigate("settings")
                                    },
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = stringResource(R.string.nav_settings),
                                        tint = accent
                                    )
                                }
                            }
                        }

                        item(
                            key = "minimal_dolphin",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            RandomExternalDolphinAnimation(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(155.dp)
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(20.dp))
                            )
                        }

                        if (pinnedCards.isNotEmpty()) {
                            items(
                                count = pinnedCards.size,
                                key = { "pinned_${pinnedCards[it].route}" }
                            ) { idx ->
                                val item = pinnedCards[idx]
                                val cardKey = "pinned_${item.route}"

                                RenderTileCard(
                                    item = item,
                                    tileSize = functionsTileSize,
                                    accent = accent,
                                    isPinned = true,
                                    isMenuExpanded = menuExpandedKey == cardKey,
                                    onMenuDismiss = { menuExpandedKey = null },
                                    onCardClick = { onCardClicked(item) },
                                    onCardLongPress = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vibrate(context)
                                        menuExpandedKey = cardKey
                                    },
                                    onPinToggle = {
                                        vibrate(context)
                                        spamViewModel.unpinCard(item.route)
                                        menuExpandedKey = null
                                    },
                                    onInfoClick = {
                                        vibrate(context)
                                        infoDialogDestination = item
                                        menuExpandedKey = null
                                    }
                                )
                            }
                        }

                        currentSections.forEach { (section, destinations) ->
                            item(
                                key = "sec_header_$section",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp, bottom = 2.dp)
                                        .onGloballyPositioned { coords ->
                                            val parent = coords.parentCoordinates
                                            if (parent != null) {
                                                val pos = coords.positionInParent()
                                                val size = coords.size
                                                sectionHeaderCenters[section] = Offset(pos.x + size.width / 2f, pos.y + size.height / 2f)
                                            }
                                        }
                                ) {
                                    M3SegmentedListSectionHeader(title = sectionTitle(section))
                                }
                            }

                            items(
                                count = destinations.size,
                                key = { index -> "${section}_${destinations[index].route}" }
                            ) { index ->
                                val item = destinations[index]
                                val itemKey = "${section}_${item.route}"
                                val isBeingDragged = draggingItem?.route == item.route
                                val isPinned = pinnedRoutes.contains(item.route)

                                val dragScale by animateFloatAsState(
                                    targetValue = if (isBeingDragged) 1.06f else 1f,
                                    label = "tile_drag_scale"
                                )

                                RenderTileCard(
                                    item = item,
                                    tileSize = functionsTileSize,
                                    accent = accent,
                                    isPinned = isPinned,
                                    isBeingDragged = isBeingDragged,
                                    dragScale = dragScale,
                                    dragOffset = dragOffset,
                                    isMenuExpanded = menuExpandedKey == itemKey,
                                    onMenuDismiss = { menuExpandedKey = null },
                                    onCardClick = { onCardClicked(item) },
                                    onCardLongPress = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vibrate(context)
                                        menuExpandedKey = itemKey
                                    },
                                    onPinToggle = {
                                        vibrate(context)
                                        if (isPinned) {
                                            spamViewModel.unpinCard(item.route)
                                        } else {
                                            spamViewModel.pinCard(item.route)
                                        }
                                        menuExpandedKey = null
                                    },
                                    onInfoClick = {
                                        vibrate(context)
                                        infoDialogDestination = item
                                        menuExpandedKey = null
                                    },
                                    modifier = Modifier
                                        .animateItem()
                                        .onGloballyPositioned { coords ->
                                            val parent = coords.parentCoordinates
                                            if (parent != null && !isBeingDragged) {
                                                val pos = coords.positionInParent()
                                                val size = coords.size
                                                itemCenters[itemKey] = Offset(pos.x + size.width / 2f, pos.y + size.height / 2f)
                                            }
                                        }
                                        .then(
                                            if (item.isPlugin) {
                                                Modifier.pointerInput(item.route) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            vibrate(context)
                                                            draggingItem = item
                                                            dragOffset = Offset.Zero
                                                            startItemCenter = itemCenters[itemKey] ?: Offset.Zero
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            handleDragMovement(dragAmount)
                                                        },
                                                        onDragEnd = { finishDrag() },
                                                        onDragCancel = {
                                                            draggingItem = null
                                                            dragOffset = Offset.Zero
                                                        }
                                                    )
                                                }
                                            } else Modifier
                                        )
                                )
                            }
                        }
                    }
                }
            } else if (functionsMenuViewType == 1) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
                ) {
                    currentSections.forEach { (section, destinations) ->
                        item(
                            key = "header_$section",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 16.dp, bottom = 4.dp)
                                    .onGloballyPositioned { coords ->
                                        val parent = coords.parentCoordinates
                                        if (parent != null) {
                                            val pos = coords.positionInParent()
                                            val size = coords.size
                                            sectionHeaderCenters[section] = Offset(pos.x + size.width / 2f, pos.y + size.height / 2f)
                                        }
                                    }
                            ) {
                                M3SegmentedListSectionHeader(title = sectionTitle(section))
                            }
                        }
                        items(
                            count = destinations.size,
                            key = { index -> "${section}_${destinations[index].route}" }
                        ) { index ->
                            val item = destinations[index]
                            val itemKey = "${section}_${item.route}"
                            val isBeingDragged = draggingItem?.route == item.route
                            val isPinned = pinnedRoutes.contains(item.route)

                            val dragScale by animateFloatAsState(
                                targetValue = if (isBeingDragged) 1.06f else 1f,
                                label = "tile_drag_scale"
                            )

                            RenderTileCard(
                                item = item,
                                tileSize = functionsTileSize,
                                accent = accent,
                                isPinned = isPinned,
                                isBeingDragged = isBeingDragged,
                                dragScale = dragScale,
                                dragOffset = dragOffset,
                                isMenuExpanded = menuExpandedKey == itemKey,
                                onMenuDismiss = { menuExpandedKey = null },
                                onCardClick = { onCardClicked(item) },
                                onCardLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vibrate(context)
                                    menuExpandedKey = itemKey
                                },
                                onPinToggle = {
                                    vibrate(context)
                                    if (isPinned) {
                                        spamViewModel.unpinCard(item.route)
                                    } else {
                                        spamViewModel.pinCard(item.route)
                                    }
                                    menuExpandedKey = null
                                },
                                onInfoClick = {
                                    vibrate(context)
                                    infoDialogDestination = item
                                    menuExpandedKey = null
                                },
                                modifier = Modifier
                                    .animateItem()
                                    .onGloballyPositioned { coords ->
                                        val parent = coords.parentCoordinates
                                        if (parent != null && !isBeingDragged) {
                                            val pos = coords.positionInParent()
                                            val size = coords.size
                                            itemCenters[itemKey] = Offset(pos.x + size.width / 2f, pos.y + size.height / 2f)
                                        }
                                    }
                                    .then(
                                        if (item.isPlugin) {
                                            Modifier.pointerInput(item.route) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        vibrate(context)
                                                        draggingItem = item
                                                        dragOffset = Offset.Zero
                                                        startItemCenter = itemCenters[itemKey] ?: Offset.Zero
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        handleDragMovement(dragAmount)
                                                    },
                                                    onDragEnd = { finishDrag() },
                                                    onDragCancel = {
                                                        draggingItem = null
                                                        dragOffset = Offset.Zero
                                                    }
                                                )
                                            }
                                        } else Modifier
                                    )
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
                ) {
                    currentSections.forEach { (section, destinations) ->
                        item(key = "header_$section") {
                            Box(
                                modifier = Modifier
                                    .padding(top = 16.dp, bottom = 4.dp)
                                    .onGloballyPositioned { coords ->
                                        val parent = coords.parentCoordinates
                                        if (parent != null) {
                                            val pos = coords.positionInParent()
                                            val size = coords.size
                                            sectionHeaderCenters[section] = Offset(pos.x + size.width / 2f, pos.y + size.height / 2f)
                                        }
                                    }
                            ) {
                                M3SegmentedListSectionHeader(title = sectionTitle(section))
                            }
                        }
                        items(
                            count = destinations.size,
                            key = { index -> "${section}_${destinations[index].route}" }
                        ) { index ->
                            val item = destinations[index]
                            val itemKey = "${section}_${item.route}"
                            val isBeingDragged = draggingItem?.route == item.route
                            val isPinned = pinnedRoutes.contains(item.route)

                            val dragScale by animateFloatAsState(
                                targetValue = if (isBeingDragged) 1.04f else 1f,
                                label = "container_drag_scale"
                            )

                            Box {
                                M3SegmentedListItem(
                                    index = index,
                                    count = destinations.size,
                                    headline = item.title,
                                    supporting = item.description.takeIf { it.isNotBlank() },
                                    leadingIcon = item.icon,
                                    showChevron = !item.requiresRoot,
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isPinned) {
                                                Icon(
                                                    imageVector = Icons.Default.PushPin,
                                                    contentDescription = "Pinned",
                                                    tint = accent,
                                                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                                )
                                            }
                                            if (item.requiresRoot) {
                                                RootBadge(accentColor = accent)
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .animateItem()
                                        .onGloballyPositioned { coords ->
                                            val parent = coords.parentCoordinates
                                            if (parent != null && !isBeingDragged) {
                                                val pos = coords.positionInParent()
                                                val size = coords.size
                                                itemCenters[itemKey] = Offset(pos.x + size.width / 2f, pos.y + size.height / 2f)
                                            }
                                        }
                                        .zIndex(if (isBeingDragged) 100f else 0f)
                                        .graphicsLayer {
                                            if (isBeingDragged) {
                                                translationX = dragOffset.x
                                                translationY = dragOffset.y
                                                scaleX = dragScale
                                                scaleY = dragScale
                                                shadowElevation = 14.dp.toPx()
                                            }
                                        }
                                        .then(
                                            if (item.isPlugin) {
                                                Modifier.pointerInput(item.route) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            vibrate(context)
                                                            draggingItem = item
                                                            dragOffset = Offset.Zero
                                                            startItemCenter = itemCenters[itemKey] ?: Offset.Zero
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            handleDragMovement(dragAmount)
                                                        },
                                                        onDragEnd = { finishDrag() },
                                                        onDragCancel = {
                                                            draggingItem = null
                                                            dragOffset = Offset.Zero
                                                        }
                                                    )
                                                }
                                            } else {
                                                Modifier.pointerInput(item.route) {
                                                    detectTapGestures(
                                                        onTap = { onCardClicked(item) },
                                                        onLongPress = {
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            vibrate(context)
                                                            menuExpandedKey = itemKey
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                        .then(
                                            if (isBeingDragged) {
                                                Modifier.border(2.dp, accent, RoundedCornerShape(16.dp))
                                            } else Modifier
                                        ),
                                    onClick = {
                                        onCardClicked(item)
                                    },
                                )

                                DropdownMenu(
                                    expanded = menuExpandedKey == itemKey,
                                    onDismissRequest = { menuExpandedKey = null },
                                    modifier = Modifier.background(
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                        RoundedCornerShape(14.dp)
                                    )
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isPinned) stringResource(R.string.action_unpin) else stringResource(R.string.action_pin),
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.PushPin,
                                                contentDescription = null,
                                                tint = accent
                                            )
                                        },
                                        onClick = {
                                            vibrate(context)
                                            if (isPinned) {
                                                spamViewModel.unpinCard(item.route)
                                            } else {
                                                spamViewModel.pinCard(item.route)
                                            }
                                            menuExpandedKey = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.action_info),
                                                fontWeight = FontWeight.Medium
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = accent
                                            )
                                        },
                                        onClick = {
                                            vibrate(context)
                                            infoDialogDestination = item
                                            menuExpandedKey = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (infoDialogDestination != null) {
        val info = infoDialogDestination!!
        AlertDialog(
            onDismissRequest = { infoDialogDestination = null },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.15f),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = info.icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = info.title,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = info.section,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = info.description.ifEmpty { stringResource(R.string.other_no_description) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                DolphyTextButton(onClick = { infoDialogDestination = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun RenderTileCard(
    item: FunctionDestination,
    tileSize: Float,
    accent: Color,
    isPinned: Boolean,
    isBeingDragged: Boolean = false,
    dragScale: Float = 1f,
    dragOffset: Offset = Offset.Zero,
    isMenuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onCardClick: () -> Unit,
    onCardLongPress: () -> Unit,
    onPinToggle: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(tileSize.dp)
            .zIndex(if (isBeingDragged) 100f else 0f)
            .graphicsLayer {
                if (isBeingDragged) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    scaleX = dragScale
                    scaleY = dragScale
                    shadowElevation = 16.dp.toPx()
                }
            }
            .then(
                if (isBeingDragged) {
                    Modifier.border(2.dp, accent, RoundedCornerShape(20.dp))
                } else Modifier
            )
            .pointerInput(item.route) {
                detectTapGestures(
                    onTap = { onCardClick() },
                    onLongPress = { onCardLongPress() }
                )
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBeingDragged) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBeingDragged) 12.dp else 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    val containerSize = (tileSize * 0.26f).coerceIn(36f, 44f).dp
                    val iconSize = (tileSize * 0.15f).coerceIn(20f, 25f).dp
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accent.copy(alpha = 0.14f),
                        modifier = Modifier.size(containerSize)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPinned) {
                            Surface(
                                shape = CircleShape,
                                color = accent.copy(alpha = 0.15f),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pinned",
                                        tint = accent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                        if (item.requiresRoot) {
                            RootBadge(accentColor = accent)
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val shortDesc = remember(item.description) {
                        val words = item.description.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                        if (words.size <= 3) {
                            item.description.trim().trimEnd(',', '.', ';', ':', '-')
                        } else {
                            words.take(3).joinToString(" ").trimEnd(',', '.', ';', ':', '-')
                        }
                    }
                    if (shortDesc.isNotEmpty()) {
                        Text(
                            text = shortDesc,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = Color.White.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = onMenuDismiss,
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(14.dp)
                )
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isPinned) stringResource(R.string.action_unpin) else stringResource(R.string.action_pin),
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = accent
                        )
                    },
                    onClick = onPinToggle
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.action_info),
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = accent
                        )
                    },
                    onClick = onInfoClick
                )
            }
        }
    }
}
