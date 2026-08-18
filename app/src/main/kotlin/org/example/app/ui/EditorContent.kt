package org.example.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.domain.audio.WaveformPeaks
import org.example.app.navigation.EditorComponent
import java.awt.Cursor
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * §8.7 waveform editor. One segment (last take of one VOCAL instance) is shown at a time; the
 * ±5s visible-context window, the boundary positions and the position line all come from
 * [EditorComponent.State] — this composable only renders them and forwards drag/tap/playback/
 * navigation events, per §5.2 ("UI files contain no logic"). The pixel↔sample mapping is the one
 * piece of arithmetic that has to live here, since only the layout knows the canvas width.
 *
 * Layout: title block, then a panel that takes **all** remaining height (a waveform is only as
 * useful as it is large), then a single row of controls — play included, next to the navigation
 * and accept buttons.
 */
@Composable
fun EditorContent(component: EditorComponent, localization: UiLocalization) {
    val state by component.state.subscribeAsState()
    val segment = state.currentSegment

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(localization.resolve("editor.title"), style = MaterialTheme.typography.headlineLarge)
            // The instructions describe dragging boundaries, so they only belong on a screen that
            // has boundaries to drag.
            if (segment != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    localization.resolve("editor.instructions"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (segment == null) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(localization.resolve("editor.noSegments"), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    AcceptButton(localization, component::onAccept)
                }
            }
        } else {
            Text(
                localization.resolve(
                    "editor.segmentOfTotal",
                    mapOf("n" to (state.currentIndex + 1).toString(), "total" to state.segments.size.toString()),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(TestTags.Editor.SEGMENT_LABEL),
            )

            EditorPanel(
                modifier = Modifier.weight(1f),
                localization = localization,
                waveform = state.waveform,
                visibleStart = state.visibleStartSample,
                visibleStop = state.visibleStopSample,
                startSample = segment.startSample,
                stopSample = segment.stopSample,
                positionSample = state.positionSample,
                sampleRate = state.sampleRate,
                onDragStart = component::onDragStart,
                onDragStop = component::onDragStop,
                onSeek = component::onSeek,
            )

            EditorControls(
                localization = localization,
                isPlaying = state.isPlaying,
                hasPrevious = state.currentIndex > 0,
                hasNext = state.currentIndex < state.segments.lastIndex,
                onPrevious = component::onPrevious,
                onPlayToggle = component::onPlayToggle,
                onNext = component::onNext,
                onAccept = component::onAccept,
            )
        }
    }
}

/** Editor width cap, shared by the panel and the control row so both edges line up. */
private val EDITOR_MAX_WIDTH = 1600.dp
private val HANDLE_HIT_WIDTH = 24.dp
private const val RULER_HEIGHT_DP = 26
private const val WAVEFORM_MIN_HEIGHT_DP = 220

/**
 * The instrument itself: time ruler on top, waveform below it, readouts underneath. Ruler and
 * waveform share one [BoxWithConstraints] so both map samples onto exactly the same pixel range.
 */
