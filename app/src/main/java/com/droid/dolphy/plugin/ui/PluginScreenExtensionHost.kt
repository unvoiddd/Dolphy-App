package com.droid.dolphy.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.PluginRegistry
import com.droid.dolphy.plugin.model.PluginScreenContribution

@Composable
fun PluginScreenExtensionHost(route: String, navController: NavController) {
    val contributions by PluginRegistry.screenContributions.collectAsState()
    val revision by PluginRegistry.revision.collectAsState()
    val matching = contributions
        .filter { PluginManager.routeMatches(it.routePattern, route) }
        .sortedByDescending { it.priority }
    val replacement = matching.firstOrNull { it.mode == "replace" }
    val layers = listOfNotNull(replacement) + matching.filterNot { it.mode == "replace" }.reversed()
    Box(Modifier.fillMaxSize()) {
        layers.forEach { contribution ->
            PluginScreenExtension(contribution, revision, navController)
        }
    }
}

@Composable
fun PluginSurfaceExtensionHost(
    surface: String,
    navController: NavController,
    fallback: @Composable () -> Unit,
) {
    val contributions by PluginRegistry.screenContributions.collectAsState()
    val revision by PluginRegistry.revision.collectAsState()
    val route = "surface/$surface"
    val matching = contributions
        .filter { PluginManager.routeMatches(it.routePattern, route) }
        .sortedByDescending { it.priority }
    val replacement = matching.firstOrNull { it.mode == "replace" }
    val layers = listOfNotNull(replacement) + matching.filterNot { it.mode == "replace" }.reversed()
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (replacement == null) fallback()
        layers.forEach { contribution ->
            PluginSurfaceExtension(contribution, revision, navController)
        }
    }
}

@Composable
private fun PluginSurfaceExtension(
    contribution: PluginScreenContribution,
    revision: Int,
    navController: NavController,
) {
    val session = remember(contribution.pluginId) { PluginManager.getSession(contribution.pluginId) } ?: return
    var tick by remember(contribution.pluginId, contribution.screenId) { mutableIntStateOf(0) }
    val bindingOwner = remember { Any() }
    DisposableEffect(session, navController, bindingOwner) {
        PluginSessionUiBindings.attach(
            session,
            bindingOwner,
            PluginSessionUiBinding(
                navigate = { target ->
                    if (target.startsWith("__app__:")) navController.navigate(target.removePrefix("__app__:"))
                    else navController.navigate("plugin/${contribution.pluginId}/$target")
                },
                refresh = {
                    tick += 1
                    PluginRegistry.touch()
                },
            ),
        )
        onDispose {
            PluginSessionUiBindings.detach(session, bindingOwner)
        }
    }
    val node = remember(contribution.pluginId, contribution.screenId, revision, tick, session.getStateVersion()) {
        session.renderScreen(contribution.screenId)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (contribution.mode == "replace") MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        PluginUiRenderer(
            node = node,
            accent = MaterialTheme.colorScheme.primary,
            onBack = { navController.popBackStack() },
            onCallback = { id, value -> session.onCallback(id, value); tick += 1 },
        )
    }
}

@Composable
private fun PluginScreenExtension(
    contribution: PluginScreenContribution,
    revision: Int,
    navController: NavController,
) {
    val session = remember(contribution.pluginId) { PluginManager.getSession(contribution.pluginId) } ?: return
    var tick by remember(contribution.pluginId, contribution.screenId) { mutableIntStateOf(0) }
    val bindingOwner = remember { Any() }
    DisposableEffect(session, navController, bindingOwner) {
        PluginSessionUiBindings.attach(
            session,
            bindingOwner,
            PluginSessionUiBinding(
                navigate = { target ->
                    if (target.startsWith("__app__:")) {
                        navController.navigate(target.removePrefix("__app__:"))
                    } else {
                        navController.navigate("plugin/${contribution.pluginId}/$target")
                    }
                },
                refresh = {
                    tick += 1
                    PluginRegistry.touch()
                },
            ),
        )
        onDispose {
            PluginSessionUiBindings.detach(session, bindingOwner)
        }
    }
    val node = remember(contribution.pluginId, contribution.screenId, revision, tick, session.getStateVersion()) {
        session.renderScreen(contribution.screenId)
    }
    val alignment = when (contribution.mode) {
        "top" -> Alignment.TopCenter
        "bottom" -> Alignment.BottomCenter
        "fab" -> Alignment.BottomEnd
        else -> Alignment.Center
    }
    val modifier = when (contribution.mode) {
        "replace" -> Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        "fab" -> Modifier.padding(end = 20.dp, bottom = 116.dp)
        "top" -> Modifier.padding(top = 12.dp)
        "bottom" -> Modifier.padding(bottom = 108.dp)
        else -> Modifier.fillMaxSize()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
        if (contribution.mode in setOf("top", "bottom")) {
            Surface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                PluginUiRenderer(
                    node = node,
                    accent = MaterialTheme.colorScheme.primary,
                    onBack = { navController.popBackStack() },
                    onCallback = { id, value -> session.onCallback(id, value); tick += 1 },
                )
            }
        } else {
            Box(modifier) {
                PluginUiRenderer(
                    node = node,
                    accent = MaterialTheme.colorScheme.primary,
                    onBack = { navController.popBackStack() },
                    onCallback = { id, value -> session.onCallback(id, value); tick += 1 },
                )
            }
        }
    }
}
