package com.droid.dolphy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.catalog.components.LiquidButton


@Composable
fun DolphyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    
    liquidTint: Color = Color.Unspecified,
    forTopBar: Boolean = false,
    content: @Composable () -> Unit,
) {
    val liquid = if (forTopBar) isLiquidGlassTopBarChrome() else isLiquidGlassChrome()
    if (liquid && enabled && LocalLiquidGlassBackdrop.current != null) {
        val backdrop = LocalLiquidGlassBackdrop.current!!
        val tint =
            if (liquidTint.isSpecified) liquidTint
            else MaterialTheme.colorScheme.primary
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = Modifier.size(44.dp),
            tint = tint,
            surfaceColor = tint,
            applyDefaultHeight = false,
            contentPaddingHorizontal = 0.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    }
}


@Composable
fun DolphyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit,
) {
    if (isLiquidGlassChrome() && enabled) {
        val backdrop = LocalLiquidGlassBackdrop.current!!
        val tint = colors.containerColor
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            tint = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.primary,
            surfaceColor = if (tint != Color.Unspecified) tint else MaterialTheme.colorScheme.primary,
            content = content,
        )
    } else {
        AccentButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            content = content,
        )
    }
}


@Composable
fun DolphyTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (isLiquidGlassChrome() && enabled) {
        val backdrop = LocalLiquidGlassBackdrop.current!!
        val primary = MaterialTheme.colorScheme.primary
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier,
            tint = primary,
            surfaceColor = primary,
            applyDefaultHeight = true,
            contentPaddingHorizontal = 12.dp,
            content = content,
        )
    } else {
        AccentButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
    }
}


@Composable
fun DolphyLiquidTitlePill(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit,
) {
    if (isLiquidGlassTopBarChrome() && LocalLiquidGlassBackdrop.current != null) {
        val backdrop = LocalLiquidGlassBackdrop.current!!
        LiquidButton(
            onClick = {},
            backdrop = backdrop,
            modifier = modifier.height(44.dp),
            isInteractive = false,
            tint = tint,
            surfaceColor = tint,
            applyDefaultHeight = false,
            contentPaddingHorizontal = 16.dp,
            content = content,
        )
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

