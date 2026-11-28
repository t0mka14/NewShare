package org.example.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.buildAnnotatedString
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorContent(component: EditorComponent, localization: UiLocalization) {
    val state by component.state.subscribeAsState()
    val segment = state.currentSegment
    val enhanced = state.enhanced
    val latestState = rememberUpdatedState(state)
    val focusRequester = remember { FocusRequester() }
    if (enhanced) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Delete / Esc only exist in the enhanced editor; the default screen gets no extra modifiers.
    val keyModifier = if (!enhanced) Modifier else Modifier
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || latestState.value.selection == null) return@onPreviewKeyEvent false
            when (event.key) {
                Key.Delete -> { component.onCutSelection(); true }
                Key.Escape -> { component.onClearSelection(); true }
                else -> false
            }
        }

    Column(
        modifier = Modifier.fillMaxSize().then(keyModifier).padding(horizontal = 32.dp, vertical = 24.dp),
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
                    localization.resolve(if (enhanced) "editor.instructions.enhanced" else "editor.instructions"),
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DiscardButton(localization, component::onDiscard)
                        AcceptButton(localization, component::onAccept)
                    }
                }
            }
        } else {
            Text(
                buildAnnotatedString {
                    append(
                        localization.resolve(
                            "editor.segmentOfTotal",
                            mapOf("n" to (state.currentIndex + 1).toString(), "total" to state.segments.size.toString()),
                        ),
                    )
                    if (segment.subtype.isNotBlank()) append(" - ${segment.subtype}")
                    // F-025: tell repetitions of one task apart; a one-repetition task needs no suffix.
                    if (segment.repetitionCount > 1) {
                        append(localization.resolve("editor.segmentRepetition", mapOf("r" to segment.repetition.toString())))
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(TestTags.Editor.SEGMENT_LABEL),
            )

            EditorPanel(
                modifier = Modifier.weight(1f),
                localization = localization,
                state = state,
                segment = segment,
                component = component,
            )

            // Enhanced mode reserves this line inside the panel so the waveform never changes height.
            if (segment.videoNotCut && !enhanced) VideoNotCutNote(localization, Modifier.contentWidth(EDITOR_MAX_WIDTH))

            EditorControls(
                localization = localization,
                isPlaying = state.isPlaying,
                hasPrevious = state.currentIndex > 0,
                hasNext = state.currentIndex < state.segments.lastIndex,
                onPrevious = component::onPrevious,
                onPlayToggle = component::onPlayToggle,
                onNext = component::onNext,
                enhanced = enhanced,
                hasSelection = state.selection != null,
                onPlaySelection = component::onPlaySelection,
                onCutSelection = component::onCutSelection,
                onClearSelection = component::onClearSelection,
                canReset = segment.changedSinceOpened,
                onReset = component::onResetSegment,
                onDiscard = component::onDiscard,
                onAccept = component::onAccept,
            )
        }
    }
    if (state.discardConfirmationOpen) DiscardConfirmDialog(component, localization)
}

/** The "Discard edits?" confirmation, inside the main window like every dialog. */
@Composable
private fun DiscardConfirmDialog(component: EditorComponent, localization: UiLocalization) {
    AlertDialog(
        onDismissRequest = component::onDiscardCancelled,
        modifier = Modifier.testTag(TestTags.Editor.DISCARD_DIALOG),
        title = { Text(localization.resolve("editor.discardConfirm.title")) },
        text = { Text(localization.resolve("editor.discardConfirm.message")) },
        confirmButton = {
            Button(onClick = component::onDiscardConfirmed, modifier = Modifier.testTag(TestTags.Editor.DISCARD_CONFIRM_BUTTON)) {
                Text(localization.resolve("editor.discardConfirm.confirm"))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = component::onDiscardCancelled, modifier = Modifier.testTag(TestTags.Editor.DISCARD_CANCEL_BUTTON)) {
                Text(localization.resolve("action.cancel"))
            }
        },
    )
}

/** Editor width cap, shared by the panel and the control row so both edges line up. */
private val EDITOR_MAX_WIDTH = 1600.dp
private val HANDLE_HIT_WIDTH = 24.dp
private val NOTE_LINE_HEIGHT = 26.dp
private const val RULER_HEIGHT_DP = 26
private const val WAVEFORM_MIN_HEIGHT_DP = 150

