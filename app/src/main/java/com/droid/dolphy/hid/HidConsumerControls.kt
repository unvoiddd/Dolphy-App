package com.droid.dolphy.hid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.droid.dolphy.AccentButton
import com.droid.dolphy.M3SegmentedListItem
import com.droid.dolphy.M3SegmentedListItemSpacing
import com.droid.dolphy.M3SegmentedListSectionHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class HidQuickAction(
    val label: String,
    val action: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PcConsumerControlsSection(hidConnection: Connection) {
    val scope = rememberCoroutineScope()
    var selectedPage by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }

    fun tapKey(key: String) {
        scope.launch { DuckyUtils.tapKey(hidConnection, key) }
    }

    fun tapShortcut(key: String) {
        scope.launch {
            runCatching {
                hidConnection.modifierDown("MOD_LCTRL")
                delay(24)
                hidConnection.keyDown(key)
                delay(36)
                hidConnection.keyUp(key)
                hidConnection.modifierUp("MOD_LCTRL")
            }
        }
    }

    fun tapConsumer(mask: Int) {
        scope.launch {
            runCatching {
                hidConnection.consumerDown(mask)
                delay(48)
                hidConnection.consumerUp(mask)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Control", "Launch", "Actions").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedPage == index,
                    onClick = {
                        selectedPage = index
                        search = ""
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                    label = { Text(label, maxLines = 1) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when (selectedPage) {
            0 -> {
                val mediaActions = remember {
                    listOf(
                        "Brightness +" to 0x0001,
                        "Brightness −" to 0x0002,
                        "Next track" to 0x0004,
                        "Previous track" to 0x0008,
                        "Stop" to 0x0010,
                        "Play / Pause" to 0x0020,
                        "Mute" to 0x0040,
                        "Volume +" to 0x0080,
                        "Volume −" to 0x0100,
                        "Eject" to 0x0200,
                        "Snapshot" to 0x0400,
                        "Sleep" to 0x0800,
                        "Hibernate" to 0x1000,
                        "Power down" to 0x2000,
                        "Cold restart" to 0x4000,
                        "Warm restart" to 0x8000,
                    )
                }
                val shortcuts = remember {
                    listOf(
                        "Undo" to "Z",
                        "Redo" to "Y",
                        "Save" to "S",
                        "Paste" to "V",
                        "Cut" to "X",
                        "Copy" to "C",
                        "Select all" to "A",
                        "Find" to "F",
                        "Replace" to "H",
                    )
                }
                var textToSend by remember { mutableStateOf("") }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = textToSend,
                            onValueChange = { textToSend = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Text") },
                            singleLine = true,
                        )
                    }
                    item {
                        AccentButton(
                            onClick = {
                                val value = textToSend
                                scope.launch {
                                    DuckyUtils.typeText(hidConnection, value)
                                    textToSend = ""
                                }
                            },
                            enabled = textToSend.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Send text")
                        }
                    }
                    item { M3SegmentedListSectionHeader("D-PAD") }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Spacer(Modifier.weight(1f))
                                AccentButton(onClick = { tapKey("UP") }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.ArrowUpward, null)
                                }
                                Spacer(Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AccentButton(onClick = { tapKey("LEFT") }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                }
                                AccentButton(onClick = { tapKey("DOWN") }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.ArrowDownward, null)
                                }
                                AccentButton(onClick = { tapKey("RIGHT") }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                                }
                            }
                        }
                    }
                    item { M3SegmentedListSectionHeader("LOCK KEYS") }
                    item {
                        HidActionRows(
                            listOf(
                                HidQuickAction("Num Lock") { tapKey("NUMLOCK") },
                                HidQuickAction("Caps Lock") { tapKey("CAPSLOCK") },
                                HidQuickAction("Scroll Lock") { tapKey("SCROLLLOCK") },
                            )
                        )
                    }
                    item { M3SegmentedListSectionHeader("CTRL SHORTCUTS") }
                    item {
                        HidActionRows(shortcuts.map { (label, key) -> HidQuickAction(label) { tapShortcut(key) } })
                    }
                    item { M3SegmentedListSectionHeader("CONSUMER CONTROL") }
                    item {
                        HidActionRows(mediaActions.map { (label, mask) -> HidQuickAction(label) { tapConsumer(mask) } })
                    }
                }
            }
            1, 2 -> {
                val allActions = if (selectedPage == 1) hidApplicationLaunchActions else hidApplicationControlActions
                val filtered = remember(allActions, search) {
                    if (search.isBlank()) allActions else allActions.filter { it.label.contains(search, ignoreCase = true) }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
                    ) {
                        itemsIndexed(filtered, key = { _, item -> item.usage }) { index, item ->
                            M3SegmentedListItem(
                                index = index,
                                count = filtered.size,
                                headline = item.label,
                                supporting = "0x${item.usage.toString(16).uppercase()}",
                                leadingIcon = if (selectedPage == 1) Icons.Default.Launch else Icons.Default.TouchApp,
                                showChevron = false,
                                onClick = {
                                    runCatching {
                                        if (selectedPage == 1) {
                                            hidConnection.launchApplication(item.usage)
                                        } else {
                                            hidConnection.controlApplication(item.usage)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HidActionRows(actions: List<HidQuickAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowActions.forEach { action ->
                    AccentButton(
                        onClick = action.action,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = action.label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (rowActions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
