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
import org.example.app.domain.audio.AudioPlaybackService
import org.example.app.domain.audio.WaveformPeaks
import org.example.app.domain.audio.WaveformService
import org.example.app.domain.session.MasterPart
import org.example.app.domain.session.MasterPartMap
import org.example.app.domain.session.SessionRepository
import org.example.app.domain.timeline.TakeSelector
import org.example.app.domain.timeline.TimelineEditedSegment
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

    fun onPlayToggle()

    /**
     * Finalizes editing: writes `timeline_edited.json` with the **complete** segment list for
     * every VOCAL instance (§13 decision 33) only if at least one boundary actually moved from
     * its initial (take START/STOP event) position (§8.7 decision 13); an untouched pass writes
     * nothing. Either way, hands off to the caller (`RootComponent`) to proceed to processing.
     */
    fun onAccept()

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
    ) {
        val moved: Boolean get() = startSample != initialStartSample || stopSample != initialStopSample
    }

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
        val positionSample: Long? = null,
        val accepted: Boolean = false,
    ) {
        val currentSegment: Segment? get() = segments.getOrNull(currentIndex)
    }

    companion object {
        const val VISIBLE_CONTEXT_SECONDS = 5
        const val WAVEFORM_BUCKET_COUNT = 400
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
    private val onDone: (folderName: String) -> Unit,
) : EditorComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    /** One entry per loaded segment, in the same order/index as [EditorComponent.State.segments]. */
    private data class Loaded(val segment: EditorComponent.Segment, val part: MasterPart)

    private var loaded: List<Loaded> = emptyList()
    private var sampleRate: Int = 0

    private val _state = MutableValue(EditorComponent.State())
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
            audioPlaybackService.positionSamples.collect { pos -> _state.value = _state.value.copy(positionSample = pos) }
        }
        scope.launch(dispatchers.main) { load() }
    }

    private suspend fun load() {
        val examination = sessionRepository.readExamination(folderName)
        val original = timelineRepository.readOriginal(folderName)
        val format = examination?.captureFormat
        if (examination == null || original == null || format == null) {
            _state.value = EditorComponent.State(loading = false)
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

        val vocalRecords = examination.tasks.filter { it.type == "VOCAL" && !it.skipped }
        val result = mutableListOf<Loaded>()
        for (record in vocalRecords) {
            val take = TakeSelector.lastTake(original.events, record.taskIndex, record.repetition) ?: continue
            val globalRange = TakeSelector.takeSampleRange(original.events, record.taskIndex, record.repetition, take) ?: continue
            val resolved = MasterPartMap.resolve(parts, globalRange.first, globalRange.last + 1) ?: continue
            val (part, localRange) = resolved
            val fileTotal = (part.globalEndExclusive ?: globalTotal) - part.globalStart
            result += Loaded(
                segment = EditorComponent.Segment(
                    taskIndex = record.taskIndex,
                    repetition = record.repetition,
                    subtype = record.subtype.orEmpty(),
                    startSample = localRange.first,
                    stopSample = localRange.last + 1,
                    initialStartSample = localRange.first,
                    initialStopSample = localRange.last + 1,
                    fileTotalSamples = fileTotal,
                ),
                part = part,
            )
        }
        loaded = result.sortedBy { it.segment.taskIndex }

        _state.value = EditorComponent.State(
            loading = false,
            segments = loaded.map { it.segment },
            currentIndex = 0,
        )
        refreshVisibleWindow()
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
        val index = _state.value.currentIndex
        val current = loaded.getOrNull(index) ?: return
        val candidate = if (isStart) {
            current.segment.copy(startSample = newLocalSample)
        } else {
            current.segment.copy(stopSample = newLocalSample)
        }

        val previous = previousInSamePart(index)
        val next = nextInSamePart(index)
        val errors = org.example.app.domain.timeline.TimelineBoundaryValidator.validateSingle(
            segment = toEditedSegment(candidate),
            previous = previous?.let { toEditedSegment(it.segment) },
            next = next?.let { toEditedSegment(it.segment) },
            totalSamples = current.segment.fileTotalSamples,
        )
        if (errors.isNotEmpty()) return // reject the drag, state unchanged (§8.7 boundary validation)

        loaded = loaded.toMutableList().also { it[index] = current.copy(segment = candidate) }
        // Deliberately does NOT call refreshVisibleWindow() here: the ±5s visible window is
        // fixed for the duration of one segment view (§8.7 — it is a *display* context window,
        // not a boundary), so the pixel<->sample mapping stays stable while the examiner drags,
        // instead of shifting under their pointer as the boundary approaches its edge.
        _state.value = _state.value.copy(segments = loaded.map { it.segment })
    }

    private fun toEditedSegment(segment: EditorComponent.Segment): TimelineEditedSegment =
        TimelineEditedSegment(
            taskIndex = segment.taskIndex,
            repetition = segment.repetition,
            startSample = segment.startSample,
            stopSample = segment.stopSample,
        )

    private fun previousInSamePart(index: Int): Loaded? {
        val current = loaded.getOrNull(index) ?: return null
        return loaded.subList(0, index).lastOrNull { it.part.file == current.part.file }
    }

    private fun nextInSamePart(index: Int): Loaded? {
        val current = loaded.getOrNull(index) ?: return null
        return loaded.subList(index + 1, loaded.size).firstOrNull { it.part.file == current.part.file }
    }

    override fun onPlayToggle() {
        val current = loaded.getOrNull(_state.value.currentIndex) ?: return
        if (_state.value.isPlaying) {
            audioPlaybackService.stop()
        } else {
            audioPlaybackService.playRange(current.part.file, current.segment.startSample, current.segment.stopSample)
        }
    }

    override fun onAccept() {
        val anyMoved = loaded.any { it.segment.moved }
        if (anyMoved) {
            val segments = loaded.map { entry ->
                TimelineEditedSegment(
                    taskIndex = entry.segment.taskIndex,
                    repetition = entry.segment.repetition,
                    // Persisted timeline segments are global-sample-shaped (§8.10); translate
                    // back from this part's local frame offsets.
                    startSample = entry.segment.startSample + entry.part.globalStart,
                    stopSample = entry.segment.stopSample + entry.part.globalStart,
                )
            }
            val examination = sessionRepository.readExamination(folderName)
            timelineRepository.writeEdited(
                folderName,
                TimelineEdited(
                    sessionId = examination?.sessionId.orEmpty(),
                    sampleRate = sampleRate,
                    segments = segments,
                ),
            )
        }
        audioPlaybackService.stop()
        _state.value = _state.value.copy(accepted = true)
        onDone(folderName)
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
            waveform = peaks,
        )
    }
}
