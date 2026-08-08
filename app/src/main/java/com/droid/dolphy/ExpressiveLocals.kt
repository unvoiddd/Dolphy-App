package com.droid.dolphy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.Backdrop

val LocalExpressiveEnabled = staticCompositionLocalOf { false }
val LocalAnimatedBackgroundEnabled = staticCompositionLocalOf { false }


val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }


val LocalLiquidGlassButtons = staticCompositionLocalOf { true }


val LocalLiquidGlassTopBars = staticCompositionLocalOf { true }


val LocalLiquidGlassNav = staticCompositionLocalOf { true }


val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }


val LocalLiquidGlassContentBackdrop = staticCompositionLocalOf<Backdrop?> { null }


@Composable
fun isLiquidGlassChrome(): Boolean =
    LocalLiquidGlassEnabled.current &&
        LocalLiquidGlassButtons.current &&
        LocalLiquidGlassBackdrop.current != null


@Composable
fun isLiquidGlassTopBarChrome(): Boolean =
    LocalLiquidGlassEnabled.current &&
        LocalLiquidGlassTopBars.current &&
        LocalLiquidGlassBackdrop.current != null


@Composable
fun isLiquidGlassNavChrome(): Boolean =
    LocalLiquidGlassEnabled.current && LocalLiquidGlassNav.current

