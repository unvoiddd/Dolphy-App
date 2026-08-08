package com.droid.dolphy

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay

@Composable
fun TintedFrameAnimation(
    @DrawableRes frames: List<Int>,
    modifier: Modifier = Modifier,
    frameDelayMs: Long = 75L,
    tintColor: Color = Color.Unspecified,
    tintAlpha: Float = 0.58f,
    contentDescription: String? = null,
    frameFilters: List<ColorFilter?>? = null
) {
    if (frames.isEmpty()) return
    var frameIndex by remember(frames) { mutableIntStateOf(0) }

    LaunchedEffect(frames, frameDelayMs) {
        if (frames.size <= 1) return@LaunchedEffect
        while (true) {
            delay(frameDelayMs.coerceAtLeast(1L))
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    val currentFilter = if (frameFilters != null && frameIndex < frameFilters.size) {
        frameFilters[frameIndex]
    } else if (tintColor != Color.Unspecified) {
        ColorFilter.tint(
            color = tintColor.copy(alpha = tintAlpha),
            blendMode = BlendMode.Modulate
        )
    } else {
        null
    }

    Image(
        painter = painterResource(id = frames[frameIndex]),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = currentFilter
    )
}

