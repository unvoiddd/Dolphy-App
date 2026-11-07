package com.droid.dolphy.plugin.store

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberTranslated(text: String): String {
    if (text.isBlank()) return text
    val context = LocalContext.current
    remember { PluginStoreRepository.initialize(context.applicationContext) }
    val enabled by PluginStoreRepository.translateEnabled.collectAsState()
    if (!enabled) return text

    var result by remember(text) {
        mutableStateOf(PluginTranslator.cachedOnly(text) ?: text)
    }
    LaunchedEffect(text, enabled) {
        if (PluginTranslator.cachedOnly(text) == null) {
            val translated = PluginTranslator.translate(text)
            if (!translated.isNullOrBlank()) result = translated
        } else {
            result = PluginTranslator.cachedOnly(text) ?: text
        }
    }
    return result
}
