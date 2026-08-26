package com.droid.dolphy

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun DolphySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val haptic = LocalHapticFeedback.current
    val onToggle: (Boolean) -> Unit = { checkedNow ->
        if (enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onCheckedChange(checkedNow)
        }
    }

    Switch(
        checked = checked,
        onCheckedChange = onToggle,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        thumbContent = {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    )
}

@Composable
fun DolphySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: androidx.compose.material3.SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource
    )
}

@Composable
fun DolphyLinearProgressIndicator(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    strokeCap: StrokeCap = StrokeCap.Round
) {
    if (LocalExpressiveEnabled.current) {
        ExpressiveLinearProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor
        )
    } else {
        if (progress == null) {
            LinearProgressIndicator(modifier = modifier, color = color, trackColor = trackColor, strokeCap = strokeCap)
        } else {
            LinearProgressIndicator(progress = progress, modifier = modifier, color = color, trackColor = trackColor, strokeCap = strokeCap)
        }
    }
}

@Composable
fun DolphyCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 4.dp
) {
    if (LocalExpressiveEnabled.current) {
        ExpressiveCircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
    } else {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
    }
}

@Composable
private fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = colorScheme.primary,
            uncheckedTrackColor = colorScheme.surfaceVariant,
            checkedThumbColor = colorScheme.onPrimary,
            uncheckedThumbColor = colorScheme.onSurfaceVariant
        ),
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
        }
    )
}

@Composable
private fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier) {
        val colorScheme = MaterialTheme.colorScheme
        val phase = rememberInfiniteTransition(label = "wave").animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing)
            ),
            label = "wave_phase"
        )
        val trackHeight = 10.dp
        val amplitude = 4.dp

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        ) {
            val w = size.width
            val h = size.height / 2f
            val ampPx = amplitude.toPx()
            val pathInactive = Path()
            val pathActive = Path()
            val progress = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val activeW = w * progress

            val step = 6f
            var x = 0f
            while (x <= w) {
            val y = h + (sin((x / w) * 2f * PI.toFloat() + phase.value).toFloat() * ampPx)
                if (x == 0f) pathInactive.moveTo(x, y) else pathInactive.lineTo(x, y)
                if (x <= activeW) {
                    if (x == 0f) pathActive.moveTo(x, y) else pathActive.lineTo(x, y)
                }
                x += step
            }

            drawPath(pathInactive, color = colorScheme.outlineVariant, style = Stroke(width = trackHeight.toPx(), cap = StrokeCap.Round))
            drawPath(pathActive, color = colorScheme.primary, style = Stroke(width = trackHeight.toPx(), cap = StrokeCap.Round))
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                thumbColor = colorScheme.primaryContainer
            )
        )
    }
}

@Composable
fun ExpressiveLinearProgressIndicator(
    progress: Float? = null,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified
) {
    val phase = rememberInfiniteTransition(label = "wave_progress").animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing)
        ),
        label = "wave_progress_phase"
    )
    val colorScheme = MaterialTheme.colorScheme
    val activeColor = if (color == Color.Unspecified) colorScheme.primary else color
    val inactiveColor = if (trackColor == Color.Unspecified) colorScheme.outlineVariant else trackColor

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
    ) {
        val w = size.width
        val h = size.height / 2f
        val amp = size.height * 0.28f
        val step = 6f
        val activeW = if (progress != null) w * progress.coerceIn(0f, 1f) else w

        val pathInactive = Path()
        val pathActive = Path()

        var x = 0f
        while (x <= w) {
            val y = h + (sin((x / w) * 2f * PI.toFloat() + phase.value).toFloat() * amp)
            if (x == 0f) pathInactive.moveTo(x, y) else pathInactive.lineTo(x, y)
            if (x <= activeW) {
                if (x == 0f) pathActive.moveTo(x, y) else pathActive.lineTo(x, y)
            }
            x += step
        }

        drawPath(pathInactive, color = inactiveColor, style = Stroke(width = size.height, cap = StrokeCap.Round))
        drawPath(pathActive, color = activeColor, style = Stroke(width = size.height, cap = StrokeCap.Round))
    }
}

@Composable
fun ExpressiveCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    strokeWidth: Dp = 4.dp
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color,
        strokeWidth = strokeWidth
    )
}

