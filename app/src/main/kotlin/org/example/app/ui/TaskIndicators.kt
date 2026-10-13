package org.example.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
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
 * `ContinuousSessionRecorder.fastLevels` via `TaskComponent.Content.Vocal.level` — the
 * barely-smoothed per-chunk level, not the 300 ms meter value the calibration bar reads
 * (both indicators shape the signal themselves; window in `LevelMeter.FAST_WINDOW_MS`). [CIRCLE] is the
 * legacy `drawRecCircle` (§13 decision 36): a static gray dot with a green spring-animated
 * stroke circle that swells with the input level only while [capturing] — at rest it sits at
 * the minimum radius like the legacy did outside the STOP state. [WAVEFORM] draws the take's
 * level history as one growing trace (§13 decision 61): it fills the canvas left to right, then
 * compresses as the take goes on, and stops growing at Stop — the finished trace stays on screen
 * unchanged, like the legacy post-take waveform. After Stop the trace replaces the circle too.
 * Nothing is drawn before a take's first sample, so `Idle` shows no room noise. The history is
 * local Compose display state, never read back by any component; a new [takeKey] (the take
 * number) starts it empty.
 * Takes its colors from the app-wide `ShareTheme` palette (`tertiary` is the legacy green).
 */
@Composable
fun TaskLevelIndicator(
    indicatorType: IndicatorType,
    level: Float,
    capturing: Boolean = true,
    modifier: Modifier = Modifier,
    takeKey: Any? = null,
) {
    // Hoisted here so the trace outlives Stop; only an open take adds to it.
    val history = remember(takeKey) { mutableStateListOf<Float>() }
    if (capturing) history.add(level.coerceIn(0f, 1f))
    when {
        indicatorType == IndicatorType.CIRCLE && capturing -> CircleLevelIndicator(level, true, modifier)
        history.isNotEmpty() -> WaveformLevelIndicator(history, modifier)
        indicatorType == IndicatorType.CIRCLE -> CircleLevelIndicator(level, false, modifier)
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

// Samples the canvas holds before the trace starts compressing: ~3s at ~30 updates/s (§6.2).
private const val WAVEFORM_MIN_SAMPLES = 120

/** Level step between the ticks on the waveform's left edge (0.2 → 0.2 … 0.8 each side; the outline marks 1.0). */
private const val WAVEFORM_TICK_STEP = 0.2f
private val WAVEFORM_PADDING = 4.dp

@Composable
private fun WaveformLevelIndicator(history: List<Float>, modifier: Modifier = Modifier) {
    val waveColor = MaterialTheme.colorScheme.tertiary
    // Same outline treatment as the calibration level bar, so the trace reads as a bounded
    // instrument rather than free-floating strokes.
    val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = modifier
            .size(width = 500.dp, height = 250.dp)
            .border(width = 1.dp, color = outlineColor)
            .padding(all = WAVEFORM_PADDING) // keeps peaks off the outline
            .testTag(TestTags.Task.LEVEL_INDICATOR),
    ) {
        val midY = size.height / 2f
        // Scale behind the trace: the zero line, and level ticks running from the outline
        // (one padding left of the drawing area) to as far past it, mirrored above and below.
        val axisStroke = 1.dp.toPx()
        drawLine(outlineColor, Offset(0f, midY), Offset(size.width, midY), axisStroke)
        val pad = WAVEFORM_PADDING.toPx()
        var tick = WAVEFORM_TICK_STEP
        while (tick < 1f - 1e-3f) {
            for (y in floatArrayOf(midY - tick * midY, midY + tick * midY)) {
                drawLine(outlineColor, Offset(-pad, y), Offset(pad, y), axisStroke)
            }
            tick += WAVEFORM_TICK_STEP
        }

        if (history.isEmpty()) return@Canvas
        val stepX = size.width / maxOf(history.size, WAVEFORM_MIN_SAMPLES)
        history.forEachIndexed { i, value ->
            val x = i * stepX
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