@Composable
private fun EditorPanel(
    modifier: Modifier,
    localization: UiLocalization,
    waveform: WaveformPeaks?,
    visibleStart: Long,
    visibleStop: Long,
    startSample: Long,
    stopSample: Long,
    positionSample: Long?,
    sampleRate: Int,
    onDragStart: (Long) -> Unit,
    onDragStop: (Long) -> Unit,
    onSeek: (Long) -> Unit,
) {
    Surface(
        modifier = modifier.contentWidth(EDITOR_MAX_WIDTH).heightIn(min = (WAVEFORM_MIN_HEIGHT_DP + 90).dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = WAVEFORM_MIN_HEIGHT_DP.dp),
            ) {
                val range = (visibleStop - visibleStart).coerceAtLeast(1L)
                val widthPx = constraints.maxWidth.toFloat()

                fun sampleToX(sample: Long): Float = ((sample - visibleStart).toFloat() / range) * widthPx
                fun xToSample(x: Float): Long = visibleStart + ((x / widthPx) * range).toLong()

                Column(modifier = Modifier.fillMaxSize()) {
                    TimeRuler(
                        visibleStart = visibleStart,
                        visibleStop = visibleStop,
                        sampleRate = sampleRate,
                        widthPx = widthPx,
                        sampleToX = ::sampleToX,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            // A lit lane inside the frame: the peaks read as an instrument's
                            // display rather than as ink on the panel.
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag(TestTags.Editor.WAVEFORM_CANVAS),
                    ) {
                        WaveformCanvas(
                            waveform = waveform,
                            startX = sampleToX(startSample),
                            stopX = sampleToX(stopSample),
                            positionX = positionSample?.let(::sampleToX),
                            onSeekToPixelX = { x -> onSeek(xToSample(x)) },
                        )
                        EditorBoundaryHandle(
                            xPx = sampleToX(startSample),
                            testTag = TestTags.Editor.START_BOUNDARY_HANDLE,
                            onDragToPixelX = { newXPx -> onDragStart(xToSample(newXPx)) },
                        )
                        EditorBoundaryHandle(
                            xPx = sampleToX(stopSample),
                            testTag = TestTags.Editor.STOP_BOUNDARY_HANDLE,
                            onDragToPixelX = { newXPx -> onDragStop(xToSample(newXPx)) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            EditorReadouts(
                localization = localization,
                startSample = startSample,
                stopSample = stopSample,
                positionSample = positionSample,
                sampleRate = sampleRate,
            )
        }
    }
}

/**
 * Peaks and the two boundaries. Buckets are drawn **centered** on `(i + 0.5) * step`: `drawLine`
 * centers its stroke on the given x, so drawing at `i * step` would put the whole waveform half a
 * bucket left of where the boundaries and the position line are placed by `sampleToX` — small,
 * but visible exactly where it matters, under the position line.
 */
@Composable
private fun WaveformCanvas(
    waveform: WaveformPeaks?,
    startX: Float,
    stopX: Float,
    positionX: Float?,
    onSeekToPixelX: (Float) -> Unit,
) {
    val insideColor = MaterialTheme.colorScheme.primary
    val outsideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val selectionFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant
    val boundaryColor = MaterialTheme.colorScheme.secondary
    val playheadColor = MaterialTheme.colorScheme.error

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerHoverIcon(PointerIcon.Crosshair)
            .pointerInput(Unit) { detectTapGestures { offset -> onSeekToPixelX(offset.x) } },
    ) {
        val midY = size.height / 2f
        // Peaks stop short of the frame so a full-scale sample still reads as a peak and not as
        // a bar clipped by the panel edge.
        val amplitude = midY * 0.94f

        drawRect(
            color = selectionFill,
            topLeft = Offset(startX, 0f),
            size = Size((stopX - startX).coerceAtLeast(0f), size.height),
        )
        drawLine(zeroLineColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1f)

        waveform?.let { peaks ->
            val step = size.width / peaks.bucketCount
            val barWidth = (step * 0.85f).coerceAtLeast(1f)
            val insideBrush = Brush.verticalGradient(
                colors = listOf(insideColor, insideColor.copy(alpha = 0.75f), insideColor),
                startY = 0f,
                endY = size.height,
            )
            for (i in 0 until peaks.bucketCount) {
                val x = (i + 0.5f) * step
                val top = midY - peaks.max[i].coerceIn(-1f, 1f) * amplitude
                val bottom = midY - peaks.min[i].coerceIn(-1f, 1f) * amplitude
                if (x in startX..stopX) {
                    drawLine(
                        brush = insideBrush,
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = barWidth,
                    )
                } else {
                    drawLine(
                        color = outsideColor,
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = barWidth,
                    )
                }
            }
        }

        drawLine(boundaryColor, Offset(startX, 0f), Offset(startX, size.height), strokeWidth = 2.dp.toPx())
        drawLine(boundaryColor, Offset(stopX, 0f), Offset(stopX, size.height), strokeWidth = 2.dp.toPx())

        positionX?.let { x ->
            drawLine(playheadColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            val head = 6.dp.toPx()
            drawPath(
                path = Path().apply {
                    moveTo(x - head, 0f)
                    lineTo(x + head, 0f)
                    lineTo(x, head * 1.6f)
                    close()
                },
                color = playheadColor,
            )
        }
    }
}

/**
 * Second marks over the visible window. The step adapts to the zoom so labels never crowd:
 * the smallest of the candidate steps that still leaves ~90px between ticks wins.
 */
@Composable
private fun TimeRuler(
    visibleStart: Long,
    visibleStop: Long,
    sampleRate: Int,
    widthPx: Float,
    sampleToX: (Long) -> Float,
) {
    val textMeasurer = rememberTextMeasurer()
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.bodySmall.copy(
        color = tickColor,
        fontWeight = FontWeight.Medium,
    )

    Canvas(modifier = Modifier.fillMaxWidth().height(RULER_HEIGHT_DP.dp)) {
        if (sampleRate <= 0 || widthPx <= 0f || visibleStop <= visibleStart) return@Canvas
        val visibleSeconds = (visibleStop - visibleStart).toDouble() / sampleRate
        val stepSeconds = TICK_STEPS_SECONDS.firstOrNull { widthPx * (it / visibleSeconds) >= MIN_TICK_SPACING_PX }
            ?: TICK_STEPS_SECONDS.last()

        val startSeconds = visibleStart.toDouble() / sampleRate
        val stopSeconds = visibleStop.toDouble() / sampleRate

        // Unlabelled halfway ticks: they give the eye a finer scale without a second row of text.
        val minorStep = stepSeconds / 2
        var minor = ceil(startSeconds / minorStep) * minorStep
        while (minor <= stopSeconds) {
            val x = sampleToX((minor * sampleRate).toLong())
            drawLine(
                color = tickColor.copy(alpha = 0.35f),
                start = Offset(x, size.height - MINOR_TICK_LENGTH_PX),
                end = Offset(x, size.height),
                strokeWidth = 1f,
            )
            minor += minorStep
        }

        var tick = ceil(startSeconds / stepSeconds) * stepSeconds
        while (tick <= stopSeconds) {
            val x = sampleToX((tick * sampleRate).toLong())
            drawLine(
                color = tickColor,
                start = Offset(x, size.height - TICK_LENGTH_PX),
                end = Offset(x, size.height),
                strokeWidth = 1.5f,
            )
            val label = if (stepSeconds < 1.0) formatSeconds(tick) else formatClock(tick)
            val measured = textMeasurer.measure(AnnotatedString(label), labelStyle)
            val labelX = (x - measured.size.width / 2f).coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
            drawText(measured, topLeft = Offset(labelX, 0f))
            tick += stepSeconds
        }
    }
}

private val TICK_STEPS_SECONDS = listOf(0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0, 60.0)
private const val MIN_TICK_SPACING_PX = 90f
private const val TICK_LENGTH_PX = 8f
private const val MINOR_TICK_LENGTH_PX = 4f

/** Trimmed range on the left, position line on the right — both in mm:ss.t. */
@Composable
private fun EditorReadouts(
    localization: UiLocalization,
    startSample: Long,
    stopSample: Long,
    positionSample: Long?,
    sampleRate: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Readout(localization.resolvePlain("editor.startLabel"), formatSampleTime(startSample, sampleRate))
            Readout(localization.resolvePlain("editor.stopLabel"), formatSampleTime(stopSample, sampleRate))
            Readout(
                localization.resolvePlain("editor.durationLabel"),
                formatSampleDuration((stopSample - startSample).coerceAtLeast(0L), sampleRate),
                emphasized = true,
            )
        }
        Readout(
            localization.resolvePlain("editor.positionLabel"),
            formatSampleTime(positionSample ?: startSample, sampleRate),
        )
    }
}

@Composable
private fun Readout(label: String, value: String, emphasized: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Everything actionable in one row (previous · play/stop · next · accept). */
@Composable
private fun EditorControls(
    localization: UiLocalization,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onPlayToggle: () -> Unit,
    onNext: () -> Unit,
    onAccept: () -> Unit,
) {
    Row(
        modifier = Modifier.contentWidth(EDITOR_MAX_WIDTH),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPrevious,
            enabled = hasPrevious,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.testTag(TestTags.Editor.PREVIOUS_SEGMENT_BUTTON),
        ) {
            ButtonIcon(Icons.Filled.ChevronLeft)
            Text(localization.resolve("action.previous"))
        }

        Button(
            onClick = onPlayToggle,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.testTag(TestTags.Editor.PLAY_SEGMENT_BUTTON),
        ) {
            ButtonIcon(if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow)
            Text(localization.resolve(if (isPlaying) "action.stop" else "action.play"))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onNext,
                enabled = hasNext,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.testTag(TestTags.Editor.NEXT_SEGMENT_BUTTON),
            ) {
                Text(localization.resolve("action.next"))
                ButtonIcon(Icons.Filled.ChevronRight, trailing = true)
            }
            AcceptButton(localization, onAccept)
        }
    }
}

