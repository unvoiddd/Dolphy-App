package com.droid.dolphy

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.intellij.lang.annotations.Language

enum class BlurDirection {
    TOP,
    BOTTOM
}

/**
 *
 */
@Language("AGSL")
private val PROGRESSIVE_BLUR_SKSL = """
    uniform shader content;
    uniform float blurRadius;
    uniform float height;
    uniform float contentHeight;
    uniform int isTop;

    half4 main(float2 fragCoord) {
        float progress;
        if (isTop == 1) {
            progress = 1.0 - clamp(fragCoord.y / height, 0.0, 1.0);
        } else {
            progress = 1.0 - clamp((contentHeight - fragCoord.y) / height, 0.0, 1.0);
        }

        // Easing curve for a smoother transition.
        progress = pow(progress, 1.5);

        float radius = progress * blurRadius;

        if (radius <= 0.0) {
            return content.eval(fragCoord);
        }

        half4 accum = half4(0.0);
        float weightSum = 0.0;

        // Dither to hide banding.
        float dither = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
        float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);

        const int SAMPLES = 4;
        float offsetScale = radius / float(SAMPLES);

        for (int x = -SAMPLES; x <= SAMPLES; x++) {
            for (int y = -SAMPLES; y <= SAMPLES; y++) {
                float2 offset = (float2(float(x), float(y)) + jitter) * offsetScale;
                float distSq = dot(offset, offset);
                float radiusSq = radius * radius;
                if (distSq <= radiusSq) {
                    float weight = exp(-3.0 * distSq / radiusSq);
                    accum += content.eval(fragCoord + offset) * weight;
                    weightSum += weight;
                }
            }
        }

        return accum / weightSum;
    }
""".trimIndent()

fun Modifier.progressiveBlur(
    blurRadius: Float,
    height: Float,
    direction: BlurDirection = BlurDirection.BOTTOM,
    showGradientOverlay: Boolean = true,
    overlayColor: Color = Color.Unspecified,
): Modifier = composed {
    val tint = if (overlayColor.isSpecified()) overlayColor
    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f)

    val isSamsung = remember {
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
            Build.BRAND.equals("samsung", ignoreCase = true)
    }

    val blurModifier =
        if (!isSamsung && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && blurRadius > 0f && height > 0f) {
            Modifier.graphicsLayer {
                if (size.height <= 0f) return@graphicsLayer
                val shader = RuntimeShader(PROGRESSIVE_BLUR_SKSL)
                shader.setFloatUniform("blurRadius", blurRadius)
                shader.setFloatUniform("height", height)
                shader.setFloatUniform("contentHeight", size.height)
                shader.setIntUniform("isTop", if (direction == BlurDirection.TOP) 1 else 0)
                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(shader, "content")
                    .asComposeRenderEffect()
            }
        } else {
            Modifier
        }

    val gradientModifier = if (showGradientOverlay && height > 0f) {
        Modifier.drawWithContent {
            drawContent()
            val brush = when (direction) {
                BlurDirection.TOP -> Brush.verticalGradient(
                    colors = listOf(tint, Color.Transparent),
                    endY = height,
                )
                BlurDirection.BOTTOM -> Brush.verticalGradient(
                    colors = listOf(Color.Transparent, tint),
                    startY = size.height - height,
                )
            }
            drawRect(brush = brush)
        }
    } else {
        Modifier
    }

    this.then(blurModifier).then(gradientModifier)
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified
