package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.audio.AudioClipService
import org.example.app.domain.audio.AudioPlaybackService
import org.example.app.domain.audio.WaveformPeaks
import org.example.app.domain.audio.WaveformService
import org.example.app.domain.session.MasterPart
import org.example.app.domain.session.MasterPartMap
import org.example.app.domain.session.ProcessingStatus
import org.example.app.domain.session.SessionRepository
import org.example.app.domain.session.TaskRecord
import org.example.app.domain.session.TimelineSource
import org.example.app.domain.timeline.TakeSelector
import org.example.app.domain.timeline.BoundaryError
import org.example.app.domain.timeline.SampleRange
import org.example.app.domain.timeline.TimelineBoundaryValidator
import org.example.app.domain.timeline.TimelineEditedSegment
import org.example.app.domain.timeline.keptRanges
import org.example.app.domain.timeline.TimelineEdited
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineRepository

/**
 * §8.7 waveform editor: shown after the protocol when `enableEditor` is true. A **segment** is
 * the last take of one VOCAL task instance (§8.3); the editor shows one segment at a time, but
 * dragged boundaries persist across previous/next navigation — [onAccept] is the single action
 * that finalizes everything and hands control back to [onDone].
 *
 * All sample values in [Segment]/[State] are *local* to that segment's own source master part
 * ([MasterPartMap]) — never the cross-part global counter — since that is what
 * [WaveformService.peaks]/[AudioPlaybackService.playRange] and the eventual
 * `timeline_edited.json` segment shape both operate in per-file terms (the file itself, not a
 * global offset, identifies the part). Converting to/from the global counter used by timeline
 * events happens only at the edges (loading from [TimelineEvent]s, writing
 * [TimelineEditedSegment]s).
 */
interface EditorComponent {
    val state: Value<State>

    fun onPrevious()
    fun onNext()

    /** Drags the start/stop boundary of the *current* segment to [newLocalSample] (local to its
     * part); rejected (state unchanged) if it violates [org.example.app.domain.timeline.TimelineBoundaryValidator]
     * against the segment's own file range or its immediate neighbor within the same part. */
    fun onDragStart(newLocalSample: Long)
    fun onDragStop(newLocalSample: Long)

    /**
     * Moves the position line to [newLocalSample] (clamped to the visible window). While a
     * segment is playing this restarts playback from there; otherwise it only parks the line,
     * and the next [onPlayToggle] starts from it.
     */
    fun onSeek(newLocalSample: Long)

    fun onPlayToggle()

    /**
     * Enhanced editor: sets the selection. [anchorLocal] is the fixed point (the drag origin, or the
     * opposite handle while refining an edge), [movingLocal] the point being dragged; either order.
     * Both are clamped to the segment's `[start, stop]`. A selection never overlaps an existing cut:
     * an anchor strictly inside a cut is ignored (state unchanged), otherwise the moving end stops at
     * the nearest cut edge in its direction. A zero-width result clears the selection. No-op unless
     * [State.enhanced].
     */
    fun onSelect(anchorLocal: Long, movingLocal: Long)

    fun onClearSelection()

    /**
     * Enhanced editor: turns the selection into a cut. Both edges snap to the nearest zero crossing
     * (+-5 ms); the cut is merged with touching/overlapping cuts and validated. A rejected cut leaves
     * everything unchanged except [State.lastCutRejected]. No-op unless [State.enhanced].
     */
    fun onCutSelection()

    /** Removes the cut at [index] (ascending order within the current segment). Works even when not enhanced. */
    fun onRestoreCut(index: Int)

    /** Zoom by factor 2 around [anchorLocal] (kept at the same relative position). Min span ~2 s. */
    fun onZoomIn(anchorLocal: Long)
    fun onZoomOut(anchorLocal: Long)
    fun onZoomFit()

    /** Moves the zoomed window so it starts at [newVisibleStart] (same span; clamped to the fit window). */
    fun onPan(newVisibleStart: Long)

    /** Plays only the selection. */
    fun onPlaySelection()

