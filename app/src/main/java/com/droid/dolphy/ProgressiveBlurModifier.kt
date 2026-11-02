package com.droid.dolphy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity

enum class BlurDirection {
    TOP,
    BOTTOM
}












@Composable
fun ProgressiveBlurContent(
    modifier: Modifier = Modifier,
    topHeightPx: Float = 0f,
    bottomHeightPx: Float = 0f,
    blurRadius: Float = 18f,
    overlayColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    val isSamsung = remember {
        android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
        android.os.Build.BRAND.equals("samsung", ignoreCase = true)
    }

    if (isSamsung || (topHeightPx <= 0f && bottomHeightPx <= 0f)) {
        Box(modifier) {
            content()
            val density = LocalDensity.current
            if (bottomHeightPx > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { bottomHeightPx.toDp() })
                        .align(Alignment.BottomCenter)
                        .drawWithContent {
                            drawEdgeTint(BlurDirection.BOTTOM, overlayColor)
                        }
                )
            }
            if (topHeightPx > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { topHeightPx.toDp() })
                        .align(Alignment.TopCenter)
                        .drawWithContent {
                            drawEdgeTint(BlurDirection.TOP, overlayColor)
                        }
                )
            }
        }
        return
    }

    val contentLayer = rememberGraphicsLayer()

    Box(modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(contentLayer)
                }
        ) {
            content()
        }
        if (topHeightPx > 0f) {
            BlurEdgeStrip(
                contentLayer = contentLayer,
                heightPx = topHeightPx,
                blurRadius = blurRadius,
                overlayColor = overlayColor,
                direction = BlurDirection.TOP,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        if (bottomHeightPx > 0f) {
            BlurEdgeStrip(
                contentLayer = contentLayer,
                heightPx = bottomHeightPx,
                blurRadius = blurRadius,
                overlayColor = overlayColor,
                direction = BlurDirection.BOTTOM,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun BlurEdgeStrip(
    contentLayer: GraphicsLayer,
    heightPx: Float,
    blurRadius: Float,
    overlayColor: Color,
    direction: BlurDirection,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val blurEffect = remember(blurRadius) {
        runCatching {
            androidx.compose.ui.graphics.BlurEffect(
                radiusX = blurRadius.coerceIn(1f, 30f),
                radiusY = blurRadius.coerceIn(1f, 30f),
                edgeTreatment = TileMode.Decal
            ) as RenderEffect?
        }.getOrNull()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { heightPx.toDp() })
            .graphicsLayer {
                clip = true
                renderEffect = blurEffect
            }
            .drawWithContent {
                val layerHeight = contentLayer.size.height
                if (layerHeight <= 0f) {
                    drawEdgeTint(direction, overlayColor)
                    return@drawWithContent
                }
                when (direction) {
                    BlurDirection.TOP -> drawLayer(contentLayer)
                    BlurDirection.BOTTOM -> {
                        translate(top = size.height - layerHeight) {
                            drawLayer(contentLayer)
                        }
                    }
                }
                val mask = when (direction) {
                    BlurDirection.TOP -> Brush.verticalGradient(
                        startY = 0f,
                        endY = size.height,
                        colorStops = arrayOf(
                            0f to Color.Black,
                            0.55f to Color.Black.copy(alpha = 0.45f),
                            1f to Color.Transparent
                        )
                    )
                    BlurDirection.BOTTOM -> Brush.verticalGradient(
                        startY = 0f,
                        endY = size.height,
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.45f),
                            1f to Color.Black
                        )
                    )
                }
                drawRect(brush = mask, blendMode = BlendMode.DstIn)
                drawEdgeTint(direction, overlayColor)
            }
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEdgeTint(
    direction: BlurDirection,
    overlayColor: Color
) {
    val brush = when (direction) {
        BlurDirection.TOP -> Brush.verticalGradient(
            colors = listOf(overlayColor, Color.Transparent)
        )
        BlurDirection.BOTTOM -> Brush.verticalGradient(
            colors = listOf(Color.Transparent, overlayColor)
        )
    }
    drawRect(brush = brush)
}
