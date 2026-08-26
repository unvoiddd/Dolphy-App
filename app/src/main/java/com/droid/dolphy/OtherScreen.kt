package com.droid.dolphy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherScreen(navController: NavController, spamViewModel: SpamViewModel) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val sections = functionDestinationSections()

    MaterialBackground(accentColor = accent) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp),
            ) {
                sections.forEach { (section, destinations) ->
                    item(key = section) {
                        FunctionSectionBlock(
                            title = section,
                            items = destinations,
                            accent = accent,
                            onClick = { destination ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                openFunctionDestination(destination, navController, context)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FunctionSectionBlock(
    title: String,
    items: List<FunctionDestination>,
    accent: androidx.compose.ui.graphics.Color,
    onClick: (FunctionDestination) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        M3SegmentedListSectionHeader(title = title)
        M3SegmentedList(items = items) { index, count, item ->
            M3SegmentedListItem(
                index = index,
                count = count,
                headline = item.title,
                supporting = item.description.takeIf { it.isNotBlank() },
                leadingIcon = item.icon,
                showChevron = !item.requiresRoot,
                trailingContent = if (item.requiresRoot) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                } else null,
                onClick = { onClick(item) },
            )
        }
    }
}
