@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.droid.dolphy

import android.bluetooth.le.*
import android.content.*
import android.os.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.droid.dolphy.nfc.ui.*
import com.droid.dolphy.qr.*
import com.droid.dolphy.ui.theme.buildAppTypography
import com.droid.dolphy.ui.theme.buildExpressiveTypography
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot

@Composable
fun DolphyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: Color = OrangeAccent,
    isAdaptiveColor: Boolean = false,
    useFlipperFont: Boolean = true,
    flipperFontScale: Float = 1.08f,
    uiScale: Float = 1f,
    animatedBackgroundEnabled: Boolean = false,
    expressiveEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val expressiveScheme = if (isAdaptiveColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else remember(accentColor, darkTheme) {
        googleExpressiveColorScheme(accentColor, darkTheme)
    }

    val typography = if (expressiveEnabled) {
        buildExpressiveTypography(useFlipperFont = useFlipperFont, fontScale = flipperFontScale)
    } else {
        buildAppTypography(useFlipperFont = useFlipperFont, fontScale = flipperFontScale)
    }

    val baseDensity = LocalDensity.current
    val safeUiScale = uiScale.coerceIn(0.8f, 1.2f)
    val scaledDensity = remember(baseDensity.density, baseDensity.fontScale, safeUiScale) {
        Density(baseDensity.density * safeUiScale, baseDensity.fontScale)
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalExpressiveEnabled provides true,
        LocalAnimatedBackgroundEnabled provides animatedBackgroundEnabled,
        LocalDensity provides scaledDensity,
    ) {
        MaterialExpressiveTheme(
            colorScheme = expressiveScheme,
            typography = typography,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}

@Suppress("RestrictedApi")
private fun googleExpressiveColorScheme(seedColor: Color, darkTheme: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seedColor.toArgb()), darkTheme, 0.0)
    val colors = MaterialDynamicColors()
    fun role(value: com.google.android.material.color.utilities.DynamicColor): Color =
        Color(value.getArgb(scheme))

    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = role(colors.primary()),
        onPrimary = role(colors.onPrimary()),
        primaryContainer = role(colors.primaryContainer()),
        onPrimaryContainer = role(colors.onPrimaryContainer()),
        inversePrimary = role(colors.inversePrimary()),
        secondary = role(colors.secondary()),
        onSecondary = role(colors.onSecondary()),
        secondaryContainer = role(colors.secondaryContainer()),
        onSecondaryContainer = role(colors.onSecondaryContainer()),
        tertiary = role(colors.tertiary()),
        onTertiary = role(colors.onTertiary()),
        tertiaryContainer = role(colors.tertiaryContainer()),
        onTertiaryContainer = role(colors.onTertiaryContainer()),
        background = role(colors.background()),
        onBackground = role(colors.onBackground()),
        surface = role(colors.surface()),
        onSurface = role(colors.onSurface()),
        surfaceVariant = role(colors.surfaceVariant()),
        onSurfaceVariant = role(colors.onSurfaceVariant()),
        surfaceTint = role(colors.surfaceTint()),
        inverseSurface = role(colors.inverseSurface()),
        inverseOnSurface = role(colors.inverseOnSurface()),
        error = role(colors.error()),
        onError = role(colors.onError()),
        errorContainer = role(colors.errorContainer()),
        onErrorContainer = role(colors.onErrorContainer()),
        outline = role(colors.outline()),
        outlineVariant = role(colors.outlineVariant()),
        scrim = role(colors.scrim()),
        surfaceBright = role(colors.surfaceBright()),
        surfaceDim = role(colors.surfaceDim()),
        surfaceContainer = role(colors.surfaceContainer()),
        surfaceContainerHigh = role(colors.surfaceContainerHigh()),
        surfaceContainerHighest = role(colors.surfaceContainerHighest()),
        surfaceContainerLow = role(colors.surfaceContainerLow()),
        surfaceContainerLowest = role(colors.surfaceContainerLowest()),
        primaryFixed = role(colors.primaryFixed()),
        primaryFixedDim = role(colors.primaryFixedDim()),
        onPrimaryFixed = role(colors.onPrimaryFixed()),
        onPrimaryFixedVariant = role(colors.onPrimaryFixedVariant()),
        secondaryFixed = role(colors.secondaryFixed()),
        secondaryFixedDim = role(colors.secondaryFixedDim()),
        onSecondaryFixed = role(colors.onSecondaryFixed()),
        onSecondaryFixedVariant = role(colors.onSecondaryFixedVariant()),
        tertiaryFixed = role(colors.tertiaryFixed()),
        tertiaryFixedDim = role(colors.tertiaryFixedDim()),
        onTertiaryFixed = role(colors.onTertiaryFixed()),
        onTertiaryFixedVariant = role(colors.onTertiaryFixedVariant()),
    )
}