@Composable
private fun AcceptButton(localization: UiLocalization, onAccept: () -> Unit) {
    Button(
        onClick = onAccept,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.testTag(TestTags.Editor.ACCEPT_BUTTON),
    ) {
        ButtonIcon(Icons.Filled.Check)
        Text(localization.resolve("action.accept"))
    }
}

@Composable
private fun ButtonIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, trailing: Boolean = false) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .padding(start = if (trailing) 6.dp else 0.dp, end = if (trailing) 0.dp else 6.dp)
            .height(20.dp),
    )
}

/**
 * A draggable boundary line handle (§8.7, `performMouseInput` press-drag-release, §10.3). The
 * gesture's pixel baseline is captured fresh at the start of *every* drag (via
 * [rememberUpdatedState] reading the always-current [xPx]) and only accumulated pixel deltas are
 * added during the drag itself — this avoids feeding the *result* of an accepted/rejected drag
 * (which recomposes [xPx]) back into the same gesture's math while it is still in progress.
 *
 * The hit area is deliberately wider than the drawn grip: the boundary line itself is painted by
 * the canvas underneath, this only adds the grab target, its grip caps and the resize cursor.
 */
@Composable
private fun EditorBoundaryHandle(xPx: Float, testTag: String, onDragToPixelX: (Float) -> Unit) {
    val latestXPx = rememberUpdatedState(xPx)
    val latestOnDrag = rememberUpdatedState(onDragToPixelX)
    var gestureBaseXPx by remember { mutableStateOf(0f) }
    var accumulatedPx by remember { mutableStateOf(0f) }
    val gripColor = MaterialTheme.colorScheme.secondary
    val gripMarkColor = MaterialTheme.colorScheme.onSecondary

    Box(
        modifier = Modifier
            .offset { IntOffset((xPx - HANDLE_HIT_WIDTH.toPx() / 2).roundToInt(), 0) }
            .width(HANDLE_HIT_WIDTH)
            .fillMaxHeight()
            .pointerHoverIcon(HORIZONTAL_RESIZE_CURSOR)
            .testTag(testTag)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        gestureBaseXPx = latestXPx.value
                        accumulatedPx = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedPx += dragAmount.x
                        latestOnDrag.value(gestureBaseXPx + accumulatedPx)
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gripWidth = 10.dp.toPx()
            val gripHeight = 26.dp.toPx()
            val left = (size.width - gripWidth) / 2f
            listOf(0f, size.height - gripHeight).forEach { top ->
                drawRoundRect(
                    color = gripColor,
                    topLeft = Offset(left, top),
                    size = Size(gripWidth, gripHeight),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
                val markInset = 3.dp.toPx()
                listOf(-1.5f, 1.5f).forEach { dx ->
                    val x = size.width / 2f + dx.dp.toPx()
                    drawLine(
                        color = gripMarkColor,
                        start = Offset(x, top + markInset),
                        end = Offset(x, top + gripHeight - markInset),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** Desktop-only east-west resize cursor — the standard affordance for a draggable edge. */
private val HORIZONTAL_RESIZE_CURSOR = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))

/** `m:ss.t`, the resolution an examiner trims at — for positions on the recording's clock. */
private fun formatSampleTime(sample: Long, sampleRate: Int): String {
    if (sampleRate <= 0) return "–:––.–"
    return formatSeconds(sample.toDouble() / sampleRate)
}

/** Lengths are read as a count of seconds, not as a clock time (`7.4 s`, not `0:07.4`). */
private fun formatSampleDuration(samples: Long, sampleRate: Int): String {
    if (sampleRate <= 0) return "– s"
    val seconds = samples.toDouble() / sampleRate
    if (seconds >= 60) return "${formatClock(seconds)} min"
    val tenths = (seconds * 10).toLong().coerceAtLeast(0L)
    return "${tenths / 10}.${tenths % 10} s"
}

private fun formatSeconds(seconds: Double): String {
    val totalTenths = (seconds * 10).toLong().coerceAtLeast(0L)
    val minutes = totalTenths / 600
    val secs = (totalTenths / 10) % 60
    val tenths = totalTenths % 10
    return "$minutes:${secs.toString().padStart(2, '0')}.$tenths"
}

/** `m:ss` for ruler labels — tenths would only add noise at second-scale tick steps. */
private fun formatClock(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