@Composable
private fun VideoNotCutNote(localization: UiLocalization, modifier: Modifier) {
    Row(
        modifier = modifier.testTag(TestTags.Editor.VIDEO_NOT_CUT_NOTE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            localization.resolve("editor.videoNotCut"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The instrument itself: time ruler on top, waveform below it, readouts underneath. Ruler and
 * waveform share one [BoxWithConstraints] so both map samples onto exactly the same pixel range.
 * In the enhanced editor a toolbar sits above it and a pan bar under it; everything is drawn with
 * the `visibleStart..visibleStop` mapping, so zooming only changes that window.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EditorPanel(
    modifier: Modifier,
    localization: UiLocalization,
    state: EditorComponent.State,
    segment: EditorComponent.Segment,
    component: EditorComponent,
) {
    val waveform = state.waveform
    val visibleStart = state.visibleStartSample
    val visibleStop = state.visibleStopSample
    val startSample = segment.startSample
    val stopSample = segment.stopSample
    val positionSample = state.positionSample
    val sampleRate = state.sampleRate
    val enhanced = state.enhanced
    val selection = state.selection
    val latestState = rememberUpdatedState(state)

    Surface(
        modifier = modifier.contentWidth(EDITOR_MAX_WIDTH)
            .heightIn(min = (WAVEFORM_MIN_HEIGHT_DP + 90 + if (enhanced) 60 else 0).dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (enhanced) {
                EditorToolbar(localization, state, component)
                // Fixed-height line: the cut-rejection message and the video note come and go
                // here without taking space from the waveform.
                Row(
                    modifier = Modifier.fillMaxWidth().height(NOTE_LINE_HEIGHT).padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (segment.videoNotCut) {
                        VideoNotCutNote(localization, Modifier.weight(1f, fill = false))
                    }
                    state.lastCutRejected?.let { rejection ->
                        Text(
                            localization.resolve(rejectionKey(rejection)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            modifier = Modifier.testTag(TestTags.Editor.CUT_REJECTION_MESSAGE),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = WAVEFORM_MIN_HEIGHT_DP.dp),
            ) {
                val range = (visibleStop - visibleStart).coerceAtLeast(1L)
                val widthPx = constraints.maxWidth.toFloat()

                fun sampleToX(sample: Long): Float = ((sample - visibleStart).toFloat() / range) * widthPx
                fun xToSample(x: Float): Long = visibleStart + ((x / widthPx) * range).toLong()
                fun onScreen(x: Float) = x >= -0.5f && x <= widthPx + 0.5f

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
                            .then(
                                if (!enhanced) Modifier else Modifier.onPointerEvent(PointerEventType.Scroll) { event ->
                                    val change = event.changes.firstOrNull() ?: return@onPointerEvent
                                    val delta = if (change.scrollDelta.y != 0f) change.scrollDelta.y else change.scrollDelta.x
                                    if (delta == 0f) return@onPointerEvent
                                    val current = latestState.value
                                    if (event.keyboardModifiers.isCtrlPressed) {
                                        val anchor = visibleStart + ((change.position.x / widthPx) * range).toLong()
                                        if (delta < 0f) component.onZoomIn(anchor) else component.onZoomOut(anchor)
                                        change.consume()
                                    } else if (current.zoomed) {
                                        val step = (range / 10f * delta).toLong()
                                        component.onPan(current.visibleStartSample + step)
                                        change.consume()
                                    }
                                },
                            )
                            .testTag(TestTags.Editor.WAVEFORM_CANVAS),
                    ) {
                        WaveformCanvas(
                            waveform = waveform,
                            startX = sampleToX(startSample),
                            stopX = sampleToX(stopSample),
                            positionX = positionSample?.let(::sampleToX),
                            onSeekToPixelX = { x -> component.onSeek(xToSample(x)) },
                            onSelectDrag = if (!enhanced) null else { anchorX, currentX ->
                                val anchor = xToSample(anchorX)
                                if (anchor in segment.startSample..segment.stopSample) {
                                    component.onSelect(anchor, xToSample(currentX))
                                }
                            },
                        )
                        if (enhanced) {
                            selection?.let { sel ->
                                SelectionBand(
                                    startX = sampleToX(sel.startSample),
                                    stopX = sampleToX(sel.stopSample),
                                    onDragStartEdge = { x -> component.onSelect(sel.stopSample, xToSample(x)) },
                                    onDragStopEdge = { x -> component.onSelect(sel.startSample, xToSample(x)) },
                                )
                            }
                        }
                        segment.cuts.forEachIndexed { index, cut ->
                            val x0 = sampleToX(cut.startSample)
                            val x1 = sampleToX(cut.stopSample)
                            if (x1 > 0f && x0 < widthPx) {
                                CutBand(
                                    localization = localization,
                                    index = index,
                                    xPx = x0,
                                    widthPx = x1 - x0,
                                    lengthText = formatSampleDuration(cut.length, sampleRate),
                                    onRestore = { component.onRestoreCut(index) },
                                )
                            }
                        }
                        if (onScreen(sampleToX(startSample))) {
                            EditorBoundaryHandle(
                                xPx = sampleToX(startSample),
                                testTag = TestTags.Editor.START_BOUNDARY_HANDLE,
                                onDragToPixelX = { newXPx -> component.onDragStart(xToSample(newXPx)) },
                            )
                        }
                        if (onScreen(sampleToX(stopSample))) {
                            EditorBoundaryHandle(
                                xPx = sampleToX(stopSample),
                                testTag = TestTags.Editor.STOP_BOUNDARY_HANDLE,
                                onDragToPixelX = { newXPx -> component.onDragStop(xToSample(newXPx)) },
                            )
                        }
                    }
                }
            }

            if (enhanced && state.zoomed) {
                Spacer(modifier = Modifier.height(6.dp))
                PanBar(state = state, onPan = component::onPan)
            }

            Spacer(modifier = Modifier.height(10.dp))
            EditorReadouts(
                localization = localization,
                segment = segment,
                positionSample = positionSample,
                sampleRate = sampleRate,
            )
        }
    }
}

private fun rejectionKey(rejection: EditorComponent.CutRejection): String = when (rejection) {
    EditorComponent.CutRejection.KEPT_TOO_SHORT -> "editor.cutRejected.tooShort"
    EditorComponent.CutRejection.OUTSIDE_SEGMENT -> "editor.cutRejected.outside"
    EditorComponent.CutRejection.SELECTION_EMPTY, EditorComponent.CutRejection.INVALID -> "editor.cutRejected.generic"
}

/** Zoom buttons on the left, the selection read-out on the right. */
@Composable
private fun EditorToolbar(localization: UiLocalization, state: EditorComponent.State, component: EditorComponent) {
    val anchor = state.positionSample?.takeIf { it in state.visibleStartSample..state.visibleStopSample }
        ?: ((state.visibleStartSample + state.visibleStopSample) / 2)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                localization.resolvePlain("editor.zoom"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedIconButton(
                onClick = { component.onZoomOut(anchor) },
                modifier = Modifier.size(32.dp).testTag(TestTags.Editor.ZOOM_OUT_BUTTON),
            ) { Icon(Icons.Filled.Remove, contentDescription = localization.resolvePlain("editor.zoomOut"), modifier = Modifier.size(18.dp)) }
            OutlinedIconButton(
                onClick = { component.onZoomIn(anchor) },
                modifier = Modifier.size(32.dp).testTag(TestTags.Editor.ZOOM_IN_BUTTON),
            ) { Icon(Icons.Filled.Add, contentDescription = localization.resolvePlain("editor.zoomIn"), modifier = Modifier.size(18.dp)) }
            OutlinedButton(
                onClick = component::onZoomFit,
                enabled = state.zoomed,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.height(32.dp).testTag(TestTags.Editor.ZOOM_FIT_BUTTON),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { ButtonLabel(localization.resolve("editor.zoomFit")) }
        }
        state.selection?.let { sel ->
            Text(
                localization.resolve(
                    "editor.selection",
                    mapOf(
                        "start" to formatSampleTime(sel.startSample, state.sampleRate),
                        "stop" to formatSampleTime(sel.stopSample, state.sampleRate),
                        "length" to formatSampleDuration(sel.length, state.sampleRate),
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(TestTags.Editor.SELECTION_LABEL),
            )
        }
    }
}

/** Selection band (accent-tinted) with a draggable handle on each edge. */
@Composable
private fun SelectionBand(
    startX: Float,
    stopX: Float,
    onDragStartEdge: (Float) -> Unit,
    onDragStopEdge: (Float) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.tertiary
    val widthDp = with(LocalDensity.current) { (stopX - startX).coerceAtLeast(0f).toDp() }
    Box(
        modifier = Modifier
            .offset { IntOffset(startX.roundToInt(), 0) }
            .width(widthDp)
            .fillMaxHeight()
            .background(accent.copy(alpha = 0.28f))
            .border(1.dp, accent.copy(alpha = 0.8f))
            .testTag(TestTags.Editor.SELECTION_BAND),
    )
    SelectionEdgeHandle(startX, TestTags.Editor.SELECTION_START_HANDLE, accent, onDragStartEdge)
    SelectionEdgeHandle(stopX, TestTags.Editor.SELECTION_STOP_HANDLE, accent, onDragStopEdge)
}

private val SELECTION_HANDLE_HIT_WIDTH = 16.dp

@Composable
private fun SelectionEdgeHandle(xPx: Float, testTag: String, color: Color, onDragToPixelX: (Float) -> Unit) {
    val latestXPx = rememberUpdatedState(xPx)
    val latestOnDrag = rememberUpdatedState(onDragToPixelX)
    var gestureBaseXPx by remember { mutableStateOf(0f) }
    var accumulatedPx by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .offset { IntOffset((xPx - SELECTION_HANDLE_HIT_WIDTH.toPx() / 2).roundToInt(), 0) }
            .width(SELECTION_HANDLE_HIT_WIDTH)
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
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.width(6.dp).height(28.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

/** A removed part: dark hatched band, its length when it fits, and a restore button on top. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CutBand(
    localization: UiLocalization,
    index: Int,
    xPx: Float,
    widthPx: Float,
    lengthText: String,
    onRestore: () -> Unit,
) {
    val fill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val stripe = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    val widthDp = with(LocalDensity.current) { widthPx.coerceAtLeast(0f).toDp() }
    Box(
        modifier = Modifier
            .offset { IntOffset(xPx.roundToInt(), 0) }
            .width(widthDp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(2.dp))
            .testTag(TestTags.Editor.cutBand(index)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(fill)
            clipRect {
                val gap = 9.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 2.dp.toPx())
                    x += gap
                }
            }
        }
        if (widthDp >= 84.dp) {
            Text(
                localization.resolve("editor.cutBand", mapOf("length" to lengthText)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        val restoreText = localization.resolvePlain("editor.restoreCut")
        TooltipArea(
            tooltip = {
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                    Text(
                        restoreText,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                    .testTag(TestTags.Editor.restoreCutButton(index))
                    .semantics { contentDescription = restoreText; role = Role.Button }
                    .clickable(onClick = onRestore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Visible window inside the fit window; dragging the thumb pans. */
@Composable
private fun PanBar(state: EditorComponent.State, onPan: (Long) -> Unit) {
    val latestState = rememberUpdatedState(state)
    val latestOnPan = rememberUpdatedState(onPan)
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    var baseStart by remember { mutableStateOf(0L) }
    var accumulatedPx by remember { mutableStateOf(0f) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .testTag(TestTags.Editor.PAN_BAR)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        baseStart = latestState.value.visibleStartSample
                        accumulatedPx = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedPx += dragAmount.x
                        val s = latestState.value
                        val fitSpan = (s.fitStopSample - s.fitStartSample).coerceAtLeast(1L)
                        latestOnPan.value(baseStart + (accumulatedPx / size.width * fitSpan).toLong())
                    },
                )
            },
    ) {
        val fitSpan = (state.fitStopSample - state.fitStartSample).coerceAtLeast(1L).toFloat()
        val left = ((state.visibleStartSample - state.fitStartSample) / fitSpan * size.width).coerceIn(0f, size.width)
        val right = ((state.visibleStopSample - state.fitStartSample) / fitSpan * size.width).coerceIn(left, size.width)
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(trackColor, size = size, cornerRadius = radius)
        drawRoundRect(
            thumbColor,
            topLeft = Offset(left, 0f),
            size = Size((right - left).coerceAtLeast(8f), size.height),
            cornerRadius = radius,
        )
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
    onSelectDrag: ((anchorX: Float, currentX: Float) -> Unit)? = null,
) {
    val insideColor = MaterialTheme.colorScheme.primary
    val outsideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val selectionFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant
    val boundaryColor = MaterialTheme.colorScheme.secondary
    val playheadColor = MaterialTheme.colorScheme.error
    val latestSelectDrag = rememberUpdatedState(onSelectDrag)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerHoverIcon(PointerIcon.Crosshair)
            .pointerInput(Unit) { detectTapGestures { offset -> onSeekToPixelX(offset.x) } }
            .then(
                if (onSelectDrag == null) Modifier else Modifier.pointerInput(Unit) {
                    var anchorX = 0f
                    detectDragGestures(
                        onDragStart = { offset -> anchorX = offset.x },
                        onDrag = { change, _ ->
                            change.consume()
                            latestSelectDrag.value?.invoke(anchorX, change.position.x)
                        },
                    )
                },
            ),
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
    segment: EditorComponent.Segment,
    positionSample: Long?,
    sampleRate: Int,
) {
    val startSample = segment.startSample
    val stopSample = segment.stopSample
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
                formatSampleDuration(segment.keptDurationSamples.coerceAtLeast(0L), sampleRate),
                emphasized = true,
                suffix = if (segment.cutCount == 0) null else localization.resolve(
                    if (segment.cutCount == 1) "editor.cutInfo.one" else "editor.cutInfo.other",
                    mapOf(
                        "original" to formatSampleDuration(segment.originalDurationSamples.coerceAtLeast(0L), sampleRate),
                        "count" to segment.cutCount.toString(),
                        "removed" to formatSampleDuration(segment.removedSamples, sampleRate),
                    ),
                ).text,
                testTag = TestTags.Editor.DURATION_LABEL,
            )
        }
        Readout(
            localization.resolvePlain("editor.positionLabel"),
            formatSampleTime(positionSample ?: startSample, sampleRate),
        )
    }
}

@Composable
private fun Readout(label: String, value: String, emphasized: Boolean = false, suffix: String? = null, testTag: String? = null) {
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
            modifier = if (testTag != null && suffix == null) Modifier.testTag(testTag) else Modifier,
        )
        if (suffix != null) {
            Text(
                text = suffix,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
            )
        }
    }
}

/** Everything actionable in one row (previous · play/stop · reset · next · discard · accept). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorControls(
    localization: UiLocalization,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onPlayToggle: () -> Unit,
    onNext: () -> Unit,
    enhanced: Boolean,
    hasSelection: Boolean,
    onPlaySelection: () -> Unit,
    onCutSelection: () -> Unit,
    onClearSelection: () -> Unit,
    canReset: Boolean,
    onReset: () -> Unit,
    onDiscard: () -> Unit,
    onAccept: () -> Unit,
) {
    val previousButton: @Composable () -> Unit = {
        Button(
            onClick = onPrevious,
            enabled = hasPrevious,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.testTag(TestTags.Editor.PREVIOUS_SEGMENT_BUTTON),
        ) {
            ButtonIcon(Icons.Filled.ChevronLeft)
            ButtonLabel(localization.resolve("action.previous"))
        }
    }
    val nextButton: @Composable () -> Unit = {
        Button(
            onClick = onNext,
            enabled = hasNext,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.testTag(TestTags.Editor.NEXT_SEGMENT_BUTTON),
        ) {
            ButtonLabel(localization.resolve("action.next"))
            ButtonIcon(Icons.Filled.ChevronRight, trailing = true)
        }
    }
    val playButton: @Composable () -> Unit = {
        Button(
            onClick = onPlayToggle,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.testTag(TestTags.Editor.PLAY_SEGMENT_BUTTON),
        ) {
            ButtonIcon(if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow)
            ButtonLabel(localization.resolve(if (isPlaying) "action.stop" else "action.play"))
        }
    }
    val resetButton: @Composable () -> Unit = {
        OutlinedButton(
            onClick = onReset,
            enabled = canReset,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.testTag(TestTags.Editor.RESET_SEGMENT_BUTTON),
        ) {
            ButtonIcon(Icons.Filled.Refresh)
            ButtonLabel(localization.resolve("editor.reset"))
        }
    }

    if (enhanced) {
        // Enhanced editor: two full-width rows, every button spread evenly across its row so both
        // rows share the same left and right edges. Top row = listening and cutting tools,
        // bottom row = moving between recordings and finishing.
        Column(
            modifier = Modifier.contentWidth(EDITOR_MAX_WIDTH),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag(TestTags.Editor.SELECTION_GROUP),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                playButton()
                OutlinedButton(
                    onClick = onPlaySelection,
                    enabled = hasSelection,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag(TestTags.Editor.PLAY_SELECTION_BUTTON),
                ) {
                    ButtonIcon(Icons.Filled.PlayArrow)
                    ButtonLabel(localization.resolve("editor.playSelection"))
                }
                OutlinedButton(
                    onClick = onCutSelection,
                    enabled = hasSelection,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag(TestTags.Editor.CUT_SELECTION_BUTTON),
                ) {
                    ButtonIcon(Icons.Filled.ContentCut)
                    ButtonLabel(localization.resolve("editor.cutSelection"))
                    ShortcutHint(localization.resolvePlain("editor.shortcut.delete"))
                }
                OutlinedButton(
                    onClick = onClearSelection,
                    enabled = hasSelection,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.testTag(TestTags.Editor.CLEAR_SELECTION_BUTTON),
                ) {
                    ButtonLabel(localization.resolve("editor.clearSelection"))
                    ShortcutHint(localization.resolvePlain("editor.shortcut.escape"))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().testTag(TestTags.Editor.ACTIONS_GROUP),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                previousButton()
                nextButton()
                resetButton()
                DiscardButton(localization, onDiscard)
                AcceptButton(localization, onAccept)
            }
        }
        return
    }

    // Same structure as the enhanced editor: Play on the top row, then one full-width row with
    // every navigation and finishing button spread evenly (Previous and Next stay together on one
    // row, F-130; labels never squeeze, F-093/F-125).
    Column(
        modifier = Modifier.contentWidth(EDITOR_MAX_WIDTH),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            playButton()
        }
        Row(
            modifier = Modifier.fillMaxWidth().testTag(TestTags.Editor.ACTIONS_GROUP),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            previousButton()
            nextButton()
            resetButton()
            DiscardButton(localization, onDiscard)
            AcceptButton(localization, onAccept)
        }
    }
}

@Composable
private fun DiscardButton(localization: UiLocalization, onDiscard: () -> Unit) {
    OutlinedButton(
        onClick = onDiscard,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.testTag(TestTags.Editor.DISCARD_BUTTON),
    ) {
        ButtonIcon(Icons.Filled.Close)
        ButtonLabel(localization.resolve("editor.discard"))
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
        ButtonLabel(localization.resolve("action.accept"))
    }
}

@Composable
private fun ShortcutHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(start = 8.dp),
    )
}

/** Button labels never wrap: the button keeps its intrinsic width and the row wraps instead. */
@Composable
private fun ButtonLabel(text: AnnotatedString) {
    Text(text, maxLines = 1, softWrap = false)
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
internal fun formatSampleTime(sample: Long, sampleRate: Int): String {
    if (sampleRate <= 0) return "–:––.–"
    return formatSeconds(sample.toDouble() / sampleRate)
}

/** Lengths are read as a count of seconds, not as a clock time (`7.4 s`, not `0:07.4`). */
internal fun formatSampleDuration(samples: Long, sampleRate: Int): String {
    if (sampleRate <= 0) return "– s"
    val tenths = roundedTenths(samples.toDouble() / sampleRate)
    // Rounded first, so 59.96 s carries into "1:00 min" rather than reading "60.0 s".
    if (tenths >= 600) return "${formatClock(tenths / 10.0)} min"
    return "${tenths / 10}.${tenths % 10} s"
}

/** Rounds to the displayed tenth (half up) instead of truncating: 62.887 s is 1:02.9, not 1:02.8. */
private fun roundedTenths(seconds: Double): Long = Math.round(seconds * 10).coerceAtLeast(0L)

internal fun formatSeconds(seconds: Double): String {
    val totalTenths = roundedTenths(seconds)
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
