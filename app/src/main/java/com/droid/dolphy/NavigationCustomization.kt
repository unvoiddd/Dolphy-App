package com.droid.dolphy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationCustomizationSections(viewModel: SpamViewModel) {
    val fabRoute by viewModel.fabDestinationRoute.collectAsState()
    val sections = functionDestinationSections()
    val destinations = sections.flatMap { it.second }
    val selectedFab = destinations.firstOrNull { it.route == fabRoute }
    var showFabSheet by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
        M3SegmentedListSectionHeader(title = stringResource(R.string.settings_bottom_panel).uppercase())
        M3SegmentedListItem(
            index = 0,
            count = 1,
            headline = "FAB",
            supporting = selectedFab?.title ?: stringResource(R.string.nav_modules),
            leadingIcon = selectedFab?.icon ?: Icons.Default.Extension,
            onClick = { showFabSheet = true },
        )
    }

    if (showFabSheet) {
        ModalBottomSheet(onDismissRequest = { showFabSheet = false }) {
            Text(
                text = stringResource(R.string.settings_fab_choose),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    M3SegmentedListItem(
                        index = 0,
                        count = 1,
                        headline = stringResource(R.string.nav_modules),
                        supporting = stringResource(R.string.settings_fab_functions_summary),
                        leadingIcon = Icons.Default.Extension,
                        selected = fabRoute == "other",
                        trailingContent = if (fabRoute == "other") {{ Icon(Icons.Default.Check, null) }} else null,
                        showChevron = false,
                        onClick = {
                            viewModel.setFabDestinationRoute("other")
                            showFabSheet = false
                        },
                    )
                }
                sections.forEach { (section, sectionItems) ->
                    item { M3SegmentedListSectionHeader(title = section) }
                    items(sectionItems, key = { it.route }) { destination ->
                        M3SegmentedListItem(
                            index = 0,
                            count = 1,
                            headline = destination.title,
                            supporting = destination.description,
                            leadingIcon = destination.icon,
                            selected = fabRoute == destination.route,
                            trailingContent = if (fabRoute == destination.route) {{ Icon(Icons.Default.Check, null) }} else null,
                            showChevron = false,
                            onClick = {
                                viewModel.setFabDestinationRoute(destination.route)
                                showFabSheet = false
                            },
                        )
                    }
                }
            }
        }
    }
}