    /**
     * Finalizes editing: writes `timeline_edited.json` with the **complete** segment list for
     * every VOCAL instance (§13 decision 33) only if at least one boundary actually moved from
     * its initial (take START/STOP event) position (§8.7 decision 13); an untouched pass writes
     * nothing. Either way, hands off to the caller (`RootComponent`) to proceed to processing.
     */
    fun onAccept()

    /** F-027: puts the current segment's boundaries back to what they were when the editor opened. */
    fun onResetSegment()

    /**
     * F-027: leaves without writing or deleting anything. Exits at once when nothing differs from
     * the opening state, otherwise opens the confirmation ([State.discardConfirmationOpen]).
     */
    fun onDiscard()
    fun onDiscardConfirmed()
    fun onDiscardCancelled()

    data class Segment(
        val taskIndex: Int,
        val repetition: Int,
        val subtype: String,
        val startSample: Long,
        val stopSample: Long,
        val initialStartSample: Long,
        val initialStopSample: Long,
        /** Total frame count of this segment's own source part file — the file-range clamp
         * (§8.7). */
        val fileTotalSamples: Long,
        /** Boundaries this segment had when the editor opened (a loaded prior edit, else the
         * take's own); what Reset and Discard go back to. */
        val openedStartSample: Long = startSample,
        val openedStopSample: Long = stopSample,
        /** F-025: how many repetitions the task this segment belongs to has (1 = no repetition
         * text in the label). [repetition] is the one-based repetition within that task. */
        val repetitionCount: Int = 1,
        /** Parts cut out of the clip (local samples, ascending, non-touching, strictly inside). */
        val cuts: List<Span> = emptyList(),
        /** Cuts this segment had when the editor opened (a loaded prior edit's, else none). */
        val openedCuts: List<Span> = cuts,
        /** This task instance filmed a video take (examination `videoTakes`). */
        val hasVideo: Boolean = false,
    ) {
        /** Edited = boundaries moved or something cut out; decides whether `timeline_edited.json` is written. */
        val moved: Boolean get() = startSample != initialStartSample || stopSample != initialStopSample || cuts.isNotEmpty()

        /** Differs from the opening state in this editor session (F-027). */
        val changedSinceOpened: Boolean
            get() = startSample != openedStartSample || stopSample != openedStopSample || cuts != openedCuts

        val cutCount: Int get() = cuts.size
        val originalDurationSamples: Long get() = stopSample - startSample
        val removedSamples: Long get() = cuts.sumOf { it.stopSample - it.startSample }
        val keptDurationSamples: Long get() = originalDurationSamples - removedSamples

        /** The UI shows "Video is not cut - audio will be shorter than video." when this is true. */
        val videoNotCut: Boolean get() = hasVideo && cuts.isNotEmpty()
    }

    /** Half-open local sample range `[startSample, stopSample)`. */
    data class Span(val startSample: Long, val stopSample: Long) {
        val length: Long get() = stopSample - startSample
    }

    /** Why the last [onCutSelection] was rejected. */
    enum class CutRejection { SELECTION_EMPTY, OUTSIDE_SEGMENT, KEPT_TOO_SHORT, INVALID }

    data class State(
        val loading: Boolean = true,
        val segments: List<Segment> = emptyList(),
        val currentIndex: Int = 0,
        /** ± 5s visible context window (local samples, clamped to file range and the
         * neighboring segment in the same part, §8.7) around the current segment's boundaries —
         * not a boundary offset itself. */
        val visibleStartSample: Long = 0,
        val visibleStopSample: Long = 0,
        val waveform: WaveformPeaks? = null,
        val isPlaying: Boolean = false,
        /** Position line: follows playback while [isPlaying], parks where it stopped or where the
         * examiner last clicked otherwise. Null only before the first segment is shown. */
        val positionSample: Long? = null,
        /** Capture sample rate — the UI needs it to label the time ruler (§8.7). */
        val sampleRate: Int = 0,
        val accepted: Boolean = false,
        val discardConfirmationOpen: Boolean = false,
        /** The enhanced-editor setting: selection, cutting and zoom are only active when true. */
        val enhanced: Boolean = false,
        /** Selection in the current segment (local samples), or null. */
        val selection: Span? = null,
        /** Set by a rejected [onCutSelection]; cleared by the next select/cut/clear/navigation. */
        val lastCutRejected: CutRejection? = null,
        /** The "Fit" window (clip +- 5 s, clamped); zoom and pan stay inside it. */
        val fitStartSample: Long = 0,
        val fitStopSample: Long = 0,
    ) {
        val zoomed: Boolean get() = visibleStartSample != fitStartSample || visibleStopSample != fitStopSample
        val currentSegment: Segment? get() = segments.getOrNull(currentIndex)
    }

