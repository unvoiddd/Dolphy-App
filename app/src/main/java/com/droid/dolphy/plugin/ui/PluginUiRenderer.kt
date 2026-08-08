package com.droid.dolphy.plugin.ui

import com.droid.dolphy.DolphyButton
import com.droid.dolphy.DolphyIconButton

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.droid.dolphy.ConnectedButtonGroup
import com.droid.dolphy.DolphyLinearProgressIndicator
import com.droid.dolphy.DolphySlider
import com.droid.dolphy.DolphySwitch
import com.droid.dolphy.ExpressiveBounceButton
import com.droid.dolphy.ExpressiveSplitButton
import com.droid.dolphy.MaterialButton
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.MaterialTabRow
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.WavyCircularProgressIndicator
import com.droid.dolphy.getSegmentedShape
import com.droid.dolphy.plugin.PluginIcons
import com.droid.dolphy.plugin.model.UiNode

@Composable
fun PluginUiRenderer(
    node: UiNode,
    accent: Color,
    onBack: () -> Unit,
    onCallback: (String?, Any?) -> Unit,
) {
    when (node) {
        is UiNode.Scaffold -> {
            Column(Modifier.fillMaxSize()) {
                val bar = node.topBar
                if (bar != null) {
                    SectionTopBar(
                        title = bar.title,
                        onBack = if (bar.showBack) onBack else null,
                        accentColor = accent,
                        actions = {
                            bar.actions.forEach { action ->
                                DolphyIconButton(onClick = { onCallback(action.onClickId, null) }) {
                                    Icon(
                                        PluginIcons.resolve(action.icon),
                                        contentDescription = null,
                                        tint = accent,
                                    )
                                }
                            }
                        },
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (val c = node.content) {
                        is UiNode.LazyColumn -> {

                            LazyColumn(
                                Modifier
                                    .fillMaxSize()
                                    .padding(c.padding.dp),
                                verticalArrangement = Arrangement.spacedBy(c.spacing.dp),
                                contentPadding = PaddingValues(bottom = 120.dp),
                            ) {
                                itemsIndexed(
                                    items = c.children,
                                    key = { index, _ -> index },
                                ) { _, child ->
                                    PluginUiRenderer(child, accent, onBack, onCallback)
                                }
                            }
                        }
                        is UiNode.Column -> {
                            val scroll = rememberScrollState()
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scroll)
                                    .padding(bottom = 120.dp)
                                    .padding(c.padding.dp),
                                verticalArrangement = Arrangement.spacedBy(c.spacing.dp),
                            ) {
                                c.children.forEach { child ->
                                    PluginUiRenderer(child, accent, onBack, onCallback)
                                }
                            }
                        }
                        else -> {
                            val scroll = rememberScrollState()
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scroll)
                                    .padding(bottom = 120.dp),
                            ) {
                                PluginUiRenderer(c, accent, onBack, onCallback)
                            }
                        }
                    }
                }
                node.fab?.let { fab ->
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.CenterEnd) {
                        PluginUiRenderer(fab, accent, onBack, onCallback)
                    }
                }
            }
        }

        is UiNode.Column -> {
            val scroll = rememberScrollState()
            val mod = Modifier
                .then(
                    if (node.fillMaxSize) {
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(bottom = 120.dp)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
                .padding(node.padding.dp)
            Column(mod, verticalArrangement = Arrangement.spacedBy(node.spacing.dp)) {
                node.children.forEach { PluginUiRenderer(it, accent, onBack, onCallback) }
            }
        }

        is UiNode.Row -> {
            Row(
                Modifier
                    .then(if (node.fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                    .padding(node.padding.dp),
                horizontalArrangement = Arrangement.spacedBy(node.spacing.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                node.children.forEach { PluginUiRenderer(it, accent, onBack, onCallback) }
            }
        }

        is UiNode.Box -> {
            Box(
                Modifier
                    .then(if (node.fillMaxSize) Modifier.fillMaxSize() else Modifier)
                    .padding(node.padding.dp)
            ) {
                node.children.forEach { PluginUiRenderer(it, accent, onBack, onCallback) }
            }
        }

        is UiNode.LazyColumn -> {
            LazyColumn(
                Modifier
                    .then(if (node.fillMaxSize) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .padding(node.padding.dp),
                verticalArrangement = Arrangement.spacedBy(node.spacing.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                itemsIndexed(node.children) { _, child ->
                    PluginUiRenderer(child, accent, onBack, onCallback)
                }
            }
        }

        is UiNode.Text -> {
            Text(
                text = node.text,
                style = textStyle(node.style),
                color = parseColor(node.color) ?: textColor(node.style),
                maxLines = node.maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }

        is UiNode.Button -> {
            val mod = if (node.fillMaxWidth) Modifier.fillMaxWidth() else Modifier
            when (node.style.lowercase()) {
                "outlined" -> OutlinedButton(
                    onClick = { onCallback(node.onClickId, null) },
                    enabled = node.enabled,
                    modifier = mod,
                ) { Text(node.text) }
                "text" -> TextButton(
                    onClick = { onCallback(node.onClickId, null) },
                    enabled = node.enabled,
                    modifier = mod,
                ) { Text(node.text) }
                "tonal" -> FilledTonalButton(
                    onClick = { onCallback(node.onClickId, null) },
                    enabled = node.enabled,
                    modifier = mod,
                ) { Text(node.text) }
                "material" -> MaterialButton(
                    text = node.text,
                    onClick = { onCallback(node.onClickId, null) },
                    enabled = node.enabled,
                    modifier = mod,
                    accentColor = accent,
                )
                else -> DolphyButton(
                    onClick = { onCallback(node.onClickId, null) },
                    enabled = node.enabled,
                    modifier = mod,
                ) { Text(node.text) }
            }
        }

        is UiNode.IconButton -> {
            DolphyIconButton(onClick = { onCallback(node.onClickId, null) }, enabled = node.enabled) {
                Icon(PluginIcons.resolve(node.icon), null, tint = accent)
            }
        }

        is UiNode.TextField -> {



            var text by remember(node.label, node.singleLine) { mutableStateOf(node.value) }
            LaunchedEffect(node.value) {

                if (node.value != text) {
                    text = node.value
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    text = new
                    onCallback(node.onChangeId, new)
                },
                label = if (node.label.isNotBlank()) ({ Text(node.label) }) else null,
                singleLine = node.singleLine,
                modifier = if (node.fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            )
        }

        is UiNode.Switch -> {
            if (node.title.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(node.title, style = MaterialTheme.typography.bodyLarge)
                        if (node.subtitle.isNotBlank()) {
                            Text(node.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    DolphySwitch(
                        checked = node.checked,
                        onCheckedChange = { onCallback(node.onChangeId, it) },
                        enabled = node.enabled,
                    )
                }
            } else {
                DolphySwitch(
                    checked = node.checked,
                    onCheckedChange = { onCallback(node.onChangeId, it) },
                    enabled = node.enabled,
                )
            }
        }

        is UiNode.Slider -> {
            Column(Modifier.fillMaxWidth()) {
                if (node.title.isNotBlank()) {
                    Text(node.title, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                }
                DolphySlider(
                    value = node.value.coerceIn(node.min, node.max),
                    onValueChange = { onCallback(node.onChangeId, it) },
                    valueRange = node.min..node.max,
                    steps = node.steps,
                )
            }
        }

        is UiNode.LinearProgress -> {
            val mod = Modifier.then(if (node.fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            DolphyLinearProgressIndicator(
                progress = node.progress?.coerceIn(0f, 1f),
                modifier = mod,
            )
        }

        is UiNode.CircularProgress -> {
            if (node.progress == null) CircularProgressIndicator()
            else CircularProgressIndicator(progress = { node.progress.coerceIn(0f, 1f) })
        }

        is UiNode.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        is UiNode.Spacer -> Spacer(Modifier.height(node.height.dp).width(node.width.dp))
        is UiNode.Icon -> Icon(
            PluginIcons.resolve(node.name),
            null,
            modifier = Modifier.size(node.size.dp),
            tint = parseColor(node.tint) ?: accent,
        )

        is UiNode.Image -> PluginAssetImage(node)

        is UiNode.Chip -> {
            FilterChip(
                selected = node.selected,
                onClick = { onCallback(node.onClickId, null) },
                label = { Text(node.text) },
            )
        }

        is UiNode.ConnectedButtonGroup -> {
            ConnectedButtonGroup(
                options = node.options,
                selectedValue = node.selectedValue,
                onValueSelected = { value -> onCallback(node.onSelectId, value) },
                accentColor = accent,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is UiNode.TabRow -> {
            MaterialTabRow(
                selectedTabIndex = node.selectedIndex.coerceIn(0, (node.tabs.size - 1).coerceAtLeast(0)),
                tabs = node.tabs,
                onTabSelected = { index -> onCallback(node.onSelectId, index) },
                modifier = Modifier.fillMaxWidth(),
                accentColor = accent,
            )
        }

        is UiNode.BounceButton -> {
            val mod = if (node.fillMaxWidth) Modifier.fillMaxWidth() else Modifier
            ExpressiveBounceButton(
                onClick = { onCallback(node.onClickId, null) },
                modifier = mod,
                enabled = node.enabled,
                containerColor = accent,
            ) {
                Text(node.text)
            }
        }

        is UiNode.SplitButton -> {
            val mod = if (node.fillMaxWidth) Modifier.fillMaxWidth() else Modifier
            ExpressiveSplitButton(
                primaryText = node.primaryText,
                onPrimaryClick = { onCallback(node.onPrimaryId, null) },
                onSecondaryClick = { onCallback(node.onSecondaryId, null) },
                modifier = mod,
                accentColor = accent,
                enabled = node.enabled,
            )
        }

        is UiNode.WavyProgress -> {
            WavyCircularProgressIndicator(
                modifier = Modifier.size(node.size.dp),
                progress = node.progress?.coerceIn(0f, 1f),
                color = accent,
            )
        }

        is UiNode.FloatingToolbar -> {
            PluginFloatingToolbar(
                items = node.items,
                accent = accent,
                onCallback = onCallback,
            )
        }

        is UiNode.Checkbox -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = node.enabled) {
                        onCallback(node.onChangeId, !node.checked)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = node.checked,
                    onCheckedChange = { onCallback(node.onChangeId, it) },
                    enabled = node.enabled,
                )
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    if (node.title.isNotBlank()) {
                        Text(node.title, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (node.subtitle.isNotBlank()) {
                        Text(
                            node.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        is UiNode.RadioGroup -> {
            Column(Modifier.fillMaxWidth()) {
                if (node.title.isNotBlank()) {
                    Text(node.title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                }
                node.options.forEach { (label, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onCallback(node.onSelectId, value) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = node.selectedValue == value,
                            onClick = { onCallback(node.onSelectId, value) },
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        is UiNode.Dropdown -> {
            PluginDropdown(
                options = node.options,
                selectedValue = node.selectedValue,
                label = node.label,
                enabled = node.enabled,
                onSelect = { onCallback(node.onSelectId, it) },
            )
        }

        is UiNode.LazyRow -> {
            LazyRow(
                modifier = if (node.fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
                contentPadding = PaddingValues(node.padding.dp),
                horizontalArrangement = Arrangement.spacedBy(node.spacing.dp),
            ) {
                itemsIndexed(node.children) { _, child ->
                    PluginUiRenderer(child, accent, onBack, onCallback)
                }
            }
        }

        is UiNode.MaterialCard -> {
            val shape = if (node.segmentedIndex != null && node.segmentedCount != null) {
                getSegmentedShape(node.segmentedIndex, node.segmentedCount)
            } else {
                RoundedCornerShape(28.dp)
            }
            MaterialCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (node.onClickId != null) Modifier.clickable { onCallback(node.onClickId, null) }
                        else Modifier
                    ),
                accentColor = accent,
                shape = shape,
                contentPadding = node.contentPadding.dp,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    node.children.forEach { PluginUiRenderer(it, accent, onBack, onCallback) }
                }
            }
        }

        is UiNode.FunctionRow -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = node.onClickId != null) { onCallback(node.onClickId, null) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = parseColor(node.iconTint) ?: accent
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PluginIcons.resolve(node.icon), null, tint = tint, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(node.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (node.description.isNotBlank()) {
                        Text(node.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        is UiNode.SegmentedList -> {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(
                    if (node.spacing > 0f) node.spacing.dp else com.droid.dolphy.M3SegmentedListItemSpacing,
                ),
            ) {
                val count = node.children.size
                node.children.forEachIndexed { index, child ->
                    when {
                        child is UiNode.MaterialCard && child.segmentedIndex == null -> {
                            PluginUiRenderer(
                                child.copy(segmentedIndex = index, segmentedCount = count),
                                accent, onBack, onCallback,
                            )
                        }
                        child is UiNode.FunctionRow -> {
                            com.droid.dolphy.M3SegmentedListItem(
                                index = index,
                                count = count,
                                headline = child.title,
                                supporting = child.description.takeIf { it.isNotBlank() },
                                leadingIcon = PluginIcons.resolve(child.icon).let {
                                    null
                                },
                                leadingContent = {
                                    val tint = parseColor(child.iconTint) ?: accent
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(tint.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            PluginIcons.resolve(child.icon),
                                            null,
                                            tint = tint,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                },
                                onClick = if (child.onClickId != null) {
                                    { onCallback(child.onClickId, null) }
                                } else null,
                            )
                        }
                        child is UiNode.SettingsRow -> {
                            com.droid.dolphy.M3SegmentedListItem(
                                index = index,
                                count = count,
                                headline = child.title,
                                supporting = child.subtitle.takeIf { it.isNotBlank() },
                                leadingContent = if (child.icon != null) {
                                    {
                                        Icon(
                                            PluginIcons.resolve(child.icon),
                                            null,
                                            tint = accent,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                } else null,
                                trailingContent = child.trailing?.let { t ->
                                    { PluginUiRenderer(t, accent, onBack, onCallback) }
                                },
                                onClick = if (child.onClickId != null) {
                                    { onCallback(child.onClickId, null) }
                                } else null,
                            )
                        }
                        else -> {
                            com.droid.dolphy.M3SegmentedListItemContainer(
                                index = index,
                                count = count,
                            ) {
                                Box(Modifier.padding(4.dp)) {
                                    PluginUiRenderer(child, accent, onBack, onCallback)
                                }
                            }
                        }
                    }
                }
            }
        }

        is UiNode.SettingsRow -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = node.onClickId != null) { onCallback(node.onClickId, null) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (node.icon != null) {
                    Icon(PluginIcons.resolve(node.icon), null, tint = accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(node.title, style = MaterialTheme.typography.bodyLarge)
                    if (node.subtitle.isNotBlank()) {
                        Text(node.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                node.trailing?.let { PluginUiRenderer(it, accent, onBack, onCallback) }
            }
        }

        is UiNode.WebView -> PluginWebView(node)

        is UiNode.LogPanel -> {
            val scroll = rememberScrollState()

            val panelHeight = node.maxHeight.coerceAtLeast(280f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(panelHeight.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(0.35f))
                    .padding(12.dp)
                    .verticalScroll(scroll),
            ) {
                Text(
                    node.text.ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFFB2FF59),
                )
            }
            LaunchedEffect(node.text) {

                scroll.scrollTo(scroll.maxValue)
            }
        }

        is UiNode.AlertDialog -> {
            if (node.show) {
                PluginAlertDialogContent(
                    title = node.title,
                    message = node.message,
                    buttons = node.buttons.ifEmpty {
                        listOf(
                            UiNode.DialogButton(node.confirmText, "filled", node.onConfirmId),
                            UiNode.DialogButton(node.dismissText, "text", node.onDismissId),
                        )
                    },
                    cancelable = node.cancelable,
                    onDismiss = { onCallback(node.onDismissId, null) },
                    onButton = { id -> onCallback(id, null) },
                )
            }
        }

        is UiNode.Empty -> {}
        is UiNode.TopBar -> {

            SectionTopBar(title = node.title, onBack = if (node.showBack) onBack else null, accentColor = accent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginDropdown(
    options: List<Pair<String, String>>,
    selectedValue: String,
    label: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.second == selectedValue }?.first
        ?: selectedValue
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = if (label.isNotBlank()) ({ Text(label) }) else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (optLabel, value) ->
                DropdownMenuItem(
                    text = { Text(optLabel) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun PluginFloatingToolbar(
    items: List<UiNode.ToolbarItem>,
    accent: Color,
    onCallback: (String?, Any?) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val barColor = colorScheme.surfaceContainerHighest.copy(alpha = 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .height(64.dp)
                .wrapContentWidth(),
            shape = RoundedCornerShape(32.dp),
            color = barColor,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
            ) {
                items.forEach { item ->
                    val pillColor by animateColorAsState(
                        targetValue = if (item.selected) accent else Color.Transparent,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 450f),
                        label = "plugin_ftb_pill",
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (item.selected) Color.White else colorScheme.onSurfaceVariant,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 450f),
                        label = "plugin_ftb_icon",
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp, 48.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(pillColor)
                            .clickable { onCallback(item.onClickId, null) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = PluginIcons.resolve(item.icon),
                            contentDescription = item.label.ifBlank { item.icon },
                            tint = iconColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PluginAlertDialogContent(
    title: String,
    message: String,
    buttons: List<UiNode.DialogButton>,
    cancelable: Boolean,
    onDismiss: () -> Unit,
    onButton: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (cancelable) onDismiss()
        },
        title = if (title.isNotBlank()) {
            { Text(title, fontWeight = FontWeight.SemiBold) }
        } else null,
        text = if (message.isNotBlank()) {
            { Text(message) }
        } else null,
        confirmButton = {
            val primary = buttons.firstOrNull()
            if (primary != null) {
                DialogActionButton(primary) { onButton(primary.onClickId) }
            }
        },
        dismissButton = {
            if (buttons.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    buttons.drop(1).forEach { btn ->
                        DialogActionButton(btn) { onButton(btn.onClickId) }
                    }
                }
            }
        },
    )
}

@Composable
private fun DialogActionButton(btn: UiNode.DialogButton, onClick: () -> Unit) {
    when (btn.style.lowercase()) {
        "filled", "material" -> {
            Button(onClick = onClick) { Text(btn.text) }
        }
        "tonal" -> {
            FilledTonalButton(onClick = onClick) { Text(btn.text) }
        }
        "outlined" -> {
            OutlinedButton(onClick = onClick) { Text(btn.text) }
        }
        "destructive" -> {
            TextButton(onClick = onClick) {
                Text(btn.text, color = MaterialTheme.colorScheme.error)
            }
        }
        else -> {
            TextButton(onClick = onClick) { Text(btn.text) }
        }
    }
}

@Composable
private fun PluginAssetImage(node: UiNode.Image) {
    val context = LocalContext.current
    val bitmap = remember(node.source) {
        decodePluginImageSource(context, node.source)
    }
    val scale = when (node.scale.lowercase()) {
        "crop", "centercrop", "center_crop" -> ContentScale.Crop
        "fill", "fillbounds", "fill_bounds" -> ContentScale.FillBounds
        "fillwidth", "fill_width" -> ContentScale.FillWidth
        "fillheight", "fill_height" -> ContentScale.FillHeight
        "inside" -> ContentScale.Inside
        "none" -> ContentScale.None
        else -> ContentScale.Fit
    }
    var mod: Modifier = Modifier
    when {
        node.width != null && node.height != null -> {
            mod = mod.size(width = node.width.dp, height = node.height.dp)
        }
        node.width != null -> {
            mod = mod.width(node.width.dp)
            if (node.fillMaxWidth) mod = mod.fillMaxWidth()
        }
        node.height != null -> {
            mod = mod.height(node.height.dp)
            if (node.fillMaxWidth) mod = mod.fillMaxWidth()
        }
        node.fillMaxWidth -> mod = mod.fillMaxWidth()
        else -> {
            mod = mod.widthIn(max = 280.dp).heightIn(max = 280.dp)
        }
    }
    if (node.cornerRadius > 0f) {
        mod = mod.clip(RoundedCornerShape(node.cornerRadius.dp))
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = node.contentDescription.ifBlank { null },
            modifier = mod,
            contentScale = scale,
        )
    } else {
        Box(
            mod.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "img?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun decodePluginImageSource(context: android.content.Context, source: String): android.graphics.Bitmap? {
    if (source.isBlank()) return null
    return try {
        val trimmed = source.trim()
        when {
            trimmed.startsWith("data:", ignoreCase = true) -> {
                val b64 = trimmed.substringAfter("base64,", missingDelimiterValue = "")
                if (b64.isBlank()) null
                else {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
            trimmed.startsWith("asset://", ignoreCase = true) ||
                trimmed.startsWith("assets://", ignoreCase = true) -> {
                val path = trimmed.substringAfter("://").removePrefix("/")
                context.assets.open(sanitizeAsset(path)).use { BitmapFactory.decodeStream(it) }
            }
            trimmed.length > 64 && !trimmed.contains('/') && !trimmed.contains('.') &&
                trimmed.matches(Regex("^[A-Za-z0-9+/=\\s]+$")) -> {
                val bytes = Base64.decode(trimmed, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            else -> {
                val path = sanitizeAsset(trimmed)
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun sanitizeAsset(path: String): String {
    return path.trim()
        .replace('\\', '/')
        .removePrefix("/")
        .split('/')
        .filter { it.isNotEmpty() && it != "." && it != ".." }
        .joinToString("/")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PluginWebView(node: UiNode.WebView) {
    val mod = if (node.fillMaxSize) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(node.height.dp)
    AndroidView(
        modifier = mod.clip(RoundedCornerShape(12.dp)),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                if (node.html.isNotBlank()) {
                    loadDataWithBaseURL(null, node.html, "text/html", "utf-8", null)
                } else if (node.url.isNotBlank()) {
                    loadUrl(node.url)
                }
            }
        },
        update = { web ->
            if (node.html.isNotBlank()) {
                web.loadDataWithBaseURL(null, node.html, "text/html", "utf-8", null)
            } else if (node.url.isNotBlank() && web.url != node.url) {
                web.loadUrl(node.url)
            }
        },
    )
}

@Composable
private fun textStyle(style: String) = when (style.lowercase()) {
    "headlinelarge", "headline_large" -> MaterialTheme.typography.headlineLarge
    "headlinemedium", "headline_medium" -> MaterialTheme.typography.headlineMedium
    "headlinesmall", "headline_small" -> MaterialTheme.typography.headlineSmall
    "titlelarge", "title_large" -> MaterialTheme.typography.titleLarge
    "titlemedium", "title_medium" -> MaterialTheme.typography.titleMedium
    "titlesmall", "title_small" -> MaterialTheme.typography.titleSmall
    "bodylarge", "body_large" -> MaterialTheme.typography.bodyLarge
    "bodysmall", "body_small" -> MaterialTheme.typography.bodySmall
    "labelsmall", "label_small" -> MaterialTheme.typography.labelSmall
    "labelmedium", "label_medium" -> MaterialTheme.typography.labelMedium
    "labellarge", "label_large" -> MaterialTheme.typography.labelLarge
    else -> MaterialTheme.typography.bodyMedium
}

@Composable
private fun textColor(style: String): Color {
    return when {
        style.contains("label", true) -> Color.White.copy(0.6f)
        else -> MaterialTheme.colorScheme.onBackground
    }
}

private fun parseColor(raw: String?): Color? {
    if (raw.isNullOrBlank()) return null
    return try {
        when (raw.lowercase()) {
            "error", "red" -> Color(0xFFFF5252)
            "primary", "accent" -> null
            "muted", "secondary" -> Color.White.copy(0.6f)
            "success", "green" -> Color(0xFF69F0AE)
            "warning", "orange" -> Color(0xFFFFAB40)
            else -> Color(AndroidColor.parseColor(if (raw.startsWith("#")) raw else "#$raw"))
        }
    } catch (_: Exception) {
        null
    }
}

