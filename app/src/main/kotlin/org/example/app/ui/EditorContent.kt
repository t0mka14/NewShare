package org.example.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.example.app.domain.audio.WaveformPeaks
import org.example.app.navigation.EditorComponent

/**
 * §8.7 waveform editor. One segment (last take of one VOCAL instance) is shown at a time; the
 * ±5s visible-context window and initial boundary positions come entirely from
 * [EditorComponent.State] — this composable only renders it and forwards drag/playback/
 * navigation events, per §5.2 ("UI files contain no logic").
 */
@Composable
fun EditorContent(component: EditorComponent, localization: UiLocalization) {
    val state by component.state.subscribeAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(localization.resolve("editor.title"), style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(localization.resolve("editor.instructions"), style = MaterialTheme.typography.bodyMedium)
        }

        val segment = state.currentSegment
        if (state.loading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(localization.resolve("editor.title"))
            }
        } else if (segment == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(localization.resolve("editor.noSegments"))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = component::onAccept, modifier = Modifier.testTag(TestTags.Editor.ACCEPT_BUTTON)) {
                    Text(localization.resolve("action.accept"))
                }
            }
        } else {
            Text(
                localization.resolve(
                    "editor.segmentOfTotal",
                    mapOf("n" to (state.currentIndex + 1).toString(), "total" to state.segments.size.toString()),
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(TestTags.Editor.SEGMENT_LABEL),
            )

            EditorWaveform(
                waveform = state.waveform,
                visibleStart = state.visibleStartSample,
                visibleStop = state.visibleStopSample,
                startSample = segment.startSample,
                stopSample = segment.stopSample,
                positionSample = state.positionSample.takeIf { state.isPlaying },
                onDragStart = component::onDragStart,
                onDragStop = component::onDragStop,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = component::onPlayToggle, modifier = Modifier.testTag(TestTags.Editor.PLAY_SEGMENT_BUTTON)) {
                Text(localization.resolve(if (state.isPlaying) "action.stop" else "action.play"))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = component::onPrevious,
                    enabled = state.currentIndex > 0,
                    modifier = Modifier.testTag(TestTags.Editor.PREVIOUS_SEGMENT_BUTTON),
                ) {
                    Text(localization.resolve("action.previous"))
                }
                Button(
                    onClick = component::onNext,
                    enabled = state.currentIndex < state.segments.lastIndex,
                    modifier = Modifier.testTag(TestTags.Editor.NEXT_SEGMENT_BUTTON),
                ) {
                    Text(localization.resolve("action.next"))
                }
                Button(onClick = component::onAccept, modifier = Modifier.testTag(TestTags.Editor.ACCEPT_BUTTON)) {
                    Text(localization.resolve("action.accept"))
                }
            }
        }
    }
}

private const val HANDLE_WIDTH_DP = 14

@Composable
private fun EditorWaveform(
    waveform: WaveformPeaks?,
    visibleStart: Long,
    visibleStop: Long,
    startSample: Long,
    stopSample: Long,
    positionSample: Long?,
    onDragStart: (Long) -> Unit,
    onDragStop: (Long) -> Unit,
) {
    val range = (visibleStop - visibleStart).coerceAtLeast(1L)

    BoxWithConstraints(
        modifier = Modifier
            .contentWidth(1600.dp)
            .height(220.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
            .testTag(TestTags.Editor.WAVEFORM_CANVAS),
    ) {
        val widthPx = constraints.maxWidth.toFloat()

        fun sampleToX(sample: Long): Float = ((sample - visibleStart).toFloat() / range) * widthPx
        fun xToSample(x: Float): Long = visibleStart + ((x / widthPx) * range).toLong()

        // MaterialTheme.colors is a @Composable reader — resolved here, outside the DrawScope
        // lambda below (which is plain, non-composable), then captured by closure.
        val waveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        val boundaryColor = MaterialTheme.colorScheme.secondary
        val boundaryFillColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val midY = size.height / 2f
            waveform?.let { peaks ->
                val step = size.width / peaks.bucketCount
                for (i in 0 until peaks.bucketCount) {
                    val x = i * step
                    val top = midY - peaks.max[i].coerceIn(-1f, 1f) * midY
                    val bottom = midY - peaks.min[i].coerceIn(-1f, 1f) * midY
                    drawLine(
                        color = waveColor,
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = step.coerceAtLeast(1f),
                    )
                }
            }

            val startX = sampleToX(startSample)
            val stopX = sampleToX(stopSample)
            drawRect(
                color = boundaryFillColor,
                topLeft = Offset(startX, 0f),
                size = Size(stopX - startX, size.height),
            )
            drawLine(boundaryColor, Offset(startX, 0f), Offset(startX, size.height), strokeWidth = 3f)
            drawLine(boundaryColor, Offset(stopX, 0f), Offset(stopX, size.height), strokeWidth = 3f)

            positionSample?.let { pos ->
                val x = sampleToX(pos)
                drawLine(Color.Red, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
            }
        }

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

/**
 * A draggable boundary line handle (§8.7, `performMouseInput` press-drag-release, §10.3). The
 * gesture's pixel baseline is captured fresh at the start of *every* drag (via
 * [rememberUpdatedState] reading the always-current [xPx]) and only accumulated pixel deltas are
 * added during the drag itself — this avoids feeding the *result* of an accepted/rejected drag
 * (which recomposes [xPx]) back into the same gesture's math while it is still in progress.
 */
@Composable
private fun EditorBoundaryHandle(xPx: Float, testTag: String, onDragToPixelX: (Float) -> Unit) {
    val latestXPx = rememberUpdatedState(xPx)
    val latestOnDrag = rememberUpdatedState(onDragToPixelX)
    var gestureBaseXPx by remember { mutableStateOf(0f) }
    var accumulatedPx by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .offset { IntOffset((xPx - HANDLE_WIDTH_DP.dp.toPx() / 2).toInt(), 0) }
            .width(HANDLE_WIDTH_DP.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
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
    )
}
