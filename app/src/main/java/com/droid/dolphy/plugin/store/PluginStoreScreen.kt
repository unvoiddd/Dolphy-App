package com.droid.dolphy.plugin.store

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.BlurHeaderBox
import com.droid.dolphy.DolphyButton
import com.droid.dolphy.DolphyIconButton
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.plugin.PluginManager
import kotlinx.coroutines.launch


@Composable
fun PluginStoreScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val plugins by PluginStoreRepository.plugins.collectAsState()
    val loadState by PluginStoreRepository.loadState.collectAsState()
    val error by PluginStoreRepository.error.collectAsState()
    val sources by PluginStoreRepository.sources.collectAsState()
    val activeId by PluginStoreRepository.activeSourceId.collectAsState()
    val installed by PluginManager.plugins.collectAsState()

    var query by remember { mutableStateOf("") }
    var sheetOpen by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        PluginStoreRepository.initialize(context)
        PluginStoreRepository.refresh(force = false)
        PluginStoreRepository.runAutoUpdateOnce(PluginManager.plugins.value)
    }
    androidx.compose.runtime.LaunchedEffect(activeId) {
        PluginStoreRepository.refresh(force = false)
    }

    val filtered = remember(plugins, query) {
        if (query.isBlank()) plugins
        else plugins.filter {
            it.name.contains(query, true) || it.displayName.contains(query, true) ||
                it.author.contains(query, true) ||
                it.description.contains(query, true)
        }
    }
    val featured = remember(plugins) { plugins.take(6) }

    BlurHeaderBox(
        title = stringResource(R.string.store_title),
        onBack = { navController.popBackStack() },
        accentColor = MaterialTheme.colorScheme.primary,
        actions = {
            DolphyIconButton(onClick = { scope.launch { PluginStoreRepository.refresh(force = true) } }) {
                Icon(Icons.Default.Refresh, stringResource(R.string.store_refresh))
            }
            DolphyIconButton(onClick = { sheetOpen = true }) {
                Icon(Icons.Default.Storefront, stringResource(R.string.store_sources))
            }
        },
    ) { headerPadding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = headerPadding + 10.dp, bottom = 120.dp),
            ) {
                item("search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text(stringResource(R.string.store_search_hint)) },
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }

                item("source_chip") {
                    val active = sources.firstOrNull { it.id == activeId }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.store_active_source, active?.label ?: "—"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (loadState == StoreLoadState.READY) {
                            Text(
                                stringResource(R.string.store_items_count, plugins.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                if (query.isBlank() && featured.isNotEmpty()) {
                    item("featured_title") {
                        SectionTitle(icon = Icons.Default.AutoAwesome, text = stringResource(R.string.store_featured))
                    }
                    item("featured_row") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(featured, key = { "f_" + it.key }) { plugin ->
                                FeaturedCard(
                                    plugin = plugin,
                                    onClick = {
                                        PluginStoreRepository.select(plugin)
                                        navController.navigate("plugin_store_detail")
                                    },
                                )
                            }
                        }
                    }
                    item("catalog_title") {
                        SectionTitle(icon = Icons.Default.Storefront, text = stringResource(R.string.store_all_plugins))
                    }
                }

                items(filtered, key = { it.key }) { plugin ->
                    val match = PluginStoreRepository.installedMatch(plugin, installed)
                    CatalogCard(
                        plugin = plugin,
                        installedVersion = match?.manifest?.version,
                        onClick = {
                            PluginStoreRepository.select(plugin)
                            navController.navigate("plugin_store_detail")
                        },
                    )
                }

                if (loadState == StoreLoadState.READY && filtered.isEmpty()) {
                    item("empty_filtered") {
                        CenterNote(
                            if (query.isBlank()) stringResource(R.string.store_empty_source)
                            else stringResource(R.string.store_nothing_found),
                        )
                    }
                }
            }

            when (loadState) {
                StoreLoadState.LOADING -> if (plugins.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.store_loading_catalog), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                StoreLoadState.ERROR -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.store_error_catalog),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    DolphyButton(onClick = { scope.launch { PluginStoreRepository.refresh(force = true) } }) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.store_retry))
                    }
                }
                else -> Unit
            }
        }
    }

    if (sheetOpen) {
        SourcesSheet(onDismiss = { sheetOpen = false })
    }
}

