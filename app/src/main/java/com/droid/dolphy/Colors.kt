package com.droid.dolphy

import androidx.compose.ui.graphics.Color


val DarkBackground = Color(0xFF0F0E0D)
val LightBackground = Color(0xFFF9F9F9)
val BrighterSurface = Color(0xFF22201E)
val OrangeAccent = Color(0xFFFF9800)
val OrangeBorder = Color(0xFFFFCC80)
val TextWhite = Color(0xFFFFFFFF)
val TextGray = Color(0xFFCFD8DC)
val GreenSuccess = Color(0xFFB9F6CA)


val RedThemeAccent = Color(0xFFFF5252)
val GreenThemeAccent = Color(0xFF69F0AE)
val PurpleThemeAccent = Color(0xFFE040FB)
val CyanThemeAccent = Color(0xFF18FFFF)



val SplashGradientStart = Color(0xFF0D1B2A)
val SplashGradientEnd = Color(0xFF1B263B)
val SplashAccentGradient = Color(0xFF415A77)


val OnboardingGradientStart = Color(0xFF1A1A2E)
val OnboardingGradientMiddle = Color(0xFF16213E)
val OnboardingGradientEnd = Color(0xFF0F3460)
val OnboardingAccent = Color(0xFF7B68EE)
val OnboardingHighlight = Color(0xFF00D9FF)


val CardBackgroundDark = Color(0xFF1E1E2E)
val CardBackgroundLight = Color(0xFF2A2A3E)
val SurfaceElevated = Color(0xFF252540)






val OrangeGradientStart = Color(0xFFFF6B00)
val OrangeGradientMiddle = Color(0xFFF98022)
val OrangeGradientEnd = Color(0xFFFFD600)
val OrangeGradientDeep = Color(0xFFFF2200)


val GradientDarkStart = Color(0xFF0D0D0D)
val GradientDarkMiddle = Color(0xFF1A1A1A)
val GradientDarkEnd = Color(0xFF2D2D2D)


val SurfaceVolumetricDark = Color(0xFF1E1E24)
val SurfaceVolumetricLight = Color(0xFF2A2A32)
val SurfaceHighlight = Color(0xFF3A3A44)


val ShadowDark = Color(0xFF000000)
val ShadowLight = Color(0xFF1A1A1A)


val GlowAccent = Color(0x40F98022)
val GlowAccentIntense = Color(0x60F98022)
val GlowHighlight = Color(0x80FFFFFF)


val CardBorderDark = Color(0xFF3A3A3A)
val CardBorderLight = Color(0xFF4A4A4A)


val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B0B0)
val TextTertiary = Color(0xFF808080)

fun contrastingContentColor(color: Color): Color {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return if (luminance > 0.56f) Color(0xFF18120D) else Color.White
}










fun getGradientColors(accentColor: Color): List<Color> {
    return when (accentColor) {
        OrangeAccent -> listOf(
            OrangeGradientStart,
            OrangeGradientMiddle,
            OrangeGradientEnd
        )
        RedThemeAccent -> listOf(
            Color(0xFFFF0000),
            Color(0xFFCC0000),
            Color(0xFFFF6666)
        )
        GreenThemeAccent -> listOf(
            Color(0xFF00FF00),
            Color(0xFF00CC00),
            Color(0xFF66FF66)
        )
        PurpleThemeAccent -> listOf(
            Color(0xFFBB33FF),
            Color(0xFF9900CC),
            Color(0xFFDD66FF)
        )
        CyanThemeAccent -> listOf(
            Color(0xFF00DDFF),
            Color(0xFF00AACC),
            Color(0xFF66EEFF)
        )
        else -> listOf(
            accentColor,
            accentColor.copy(alpha = 0.7f),
            accentColor.copy(alpha = 0.9f)
        )
    }
}




fun getGlowColor(accentColor: Color): Color {
    return when (accentColor) {
        OrangeAccent -> OrangeGradientMiddle.copy(alpha = 0.4f)
        RedThemeAccent -> Color(0x60FF3333)
        GreenThemeAccent -> Color(0x6000DD44)
        PurpleThemeAccent -> Color(0x60BB33FF)
        CyanThemeAccent -> Color(0x6000DDFF)
        else -> Color(0x40FFFFFF)
    }
}





fun getSplashGradientColors(accentColor: Color): List<Color> {
    return when (accentColor) {
        OrangeAccent -> listOf(
            Color(0xFF1A0A00),
            Color(0xFF2D1200),
            Color(0xFF1B263B),
            Color(0xFF2D1200)
        )
        RedThemeAccent -> listOf(
            Color(0xFF1A0000),
            Color(0xFF2D0000),
            Color(0xFF1B263B),
            Color(0xFF2D0000)
        )
        GreenThemeAccent -> listOf(
            Color(0xFF001A00),
            Color(0xFF002D00),
            Color(0xFF1B263B),
            Color(0xFF002D00)
        )
        PurpleThemeAccent -> listOf(
            Color(0xFF1A001A),
            Color(0xFF2D002D),
            Color(0xFF1B263B),
            Color(0xFF2D002D)
        )
        CyanThemeAccent -> listOf(
            Color(0xFF001A1A),
            Color(0xFF002D2D),
            Color(0xFF1B263B),
            Color(0xFF002D2D)
        )
        else -> listOf(
            SplashGradientStart,
            SplashGradientEnd,
            SplashGradientStart.copy(alpha = 0.8f)
        )
    }
}