    companion object {
        const val VISIBLE_CONTEXT_SECONDS = 5
        const val WAVEFORM_BUCKET_COUNT = 400
        const val MIN_VISIBLE_SECONDS = 2
        const val ZERO_CROSSING_RADIUS_MS = 5
    }
}

class DefaultEditorComponent(
    componentContext: ComponentContext,
    private val folderName: String,
    private val sessionRepository: SessionRepository,
    private val timelineRepository: TimelineRepository,
    private val waveformService: WaveformService,
    private val audioPlaybackService: AudioPlaybackService,
    private val dispatchers: CoroutineDispatchers,
    /**
     * [needsReprocess] is true when Accept altered the edit state on disk (different or deleted
     * `timeline_edited.json`), or when the last successful processing used a different timeline
     * than the one now in effect (a session left stale by an earlier edit).
     */
    private val onDone: (folderName: String, needsReprocess: Boolean) -> Unit,
    /** F-027: the examiner left via Discard; nothing was written or deleted. */
    private val onDiscarded: (folderName: String) -> Unit,
    /** The "Enhanced editor" setting, read when the editor is created. */
    private val enhanced: Boolean = false,
    /** Used to snap cut edges to zero crossings; null = no snapping. */
    private val audioClipService: AudioClipService? = null,
) : EditorComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    /** One entry per loaded segment, in the same order/index as [EditorComponent.State.segments]. */
    private data class Loaded(val segment: EditorComponent.Segment, val part: MasterPart)

    private var loaded: List<Loaded> = emptyList()
    private var sampleRate: Int = 0

    private val _state = MutableValue(EditorComponent.State(enhanced = enhanced))
    override val state: Value<EditorComponent.State> = _state

    init {
        lifecycle.doOnDestroy {
            audioPlaybackService.stop()
            scope.cancel()
        }
        scope.launch(dispatchers.main) {
            audioPlaybackService.isPlaying.collect { playing -> _state.value = _state.value.copy(isPlaying = playing) }
        }
        scope.launch(dispatchers.main) {
            audioPlaybackService.positionSamples.collect { pos ->
                // Only while the sound is running: the service keeps its last value after stop,
                // and a stale replay of it would drag the line back off a manual seek.
                if (audioPlaybackService.isPlaying.value) {
                    _state.value = _state.value.copy(positionSample = pos)
                }
            }
        }
        scope.launch(dispatchers.main) { load() }
    }

    private suspend fun load() {
        val examination = sessionRepository.readExamination(folderName)
        val original = timelineRepository.readOriginal(folderName)
        val format = examination?.captureFormat
        if (examination == null || original == null || format == null) {
            _state.value = EditorComponent.State(loading = false, enhanced = enhanced)
            return
        }
        sampleRate = format.sampleRate

        val sessionDir = sessionRepository.sessionDir(folderName)
        val parts = MasterPartMap.build(
            defaultMasterFile = sessionRepository.defaultMasterFile(folderName),
            defaultFormat = format,
            interruptions = examination.interruptions,
            sessionDir = sessionDir,
        )
        val globalTotal = globalTotalSamples(original.events, parts)

        val editedByKey = timelineRepository.readEdited(folderName)?.segments.orEmpty()
            .associateBy { it.taskIndex to it.repetition }

        val repetitionCounts = repetitionCounts(examination.tasks)
        val vocalRecords = examination.tasks.filter { it.type == "VOCAL" && !it.skipped }
        val result = mutableListOf<Loaded>()
        for (record in vocalRecords) {
            val take = TakeSelector.lastTake(original.events, record.taskIndex, record.repetition) ?: continue
            val globalRange = TakeSelector.takeSampleRange(original.events, record.taskIndex, record.repetition, take) ?: continue
            val resolved = MasterPartMap.resolve(parts, globalRange.first, globalRange.last + 1) ?: continue
            val (part, localRange) = resolved
            val fileTotal = (part.globalEndExclusive ?: globalTotal) - part.globalStart
            // Previously accepted edit (global samples → this part's local frame); ignored if it
            // no longer fits the part's file.
            val editedGlobal = editedByKey[record.taskIndex to record.repetition]
            val edited = editedGlobal
                ?.let { (it.startSample - part.globalStart) to (it.stopSample - part.globalStart) }
                ?.takeIf { (s, e) -> s >= 0 && e > s && e <= fileTotal }
            // Prior cuts (global -> local); dropped (boundaries kept) if they do not validate.
            val priorCuts = if (edited == null || editedGlobal == null) emptyList() else {
                val local = editedGlobal.cuts.map {
                    EditorComponent.Span(it.startSample - part.globalStart, it.stopSample - part.globalStart)
                }
                val probe = TimelineEditedSegment(
                    record.taskIndex, record.repetition, edited.first, edited.second,
                    local.map { SampleRange(it.startSample, it.stopSample) },
                )
                if (TimelineBoundaryValidator.validateCuts(probe, sampleRate).isEmpty()) local else emptyList()
            }
            val hasVideo = examination.videoTakes.any { it.taskIndex == record.taskIndex && it.repetition == record.repetition }
            result += Loaded(
                segment = EditorComponent.Segment(
                    taskIndex = record.taskIndex,
                    repetition = record.repetition,
                    subtype = record.subtype.orEmpty(),
                    startSample = edited?.first ?: localRange.first,
                    stopSample = edited?.second ?: (localRange.last + 1),
                    initialStartSample = localRange.first,
                    initialStopSample = localRange.last + 1,
                    fileTotalSamples = fileTotal,
                    openedStartSample = edited?.first ?: localRange.first,
                    openedStopSample = edited?.second ?: (localRange.last + 1),
                    repetitionCount = repetitionCounts[record.taskIndex] ?: 1,
                    cuts = priorCuts,
                    openedCuts = priorCuts,
                    hasVideo = hasVideo,
                ),
                part = part,
            )
        }
        loaded = result.sortedBy { it.segment.taskIndex }

        _state.value = EditorComponent.State(
            loading = false,
            segments = loaded.map { it.segment },
            currentIndex = 0,
            sampleRate = sampleRate,
            enhanced = enhanced,
        )
        refreshVisibleWindow()
    }

    /**
     * F-025: taskIndex → repetition count of its task. The expanded instance list keeps the
     * repetitions of one task contiguous with the same type/subtype and repetition 1, 2, ...; a
     * run is closed when the repetition does not continue the count. Skipped instances still
     * count, so "repetition 2" stays "repetition 2" when repetition 1 was skipped.
     */
    private fun repetitionCounts(tasks: List<TaskRecord>): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        var run = mutableListOf<TaskRecord>()
        fun close() {
            run.forEach { result[it.taskIndex] = run.size }
            run = mutableListOf()
        }
        for (task in tasks) {
            val last = run.lastOrNull()
            val continues = last != null && last.type == task.type && last.subtype == task.subtype &&
                task.repetition == last.repetition + 1
            if (!continues) close()
            run += task
        }
        close()
        return result
    }

    private fun globalTotalSamples(events: List<TimelineEvent>, parts: List<MasterPart>): Long {
        val stopped = events.lastOrNull { it.type == TimelineEventType.SESSION_RECORDING_STOPPED }?.sampleOffset
        if (stopped != null) return stopped
        val maxEventOffset = events.mapNotNull { it.sampleOffset }.maxOrNull() ?: 0L
        val lastPartStart = parts.lastOrNull()?.globalStart ?: 0L
        return maxOf(maxEventOffset, lastPartStart)
    }

    override fun onPrevious() {
        val index = _state.value.currentIndex
        if (index <= 0) return
        _state.value = _state.value.copy(currentIndex = index - 1)
        audioPlaybackService.stop()
        refreshVisibleWindow()
    }

    override fun onNext() {
        val index = _state.value.currentIndex
        if (index >= loaded.lastIndex) return
        _state.value = _state.value.copy(currentIndex = index + 1)
        audioPlaybackService.stop()
        refreshVisibleWindow()
    }

    override fun onDragStart(newLocalSample: Long) = dragBoundary(isStart = true, newLocalSample)

    override fun onDragStop(newLocalSample: Long) = dragBoundary(isStart = false, newLocalSample)

    private fun dragBoundary(isStart: Boolean, newLocalSample: Long) {
        val current = loaded.getOrNull(_state.value.currentIndex) ?: return
        applyCandidate(
            if (isStart) current.segment.copy(startSample = newLocalSample) else current.segment.copy(stopSample = newLocalSample),
        )
    }

    override fun onResetSegment() {
        val current = loaded.getOrNull(_state.value.currentIndex) ?: return
        val errors = applyCandidate(
            current.segment.copy(
                startSample = current.segment.openedStartSample,
                stopSample = current.segment.openedStopSample,
                cuts = emptyList(),
            ),
        )
        if (errors.isEmpty()) _state.value = _state.value.copy(selection = null, lastCutRejected = null)
    }

    /** Applies [candidate] if it validates; returns the validation errors (empty = applied). */
    private fun applyCandidate(candidate: EditorComponent.Segment): List<BoundaryError> {
        val index = _state.value.currentIndex
        val current = loaded.getOrNull(index) ?: return emptyList()

        val previous = previousInSamePart(index)
        val next = nextInSamePart(index)
        val errors = org.example.app.domain.timeline.TimelineBoundaryValidator.validateSingle(
            segment = toEditedSegment(candidate),
            previous = previous?.let { toEditedSegment(it.segment) },
            next = next?.let { toEditedSegment(it.segment) },
            totalSamples = current.segment.fileTotalSamples,
            // The part's own rate: local samples are in the part's file, which may differ from the
            // examination's rate after a device switch.
            sampleRate = current.part.format.sampleRate,
        )
        if (errors.isNotEmpty()) return errors // reject the drag, state unchanged (§8.7 boundary validation)

        loaded = loaded.toMutableList().also { it[index] = current.copy(segment = candidate) }
        // Deliberately does NOT call refreshVisibleWindow() here: the ±5s visible window is
        // fixed for the duration of one segment view (§8.7 — it is a *display* context window,
        // not a boundary), so the pixel<->sample mapping stays stable while the examiner drags,
        // instead of shifting under their pointer as the boundary approaches its edge.
        // F-028: keep the position line inside the (possibly shrunk) range. A line left at the
        // new stop is the same "parked at the end" state onPlayToggle already treats as "replay
        // the whole segment", so clamping to the stop (not jumping to the start) changes nothing
        // about what Play does, and the line stays at the nearest edge instead of teleporting.
        val position = _state.value.positionSample?.coerceIn(candidate.startSample, candidate.stopSample)?.let { outOfCut(candidate, it) }
        val selection = _state.value.selection?.let {
            val a = it.startSample.coerceAtLeast(candidate.startSample)
            val b = it.stopSample.coerceAtMost(candidate.stopSample)
            if (b > a) EditorComponent.Span(a, b) else null
        }
        _state.value = _state.value.copy(segments = loaded.map { it.segment }, positionSample = position, selection = selection)
        return emptyList()
    }

    /** [sample], or the end of the cut it falls in (= the next kept range's start). */
    private fun outOfCut(segment: EditorComponent.Segment, sample: Long): Long =
        segment.cuts.firstOrNull { sample >= it.startSample && sample < it.stopSample }?.stopSample ?: sample

    /** Kept (inclusive) ranges of [segment] from [from] on; a [from] inside a cut starts at the next range. */
    private fun keptRangesFrom(segment: EditorComponent.Segment, from: Long): List<LongRange> =
        toEditedSegment(segment).keptRanges().mapNotNull { r ->
            if (r.last < from) null else maxOf(r.first, from)..r.last
        }

    private fun toEditedSegment(segment: EditorComponent.Segment): TimelineEditedSegment =
        TimelineEditedSegment(
            taskIndex = segment.taskIndex,
            repetition = segment.repetition,
            startSample = segment.startSample,
            stopSample = segment.stopSample,
            cuts = segment.cuts.map { SampleRange(it.startSample, it.stopSample) },
        )

    private fun previousInSamePart(index: Int): Loaded? {
        val current = loaded.getOrNull(index) ?: return null
        return loaded.subList(0, index).lastOrNull { it.part.file == current.part.file }
    }

    private fun nextInSamePart(index: Int): Loaded? {
        val current = loaded.getOrNull(index) ?: return null
        return loaded.subList(index + 1, loaded.size).firstOrNull { it.part.file == current.part.file }
    }

    override fun onSeek(newLocalSample: Long) {
        val current = loaded.getOrNull(_state.value.currentIndex) ?: return
        // Seeking into a cut lands at the start of the next kept range.
        val target = outOfCut(
            current.segment,
            newLocalSample.coerceIn(_state.value.visibleStartSample, _state.value.visibleStopSample),
        )
        _state.value = _state.value.copy(positionSample = target)
        if (_state.value.isPlaying) {
            if (target < current.segment.stopSample) {
                playFrom(current, target)
            } else {
                audioPlaybackService.stop()
            }
        }
    }

    override fun onPlayToggle() {
        val current = loaded.getOrNull(_state.value.currentIndex) ?: return
        if (_state.value.isPlaying) {
            audioPlaybackService.stop()
            return
        }
        // Resume from wherever the line was parked, as long as that is inside the segment — a
        // line left at the end (or dropped outside by a boundary drag) replays the whole segment.
        val position = _state.value.positionSample
        val from = if (position != null && position in current.segment.startSample until current.segment.stopSample) {
            position
        } else {
            current.segment.startSample
        }
        playFrom(current, from)
    }

    /** Plays the segment from [from] to its stop; with cuts, only the kept ranges (cuts are skipped). */
    private fun playFrom(current: Loaded, from: Long) {
        val segment = current.segment
        if (segment.cuts.isEmpty()) {
            audioPlaybackService.playRange(current.part.file, from, segment.stopSample)
            return
        }
        val ranges = keptRangesFrom(segment, from)
        if (ranges.isEmpty()) audioPlaybackService.stop() else audioPlaybackService.playRanges(current.part.file, ranges)
    }

    override fun onPlaySelection() {
        val current = loaded.getOrNull(_state.value.currentIndex) ?: return
        val selection = _state.value.selection ?: return
        audioPlaybackService.playRange(current.part.file, selection.startSample, selection.stopSample)
    }

    override fun onSelect(anchorLocal: Long, movingLocal: Long) {
        if (!enhanced) return
        val segment = loaded.getOrNull(_state.value.currentIndex)?.segment ?: return
        val anchor = anchorLocal.coerceIn(segment.startSample, segment.stopSample)
        if (segment.cuts.any { anchor > it.startSample && anchor < it.stopSample }) return
        var moving = movingLocal.coerceIn(segment.startSample, segment.stopSample)
        if (moving > anchor) {
            segment.cuts.firstOrNull { it.startSample >= anchor }?.let { moving = minOf(moving, it.startSample) }
        } else if (moving < anchor) {
            segment.cuts.lastOrNull { it.stopSample <= anchor }?.let { moving = maxOf(moving, it.stopSample) }
        }
        val span = if (moving == anchor) null else EditorComponent.Span(minOf(anchor, moving), maxOf(anchor, moving))
        _state.value = _state.value.copy(selection = span, lastCutRejected = null)
    }

    override fun onClearSelection() {
        _state.value = _state.value.copy(selection = null, lastCutRejected = null)
    }

    override fun onCutSelection() {
        if (!enhanced) return
        val index = _state.value.currentIndex
        val current = loaded.getOrNull(index) ?: return
        val selection = _state.value.selection ?: return
        val segment = current.segment
        val radius = current.part.format.sampleRate.toLong() * EditorComponent.ZERO_CROSSING_RADIUS_MS / 1000
        fun snap(edge: Long): Long = audioClipService?.nearestZeroCrossing(current.part.file, edge, radius) ?: edge
        val start = snap(selection.startSample)
        val stop = snap(selection.stopSample)
        if (stop <= start) {
            _state.value = _state.value.copy(lastCutRejected = EditorComponent.CutRejection.SELECTION_EMPTY)
            return
        }
        // Merge with every existing cut it touches or overlaps, keep the list sorted.
        var mergedStart = start
        var mergedStop = stop
        val others = mutableListOf<EditorComponent.Span>()
        for (cut in segment.cuts) {
            if (cut.stopSample >= mergedStart && cut.startSample <= mergedStop) {
                mergedStart = minOf(mergedStart, cut.startSample)
                mergedStop = maxOf(mergedStop, cut.stopSample)
            } else {
                others += cut
            }
        }
        val cuts = (others + EditorComponent.Span(mergedStart, mergedStop)).sortedBy { it.startSample }
        val errors = applyCandidate(segment.copy(cuts = cuts))
        if (errors.isEmpty()) {
            stopPlaybackAfterCutChange()
            _state.value = _state.value.copy(selection = null, lastCutRejected = null)
        } else {
            val reason = when {
                errors.any { it is BoundaryError.KeptTooShort } -> EditorComponent.CutRejection.KEPT_TOO_SHORT
                errors.any { it is BoundaryError.CutOutsideSegment } -> EditorComponent.CutRejection.OUTSIDE_SEGMENT
                else -> EditorComponent.CutRejection.INVALID
            }
            _state.value = _state.value.copy(lastCutRejected = reason)
        }
    }

    override fun onRestoreCut(index: Int) {
        val segment = loaded.getOrNull(_state.value.currentIndex)?.segment ?: return
        if (index !in segment.cuts.indices) return
        if (applyCandidate(segment.copy(cuts = segment.cuts.filterIndexed { i, _ -> i != index })).isEmpty()) {
            stopPlaybackAfterCutChange()
        }
        _state.value = _state.value.copy(lastCutRejected = null)
    }

    /**
     * A running playback was started with the old kept ranges, so it would keep playing (and
     * reporting positions inside) audio that is now cut. Stop it; Play resumes with the new cuts.
     */
    private fun stopPlaybackAfterCutChange() {
        if (_state.value.isPlaying) audioPlaybackService.stop()
    }

    override fun onZoomIn(anchorLocal: Long) = zoomBy(anchorLocal, zoomIn = true)

    override fun onZoomOut(anchorLocal: Long) = zoomBy(anchorLocal, zoomIn = false)

    private fun zoomBy(anchorLocal: Long, zoomIn: Boolean) {
        if (!enhanced || loaded.isEmpty()) return
        val st = _state.value
        val span = st.visibleStopSample - st.visibleStartSample
        val fitSpan = st.fitStopSample - st.fitStartSample
        if (span <= 0) return
        val minSpan = minOf(EditorComponent.MIN_VISIBLE_SECONDS.toLong() * sampleRate.coerceAtLeast(1), fitSpan)
        val newSpan = if (zoomIn) maxOf(span / 2, minSpan).coerceAtMost(span) else minOf(span * 2, fitSpan)
        if (newSpan == span) return
        val anchor = anchorLocal.coerceIn(st.visibleStartSample, st.visibleStopSample)
        // Keep the anchor at the same relative position inside the window.
        val newStart = anchor - (anchor - st.visibleStartSample) * newSpan / span
        setWindow(newStart, newSpan)
    }

    override fun onZoomFit() {
        if (!enhanced || loaded.isEmpty()) return
        val st = _state.value
        setWindow(st.fitStartSample, st.fitStopSample - st.fitStartSample)
    }

    override fun onPan(newVisibleStart: Long) {
        if (!enhanced || loaded.isEmpty()) return
        val st = _state.value
        setWindow(newVisibleStart, st.visibleStopSample - st.visibleStartSample)
    }

    /** Shows `[start, start + span)` clamped into the fit window, and refetches its peaks. */
    private fun setWindow(start: Long, span: Long) {
        val st = _state.value
        val current = loaded.getOrNull(st.currentIndex) ?: return
        val clampedStart = start.coerceIn(st.fitStartSample, (st.fitStopSample - span).coerceAtLeast(st.fitStartSample))
        val stop = (clampedStart + span).coerceAtMost(st.fitStopSample)
        if (stop <= clampedStart) return
        val peaks = waveformService.peaks(current.part.file, EditorComponent.WAVEFORM_BUCKET_COUNT, clampedStart, stop)
        _state.value = _state.value.copy(visibleStartSample = clampedStart, visibleStopSample = stop, waveform = peaks)
    }

    override fun onAccept() {
        val anyMoved = loaded.any { it.segment.moved }
        val previousEdited = timelineRepository.readEdited(folderName)
        val examination = sessionRepository.readExamination(folderName)
        var needsReprocess: Boolean
        if (anyMoved) {
            val segments = loaded.map { entry ->
                TimelineEditedSegment(
                    taskIndex = entry.segment.taskIndex,
                    repetition = entry.segment.repetition,
                    // Persisted timeline segments are global-sample-shaped (§8.10); translate
                    // back from this part's local frame offsets.
                    startSample = entry.segment.startSample + entry.part.globalStart,
                    stopSample = entry.segment.stopSample + entry.part.globalStart,
                    cuts = entry.segment.cuts.map {
                        SampleRange(it.startSample + entry.part.globalStart, it.stopSample + entry.part.globalStart)
                    },
                )
            }
            needsReprocess = previousEdited?.segments != segments
            timelineRepository.writeEdited(
                folderName,
                TimelineEdited(
                    sessionId = examination?.sessionId.orEmpty(),
                    sampleRate = sampleRate,
                    segments = segments,
                ),
            )
        } else {
            needsReprocess = previousEdited != null
            // Everything is back at the original position: drop any earlier edit so processing
            // uses the original timeline again.
            timelineRepository.deleteEdited(folderName)
        }
        val processing = examination?.processing
        if (processing?.status == ProcessingStatus.Done) {
            val nowUsed = if (anyMoved) TimelineSource.EDITED else TimelineSource.ORIGINAL
            if ((processing.timelineUsed ?: TimelineSource.ORIGINAL) != nowUsed) needsReprocess = true
        }
        audioPlaybackService.stop()
        _state.value = _state.value.copy(accepted = true)
        onDone(folderName, needsReprocess)
    }

    override fun onDiscard() {
        if (loaded.none { it.segment.changedSinceOpened }) {
            exitDiscarded()
        } else {
            _state.value = _state.value.copy(discardConfirmationOpen = true)
        }
    }

    override fun onDiscardConfirmed() {
        if (!_state.value.discardConfirmationOpen) return
        exitDiscarded()
    }

    override fun onDiscardCancelled() {
        _state.value = _state.value.copy(discardConfirmationOpen = false)
    }

    private fun exitDiscarded() {
        audioPlaybackService.stop()
        _state.value = _state.value.copy(discardConfirmationOpen = false)
        onDiscarded(folderName)
    }

    private fun refreshVisibleWindow() {
        val current = loaded.getOrNull(_state.value.currentIndex)
        if (current == null) {
            _state.value = _state.value.copy(waveform = null)
            return
        }
        val segment = current.segment
        val contextSamples = EditorComponent.VISIBLE_CONTEXT_SECONDS.toLong() * sampleRate.coerceAtLeast(1)
        val prev = previousInSamePart(_state.value.currentIndex)
        val next = nextInSamePart(_state.value.currentIndex)

        var visibleStart = (segment.startSample - contextSamples).coerceAtLeast(0L)
        var visibleStop = (segment.stopSample + contextSamples).coerceAtMost(segment.fileTotalSamples)
        prev?.let { visibleStart = visibleStart.coerceAtLeast(it.segment.stopSample) }
        next?.let { visibleStop = visibleStop.coerceAtMost(it.segment.startSample) }
        if (visibleStop <= visibleStart) visibleStop = (visibleStart + 1).coerceAtMost(segment.fileTotalSamples)

        val peaks = if (visibleStop > visibleStart) {
            waveformService.peaks(current.part.file, EditorComponent.WAVEFORM_BUCKET_COUNT, visibleStart, visibleStop)
        } else {
            null
        }

        _state.value = _state.value.copy(
            visibleStartSample = visibleStart,
            visibleStopSample = visibleStop,
            fitStartSample = visibleStart,
            fitStopSample = visibleStop,
            selection = null,
            lastCutRejected = null,
            waveform = peaks,
            // Each segment view starts with the line parked at its own start boundary; carrying
            // the previous segment's position over would point at an unrelated part of the file.
            positionSample = segment.startSample,
        )
    }
}