@Composable
private fun SectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CenterNote(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun FeaturedCard(plugin: StorePlugin, onClick: () -> Unit) {
    MaterialCard(
        modifier = Modifier.width(240.dp).height(150.dp),
        accentColor = MaterialTheme.colorScheme.primary,
        containerType = "primary",
        contentPadding = 16.dp,
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(storeIconFor(plugin.name), null, modifier = Modifier.size(26.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        plugin.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                plugin.displayShortDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "v${plugin.version} · ${plugin.author.ifBlank { "—" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CatalogCard(plugin: StorePlugin, installedVersion: String?, onClick: () -> Unit) {
    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = MaterialTheme.colorScheme.primary,
        contentPadding = 14.dp,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryFixed,
                contentColor = MaterialTheme.colorScheme.onPrimaryFixed,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(storeIconFor(plugin.name), null, modifier = Modifier.size(28.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    plugin.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    plugin.displayShortDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            InstallBadge(installedVersion, plugin.version)
        }
    }
}

@Composable
private fun InstallBadge(installedVersion: String?, catalogVersion: String) {
    when {
        installedVersion == null -> Unit
        installedVersion != catalogVersion -> Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Icon(Icons.Default.Update, stringResource(R.string.store_update_available), modifier = Modifier.padding(6.dp).size(20.dp))
        }
        else -> Icon(
            Icons.Default.CheckCircle,
            stringResource(R.string.store_installed),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
    }
}


@Composable
fun PluginStoreDetailScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val plugin by PluginStoreRepository.selected.collectAsState()
    val installing by PluginStoreRepository.installing.collectAsState()
    val installed by PluginManager.plugins.collectAsState()

    val current = plugin
    if (current == null) {
        BlurHeaderBox(
            title = stringResource(R.string.store_plugin),
            onBack = { navController.popBackStack() },
        ) { headerPadding ->
            Column(Modifier.fillMaxSize().padding(top = headerPadding)) {
                CenterNote(stringResource(R.string.store_no_plugin_selected))
            }
        }
        return
    }

    val match = PluginStoreRepository.installedMatch(current, installed)
    val isInstalling = current.key in installing

    BlurHeaderBox(
        title = current.displayName,
        onBack = { navController.popBackStack() },
        accentColor = MaterialTheme.colorScheme.primary,
    ) { headerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = headerPadding + 12.dp, bottom = 140.dp),
        ) {
            item("header") {
                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerType = "primary",
                    contentPadding = 18.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(storeIconFor(current.name), null, modifier = Modifier.size(38.dp))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                current.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "v${current.version}" + if (current.author.isNotBlank()) " · ${current.author}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

            item("actions") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val installError = stringResource(R.string.store_error_prefix)
                    val label = when {
                        isInstalling -> stringResource(R.string.store_installing)
                        match == null -> stringResource(R.string.store_install)
                        match.manifest.version != current.version -> stringResource(R.string.store_update_to_version, current.version)
                        else -> stringResource(R.string.store_reinstall)
                    }
                    DolphyButton(
                        onClick = {
                            scope.launch {
                                val res = PluginStoreRepository.install(current)
                                val msg = res.fold(
                                    onSuccess = { context.getString(R.string.store_install_success, current.name) },
                                    onFailure = { installError.format(it.message.orEmpty()) },
                                )
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isInstalling,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isInstalling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                if (match != null && match.manifest.version != current.version) Icons.Default.Update
                                else Icons.Default.CloudDownload,
                                null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                    if (current.postUrl != null) {
                        DolphyButton(
                            onClick = {
                                if (!PluginStoreRepository.openPost(context, current)) {
                                    Toast.makeText(context, context.getString(R.string.store_open_link_failed), Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.store_discussion))
                        }
                    }
                }
            }

            if (match != null) {
                item("installed_note") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(R.string.store_installed_version, match.manifest.version),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item("desc_title") {
                Text(
                    stringResource(R.string.store_description),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item("desc_body") {
                MaterialCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    Text(
                        current.displayDescription.ifBlank { stringResource(R.string.store_no_description) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item("meta") {
                MaterialCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp, containerType = "low") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetaRow(stringResource(R.string.store_author), current.author.ifBlank { "—" })
                        MetaRow(stringResource(R.string.store_version), current.version)
                        if (current.updatedAt.isNotBlank()) MetaRow(stringResource(R.string.store_updated), current.updatedAt)
                        if (current.file.isNotBlank()) MetaRow(stringResource(R.string.store_file), current.file.substringAfterLast('/'))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sources by PluginStoreRepository.sources.collectAsState()
    val activeId by PluginStoreRepository.activeSourceId.collectAsState()
    var input by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.store_sources_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.store_sources_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            sources.forEach { source ->
                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 14.dp,
                    containerType = if (source.id == activeId) "primary" else "surface",
                    onClick = {
                        PluginStoreRepository.setActive(source.id)
                        scope.launch { PluginStoreRepository.refresh(force = true) }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            if (source.id == activeId) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                            null,
                            tint = if (source.id == activeId) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(source.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                source.indexUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!source.builtIn) {
                            DolphyIconButton(onClick = { PluginStoreRepository.removeSource(source.id) }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.store_delete), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.store_source_input_hint)) },
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    submitSource(input, context) { input = "" }
                    scope.launch { PluginStoreRepository.refresh(force = true) }
                }),
            )
            DolphyButton(
                onClick = {
                    submitSource(input, context) { input = "" }
                    scope.launch { PluginStoreRepository.refresh(force = true) }
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.store_add_source))
            }
        }
    }
}

private fun submitSource(input: String, context: android.content.Context, onClear: () -> Unit) {
    if (input.isBlank()) return
    PluginStoreRepository.addSource(input)
        .onSuccess {
            onClear()
            Toast.makeText(context, context.getString(R.string.store_source_connected, it.label), Toast.LENGTH_SHORT).show()
        }
        .onFailure {
            Toast.makeText(context, it.message ?: context.getString(R.string.store_invalid_link), Toast.LENGTH_LONG).show()
        }
}
