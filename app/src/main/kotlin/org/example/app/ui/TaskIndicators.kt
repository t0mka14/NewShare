package org.example.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.example.app.domain.config.IndicatorType

/**
 * Live recording feedback on VOCAL task screens (§6.2), fed by
 * `ContinuousSessionRecorder.levels` via `TaskComponent.Content.Vocal.level`. Both variants are
 * purely presentational — the rolling history buffer for [WAVEFORM] is local Compose display
 * state (like `animateFloatAsState`), not domain state; it carries no business meaning and is
 * never read back by any component.
 */
@Composable
fun TaskLevelIndicator(
    indicatorType: IndicatorType,
    level: Float,
    modifier: Modifier = Modifier,
) {
    when (indicatorType) {
        IndicatorType.CIRCLE -> CircleLevelIndicator(level, modifier)
        IndicatorType.WAVEFORM -> WaveformLevelIndicator(level, modifier)
    }
}

@Composable
private fun CircleLevelIndicator(level: Float, modifier: Modifier = Modifier) {
    val clamped = level.coerceIn(0f, 1f)
    val minRadius = 40f
    val maxRadius = 130f
    val targetRadius = minRadius + clamped * (maxRadius - minRadius)
    val animatedRadius by animateFloatAsState(
        targetValue = targetRadius,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
    )
    val animatedAlpha by animateFloatAsState(targetValue = 0.35f + clamped * 0.65f)

    val baseColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f)
    val levelColor = MaterialTheme.colors.secondary

    Canvas(modifier = modifier.size(160.dp).testTag(TestTags.Task.LEVEL_INDICATOR)) {
        drawCircle(color = baseColor, radius = minRadius)
        drawCircle(
            color = levelColor.copy(alpha = animatedAlpha),
            radius = animatedRadius,
            style = Stroke(width = 6.dp.toPx()),
        )
    }
}

private const val WAVEFORM_HISTORY_SIZE = 90 // ~3s at a representative ~30 updates/s (§6.2).

@Composable
private fun WaveformLevelIndicator(level: Float, modifier: Modifier = Modifier) {
    val history = remember { mutableStateListOf<Float>() }
    history.add(level.coerceIn(0f, 1f))
    while (history.size > WAVEFORM_HISTORY_SIZE) history.removeAt(0)

    val waveColor = MaterialTheme.colors.secondary

    Canvas(modifier = modifier.size(width = 320.dp, height = 120.dp).testTag(TestTags.Task.LEVEL_INDICATOR)) {
        if (history.isEmpty()) return@Canvas
        val midY = size.height / 2f
        val stepX = size.width / WAVEFORM_HISTORY_SIZE
        val startIndex = WAVEFORM_HISTORY_SIZE - history.size
        history.forEachIndexed { i, value ->
            val x = (startIndex + i) * stepX
            val barHeight = value * midY
            drawLine(
                color = waveColor,
                start = Offset(x, midY - barHeight),
                end = Offset(x, midY + barHeight),
                strokeWidth = stepX * 0.6f,
            )
        }
    }
}
