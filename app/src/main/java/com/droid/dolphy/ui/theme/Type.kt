package com.droid.dolphy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.droid.dolphy.R

import androidx.compose.ui.text.font.FontWeight

private val AppFontFamily = FontFamily(Font(R.font.born2b_sporty_v2))
private val GoogleSansBold = FontFamily.SansSerif

fun buildAppTypography(useFlipperFont: Boolean, fontScale: Float): Typography {
    val family = if (useFlipperFont) AppFontFamily else GoogleSansBold
    val weight = FontWeight.Bold


    val baseScale = if (useFlipperFont) fontScale else 0.85f
    val safeScale = baseScale.coerceIn(0.6f, 1.6f)
    val base = Typography()

    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family, fontWeight = weight, fontSize = base.displayLarge.fontSize * safeScale, letterSpacing = 0.3.sp),
        displayMedium = base.displayMedium.copy(fontFamily = family, fontWeight = weight, fontSize = base.displayMedium.fontSize * safeScale, letterSpacing = 0.3.sp),
        displaySmall = base.displaySmall.copy(fontFamily = family, fontWeight = weight, fontSize = base.displaySmall.fontSize * safeScale, letterSpacing = 0.3.sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = family, fontWeight = weight, fontSize = base.headlineLarge.fontSize * safeScale, letterSpacing = 0.25.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = family, fontWeight = weight, fontSize = base.headlineMedium.fontSize * safeScale, letterSpacing = 0.25.sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = family, fontWeight = weight, fontSize = base.headlineSmall.fontSize * safeScale, letterSpacing = 0.25.sp),
        titleLarge = base.titleLarge.copy(fontFamily = family, fontWeight = weight, fontSize = base.titleLarge.fontSize * safeScale, letterSpacing = 0.25.sp),
        titleMedium = base.titleMedium.copy(fontFamily = family, fontWeight = weight, fontSize = base.titleMedium.fontSize * safeScale, letterSpacing = 0.2.sp),
        titleSmall = base.titleSmall.copy(fontFamily = family, fontWeight = weight, fontSize = base.titleSmall.fontSize * safeScale, letterSpacing = 0.2.sp),
        bodyLarge = base.bodyLarge.copy(fontFamily = family, fontWeight = weight, fontSize = base.bodyLarge.fontSize * safeScale, letterSpacing = 0.2.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = family, fontWeight = weight, fontSize = base.bodyMedium.fontSize * safeScale, letterSpacing = 0.2.sp),
        bodySmall = base.bodySmall.copy(fontFamily = family, fontWeight = weight, fontSize = base.bodySmall.fontSize * safeScale, letterSpacing = 0.2.sp),
        labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = weight, fontSize = base.labelLarge.fontSize * safeScale, letterSpacing = 0.2.sp),
        labelMedium = base.labelMedium.copy(fontFamily = family, fontWeight = weight, fontSize = base.labelMedium.fontSize * safeScale, letterSpacing = 0.2.sp),
        labelSmall = base.labelSmall.copy(fontFamily = family, fontWeight = weight, fontSize = base.labelSmall.fontSize * safeScale, letterSpacing = 0.2.sp),
    )
}



fun buildExpressiveTypography(useFlipperFont: Boolean, fontScale: Float): Typography {
    val base = buildAppTypography(useFlipperFont, fontScale)
    return base.copy(
        displayLarge = base.displayLarge.copy(fontSize = base.displayLarge.fontSize * 1.12f),
        displayMedium = base.displayMedium.copy(fontSize = base.displayMedium.fontSize * 1.1f),
        displaySmall = base.displaySmall.copy(fontSize = base.displaySmall.fontSize * 1.08f),
        headlineLarge = base.headlineLarge.copy(fontSize = base.headlineLarge.fontSize * 1.08f),
        headlineMedium = base.headlineMedium.copy(fontSize = base.headlineMedium.fontSize * 1.06f),
        headlineSmall = base.headlineSmall.copy(fontSize = base.headlineSmall.fontSize * 1.04f),
        titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize * 1.05f),
        titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize * 1.04f),
        titleSmall = base.titleSmall.copy(fontSize = base.titleSmall.fontSize * 1.02f),
    )
}

val Typography = buildAppTypography(useFlipperFont = true, fontScale = 1.08f)

