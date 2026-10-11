package org.example.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.example.app.domain.config.IndicatorType
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter

/**
 * Live recording feedback on VOCAL task screens (§6.2), fed by
 * `ContinuousSessionRecorder.fastLevels` via `TaskComponent.Content.Vocal.level` — the
 * barely-smoothed per-chunk level, not the 300 ms meter value the calibration bar reads
 * (both indicators shape the signal themselves; window in `LevelMeter.FAST_WINDOW_MS`). [CIRCLE] is the
 * legacy `drawRecCircle` (§13 decision 36): a static gray dot with a green spring-animated
 * stroke circle that swells with the input level only while [capturing] — at rest it sits at
 * the minimum radius like the legacy did outside the STOP state. [WAVEFORM] has no legacy
 * counterpart (the legacy waveform view showed the finished take, not a live signal) and renders
 * **only while [capturing]** — a trace scrolling in `Idle`/`Stopped` would show room noise and
 * suggest the app is recording when no take is open. Leaving the composition also drops its
 * rolling history buffer (local Compose display state, never read back by any component), so
 * each take starts from an empty trace instead of continuing the previous one.
 * Takes its colors from the app-wide `ShareTheme` palette (`tertiary` is the legacy green).
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
        IndicatorType.WAVEFORM -> if (capturing) WaveformLevelIndicator(level, modifier)
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

private const val WAVEFORM_HISTORY_SIZE = 120 // ~3s at a representative ~30 updates/s (§6.2).

@Composable
private fun WaveformLevelIndicator(level: Float, modifier: Modifier = Modifier) {
    val history = remember { mutableStateListOf<Float>() }
    history.add(level.coerceIn(0f, 1f))
    while (history.size > WAVEFORM_HISTORY_SIZE) history.removeAt(0)

    val waveColor = MaterialTheme.colorScheme.tertiary
    // Same outline treatment as the calibration level bar, so the trace reads as a bounded
    // instrument rather than free-floating strokes.
    val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = modifier
            .size(width = 500.dp, height = 250.dp)
            .border(width = 1.dp, color = outlineColor)
            .padding(all = 4.dp) // keeps peaks off the outline
            .testTag(TestTags.Task.LEVEL_INDICATOR),
    ) {
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

/** Room reserved around a glowing preview so the halo is never clipped and the video never moves. */
private val GLOW_MARGIN = 40.dp

/**
 * The level indicator of a filming VOCAL task (§13 decision 57): a soft green halo behind
 * [content] (the camera preview) whose spread and brightness follow [level] while [capturing],
 * spring-smoothed like [CircleLevelIndicator]. At rest there is no halo, as the circle rests at
 * its minimum. Drawn as a Skia-blurred rounded rect inside a fixed [GLOW_MARGIN], so the
 * preview keeps its full framing size — the reason decision 23 gave it the middle area.
 */
@Composable
fun LevelGlow(level: Float, capturing: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glowColor = MaterialTheme.colorScheme.tertiary
    val target = if (capturing) level.coerceIn(0f, 1f) else 0f
    val intensity: Float by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow,
        ),
    )
    Box(
        modifier = modifier
            .testTag(TestTags.Task.LEVEL_GLOW)
            .drawBehind {
                if (intensity <= 0.01f) return@drawBehind
                // Louder = the halo reaches further out (2..16dp past the preview), blurs wider
                // (4..14dp sigma) and is more opaque (0.45..0.95).
                val spread = (2f + intensity * 14f).dp.toPx()
                val sigma = (4f + intensity * 10f).dp.toPx()
                val inset = GLOW_MARGIN.toPx() - spread
                val paint = Paint().apply { color = glowColor.copy(alpha = 0.45f + intensity * 0.5f) }
                paint.asFrameworkPaint().maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRRect(
                        org.jetbrains.skia.RRect.makeLTRB(
                            inset, inset, size.width - inset, size.height - inset, (8.dp + 4.dp * intensity).toPx(),
                        ),
                        paint.asFrameworkPaint(),
                    )
                }
            }
            .padding(GLOW_MARGIN),
    ) {
        content()
    }
}
