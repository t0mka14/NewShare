package org.example.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
 * `ContinuousSessionRecorder.levels` via `TaskComponent.Content.Vocal.level`. [CIRCLE] is the
 * legacy `drawRecCircle` (§13 decision 36): a static gray dot with a green spring-animated
 * stroke circle that swells with the input level only while [capturing] — at rest it sits at
 * the minimum radius like the legacy did outside the STOP state. [WAVEFORM] has no legacy
 * counterpart (the legacy waveform view showed the finished take, not a live signal) and keeps
 * its rolling history buffer — local Compose display state, never read back by any component.
 * Uses the legacy Material3 palette from `ShareLegacyM3Theme` (task screen wraps in it).
 */
@Composable
fun TaskLevelIndicator(
    indicatorType: IndicatorType,
    level: Float,
    capturing: Boolean = true,
    modifier: Modifier = Modifier,
) {
    when (indicatorType) {
        IndicatorType.CIRCLE -> CircleLevelIndicator(level, capturing, modifier)
        IndicatorType.WAVEFORM -> WaveformLevelIndicator(level, modifier)
    }
}

@Composable
private fun CircleLevelIndicator(level: Float, capturing: Boolean, modifier: Modifier = Modifier) {
    val circleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val circleVolumeColor = MaterialTheme.colorScheme.tertiary
    // Legacy radius band (dp): 35 at rest / silence, up to 60 at full level
    val minRadius = 35f
    val maxRadius = 60f
    val targetRadius = if (capturing) minRadius + level.coerceIn(0f, 1f) * (maxRadius - minRadius) else minRadius
    val anim: Float by animateFloatAsState(
        targetValue = targetRadius,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow,
        ),
    )
    // Bounded size instead of the legacy fillMaxSize(0.5f) canvas (scaling fix, §13/36);
    // 160dp comfortably fits the 60dp max radius + stroke
    Canvas(modifier = modifier.size(160.dp).testTag(TestTags.Task.LEVEL_INDICATOR)) {
        drawCircle(
            color = circleColor,
            radius = 30.dp.toPx(),
        )
        drawCircle(
            color = circleVolumeColor,
            radius = anim.dp.toPx(),
            style = Stroke(width = 5.dp.toPx()),
        )
    }
}

private const val WAVEFORM_HISTORY_SIZE = 90 // ~3s at a representative ~30 updates/s (§6.2).

@Composable
private fun WaveformLevelIndicator(level: Float, modifier: Modifier = Modifier) {
    val history = remember { mutableStateListOf<Float>() }
    history.add(level.coerceIn(0f, 1f))
    while (history.size > WAVEFORM_HISTORY_SIZE) history.removeAt(0)

    val waveColor = MaterialTheme.colorScheme.tertiary

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
