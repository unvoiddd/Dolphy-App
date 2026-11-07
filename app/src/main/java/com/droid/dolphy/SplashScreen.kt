package com.droid.dolphy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(2200)
        onSplashComplete()
    }

    MatrixSplashContent()
}

private data class MatrixGlyph(
    val iconIndex: Int,
    val xFraction: Float,
    val startFraction: Float,
    val speed: Float,
    val sizeDp: Int,
    val alpha: Float
)

private val matrixGlyphIcons = listOf(
    Icons.Default.Bluetooth,
    Icons.Default.Wifi,
    Icons.Default.Nfc,
    Icons.Default.Usb,
    Icons.Default.PhoneAndroid,
    Icons.Default.Router,
    Icons.Default.SignalCellularAlt,
    Icons.Default.Cast,
    Icons.Default.Devices,
    Icons.Default.SettingsInputAntenna,
    Icons.Default.BugReport,
    Icons.Default.Cable,
    Icons.Default.DevicesOther
)

@Composable
fun MatrixIconField(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    glyphCount: Int = 120,
    columns: Int = 14,
    minSizeDp: Int = 10,
    sizeVarianceDp: Int = 9,
    minAlpha: Float = 0.16f,
    alphaVariance: Float = 0.48f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "matrix_icons")
    val progressState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "matrix_progress"
    )

    val painters = matrixGlyphIcons.map { rememberVectorPainter(it) }

    val glyphs = remember(glyphCount, columns, minSizeDp, sizeVarianceDp, minAlpha, alphaVariance) {
        val random = Random(42)
        List(glyphCount) { index ->
            val column = index % columns
            MatrixGlyph(
                iconIndex = random.nextInt(matrixGlyphIcons.size),
                xFraction = ((column + 0.5f) / columns.toFloat()) + random.nextFloat() * 0.035f - 0.0175f,
                startFraction = random.nextFloat() * 1.35f,
                speed = 0.26f + random.nextFloat() * 0.52f,
                sizeDp = minSizeDp + random.nextInt(sizeVarianceDp),
                alpha = minAlpha + random.nextFloat() * alphaVariance
            )
        }
    }

    Canvas(modifier = modifier) {
        val progress = progressState.value
        val widthPx = size.width
        val heightPx = size.height
        if (widthPx <= 0f || heightPx <= 0f) return@Canvas

        val glyphsCount = glyphs.size
        for (i in 0 until glyphsCount) {
            val glyph = glyphs[i]
            val x = widthPx * glyph.xFraction
            val yProgress = (glyph.startFraction + progress * glyph.speed) % 1.35f
            val y = heightPx * yProgress - heightPx * 0.15f
            val sizePx = glyph.sizeDp.dp.toPx()
            val painter = painters[glyph.iconIndex]

            translate(left = x, top = y) {
                with(painter) {
                    draw(
                        size = Size(sizePx, sizePx),
                        alpha = glyph.alpha,
                        colorFilter = ColorFilter.tint(accentColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixSplashContent() {
    val accentColor = MaterialTheme.colorScheme.primary
    val titleAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 500),
        label = "splash_title_alpha"
    )

    val backgroundTop = Color(0xFF04070B)
    val backgroundMid = accentColor.copy(alpha = 0.10f)
    val backgroundBottom = Color(0xFF020304)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(backgroundTop, backgroundMid, backgroundBottom)
                )
            )
    ) {
        MatrixIconField(
            modifier = Modifier.fillMaxSize(),
            accentColor = accentColor
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Dolphy",
                color = Color.White.copy(alpha = titleAlpha),
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge.merge(
                    TextStyle(
                        shadow = Shadow(
                            color = accentColor.copy(alpha = 0.65f),
                            blurRadius = 28f
                        )
                    )
                ),
                letterSpacing = 2.sp,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