private data class TonalPalette(
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val primaryContainerLight: Color,
    val onPrimaryContainerLight: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val secondaryContainerLight: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val tertiaryContainerLight: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceVariantLight: Color,
    val onSurfaceVariantLight: Color,
    val surfaceContainerHighest: Color,
    val surfaceContainerHighestLight: Color,
    val outline: Color,
    val outlineVariant: Color,
    val outlineLight: Color,
    val outlineVariantLight: Color,
    val errorContainer: Color,
    val inversePrimary: Color
)

private fun generateTonalPalette(accentColor: Color): TonalPalette {
    val red = accentColor.red
    val green = accentColor.green
    val blue = accentColor.blue

    val luminance = (0.299f * red + 0.587f * green + 0.114f * blue)

    val maxColor = maxOf(red, green, blue)
    val minColor = minOf(red, green, blue)
    val saturation = if (maxColor > 0f) (maxColor - minColor) / maxColor else 0f

    val hue = when {
        maxColor == minColor -> 0f
        maxColor == red -> 60f * (((green - blue) / (maxColor - minColor)) % 6f)
        maxColor == green -> 60f * (((blue - red) / (maxColor - minColor)) + 2f)
        else -> 60f * (((red - green) / (maxColor - minColor)) + 4f)
    }

    fun hslColor(h: Float, s: Float, l: Float, warmth: Float = 0.02f): Color {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f

        val r: Float
        val g: Float
        val b: Float
        when {
            h < 60f -> { r = c; g = x; b = 0f }
            h < 120f -> { r = x; g = c; b = 0f }
            h < 180f -> { r = 0f; g = c; b = x }
            h < 240f -> { r = 0f; g = x; b = c }
            h < 300f -> { r = x; g = 0f; b = c }
            else -> { r = c; g = 0f; b = x }
        }

        return Color(
            red = (r + m + warmth).coerceIn(0f, 1f),
            green = (g + m + warmth * 0.4f).coerceIn(0f, 1f),
            blue = (b + m).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    fun Float.coerceFloat(min: Float, max: Float): Float = this.coerceIn(min, max)

    val darkerVariant = hslColor(hue, saturation.coerceFloat(0.2f, 0.9f), (luminance * 0.5f).coerceFloat(0.08f, 0.35f))
    val lighterVariant = hslColor(hue, (saturation * 0.5f).coerceFloat(0.15f, 0.4f), (luminance + 0.4f).coerceFloat(0.75f, 0.92f))
    val surfaceVariantDark = hslColor(hue, (saturation * 0.4f).coerceFloat(0.1f, 0.35f), (luminance * 0.18f).coerceFloat(0.06f, 0.12f))
    val surfaceVariantLight = hslColor(hue, (saturation * 0.15f).coerceFloat(0.04f, 0.12f), (luminance + 0.08f).coerceFloat(0.92f, 0.98f))

    val surfaceContainerHighestDark = hslColor(hue, (saturation * 0.4f).coerceFloat(0.12f, 0.38f), (luminance * 0.22f).coerceFloat(0.08f, 0.15f))
    val surfaceContainerHighestLight = hslColor(hue, (saturation * 0.12f).coerceFloat(0.06f, 0.15f), (luminance + 0.12f).coerceFloat(0.88f, 0.94f))

    val complementaryHue = (hue + 180f) % 360f
    val tertiary = hslColor(complementaryHue, (saturation * 0.95f).coerceFloat(0.6f, 1f), (luminance * 1.1f).coerceFloat(0.5f, 0.85f))

    val secondaryHue = (hue + 60f) % 360f
    val secondary = hslColor(secondaryHue, (saturation * 0.85f).coerceFloat(0.5f, 1f), (luminance * 0.85f).coerceFloat(0.4f, 0.75f))

    val onPrimaryContainer = if (luminance > 0.5f) Color.Black else Color.White
    val onPrimaryContainerLight = if (luminance > 0.7f) Color.Black else Color.White
    val onSecondaryContainer = if (luminance > 0.5f) Color.Black else Color.White
    val onTertiaryContainer = if (luminance > 0.5f) Color.Black else Color.White

    val outlineDark = hslColor(hue, (saturation * 0.4f).coerceFloat(0.1f, 0.3f), (luminance * 0.4f).coerceFloat(0.2f, 0.35f))
    val outlineLight = hslColor(hue, (saturation * 0.5f).coerceFloat(0.2f, 0.4f), (luminance * 0.7f).coerceFloat(0.5f, 0.6f))

    val inversePrimary = hslColor((hue + 180f) % 360f, saturation, (1f - luminance * 0.5f).coerceFloat(0.4f, 0.8f))

    return TonalPalette(
        primaryContainer = darkerVariant,
        onPrimaryContainer = onPrimaryContainer,
        primaryContainerLight = lighterVariant,
        onPrimaryContainerLight = onPrimaryContainerLight,
        secondary = secondary,
        secondaryContainer = hslColor(secondaryHue, (saturation * 0.5f).coerceFloat(0.2f, 0.4f), (luminance * 0.35f).coerceFloat(0.15f, 0.25f)),
        onSecondaryContainer = onSecondaryContainer,
        secondaryContainerLight = hslColor(secondaryHue, (saturation * 0.3f).coerceFloat(0.1f, 0.2f), (luminance + 0.15f).coerceFloat(0.85f, 0.95f)),
        tertiary = tertiary,
        tertiaryContainer = hslColor(complementaryHue, (saturation * 0.4f).coerceFloat(0.15f, 0.3f), (luminance * 0.3f).coerceFloat(0.12f, 0.2f)),
        onTertiaryContainer = onTertiaryContainer,
        tertiaryContainerLight = hslColor(complementaryHue, (saturation * 0.25f).coerceFloat(0.1f, 0.2f), (luminance + 0.2f).coerceFloat(0.88f, 0.96f)),
        surfaceVariant = surfaceVariantDark,
        onSurfaceVariant = hslColor(hue, (saturation * 0.2f).coerceFloat(0.05f, 0.15f), (luminance * 0.6f + 0.25f).coerceFloat(0.55f, 0.8f)),
        surfaceVariantLight = surfaceVariantLight,
        onSurfaceVariantLight = hslColor(hue, (saturation * 0.1f).coerceFloat(0.02f, 0.08f), (luminance * 0.3f + 0.3f).coerceFloat(0.45f, 0.6f)),
        surfaceContainerHighest = surfaceContainerHighestDark,
        surfaceContainerHighestLight = surfaceContainerHighestLight,
        outline = outlineDark,
        outlineVariant = outlineDark.copy(alpha = 0.5f),
        outlineLight = outlineLight,
        outlineVariantLight = outlineLight.copy(alpha = 0.5f),
        errorContainer = hslColor(0f, (saturation * 0.5f).coerceFloat(0.2f, 0.4f), (luminance * 0.2f).coerceFloat(0.08f, 0.15f)),
        inversePrimary = inversePrimary
    )
}

internal fun isLightColor(color: Color): Boolean {
    val luminance = (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return luminance > 0.5f
}

@Composable
fun GridBackground(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier.fillMaxSize()) {
        val gridSize = 20f
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = colorScheme.primary.copy(alpha = 0.08f),
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = 0.5f
            )
            x += gridSize
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = colorScheme.primary.copy(alpha = 0.08f),
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 0.5f
            )
            y += gridSize
        }
    }
}

@Composable
fun RetroContainer(
    modifier: Modifier = Modifier,
    borderWidth: Dp = 2.dp,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .border(borderWidth, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        content()
    }
}
