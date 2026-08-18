package org.example.app.ui.previews

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import org.example.app.domain.audio.WaveformPeaks
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.navigation.EditorComponent
import org.example.app.ui.EditorContent
import org.example.app.ui.UiLocalization
import org.example.app.ui.theme.ShareTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Design-time previews of [EditorContent] (§8.7). A preview function takes no parameters, so the
 * screen's [EditorComponent] and [UiLocalization] are supplied as stand-ins below — nothing here
 * touches `AppContainer`, the filesystem or an audio device.
 *
 * Unlike the calibration stand-in these are *not* inert: dragging a boundary and clicking the
 * waveform really move the state, because those interactions are most of what the screen is, and
 * a preview that ignores them cannot be judged. `EditorLiveDev` (dev source set) is the
 * counterpart that runs the real component over a recorded session — the only way to check the
 * position line against audio you can actually hear.
 *
 * [ShareTheme] is applied here: neither `PreviewHarness` nor `hotDev` wraps its content, so
 * without it the screen renders in Material3's purple baseline instead of the app's palette.
 */

/** Mutable stand-in: applies drags/seeks locally, with no validation beyond staying in range. */
private class PreviewEditorComponent(initial: EditorComponent.State) : EditorComponent {
    private val _state = MutableValue(initial)
    override val state: Value<EditorComponent.State> = _state

    override fun onPrevious() = move(-1)
    override fun onNext() = move(+1)

    private fun move(delta: Int) {
        val next = (_state.value.currentIndex + delta).coerceIn(0, _state.value.segments.lastIndex)
        _state.value = _state.value.copy(currentIndex = next)
    }

    override fun onDragStart(newLocalSample: Long) = updateSegment { segment ->
        segment.copy(startSample = newLocalSample.coerceIn(_state.value.visibleStartSample, segment.stopSample - 1))
    }

    override fun onDragStop(newLocalSample: Long) = updateSegment { segment ->
        segment.copy(stopSample = newLocalSample.coerceIn(segment.startSample + 1, _state.value.visibleStopSample))
    }

    private fun updateSegment(transform: (EditorComponent.Segment) -> EditorComponent.Segment) {
        val index = _state.value.currentIndex
        val segments = _state.value.segments.toMutableList()
        val current = segments.getOrNull(index) ?: return
        segments[index] = transform(current)
        _state.value = _state.value.copy(segments = segments)
    }

    override fun onSeek(newLocalSample: Long) {
        _state.value = _state.value.copy(
            positionSample = newLocalSample.coerceIn(_state.value.visibleStartSample, _state.value.visibleStopSample),
        )
    }

    override fun onPlayToggle() {
        _state.value = _state.value.copy(isPlaying = !_state.value.isPlaying)
    }

    override fun onAccept() = Unit

    /** Advances the position line as playback would, wrapping at the stop boundary. */
    fun advance(samples: Long) {
        val segment = _state.value.currentSegment ?: return
        val next = (_state.value.positionSample ?: segment.startSample) + samples
        _state.value = _state.value.copy(
            positionSample = if (next >= segment.stopSample) segment.startSample else next,
        )
    }
}

private const val PREVIEW_SAMPLE_RATE = 48_000

/**
 * A speech-shaped envelope: words of a few syllables with real pauses between them inside the
 * segment, room noise in the ±5s context around it — so the preview shows the case the screen
 * exists for (finding where the utterance really begins), not a uniform block of noise. A flat
 * loud block would also flatter the design: peaky material is what has to stay readable.
 */
private fun previewPeaks(bucketCount: Int, segmentFraction: ClosedFloatingPointRange<Float>): WaveformPeaks {
    val random = Random(seed = 7)
    val mins = ArrayList<Float>(bucketCount)
    val maxs = ArrayList<Float>(bucketCount)
    // Loudness of each successive word; a word is followed by a pause of a few buckets.
    val wordLoudness = listOf(0.55f, 0.9f, 0.35f, 0.75f, 0.5f, 0.95f, 0.42f, 0.68f)
    for (i in 0 until bucketCount) {
        val position = i.toFloat() / bucketCount
        val amplitude = if (position in segmentFraction) {
            val local = (position - segmentFraction.start) / (segmentFraction.endInclusive - segmentFraction.start)
            val wordIndex = (local * wordLoudness.size).toInt().coerceIn(wordLoudness.indices)
            val withinWord = (local * wordLoudness.size) - wordIndex
            // Last quarter of every word slot is the pause after it.
            if (withinWord > 0.75f) {
                0.02f + 0.03f * random.nextFloat()
            } else {
                val syllable = abs(sin(withinWord * 3.2f * PI.toFloat()))
                wordLoudness[wordIndex] * (0.25f + 0.75f * syllable) * (0.7f + 0.3f * random.nextFloat())
            }
        } else {
            0.015f + 0.035f * random.nextFloat()
        }
        maxs += amplitude
        mins += -amplitude * (0.75f + 0.25f * random.nextFloat())
    }
    return WaveformPeaks(bucketCount, mins, maxs)
}

private val previewLocalization = UiLocalization(
    provider = LocalizedStringProvider(),
    language = "en",
    config = RemoteConfig(
        schemaVersion = 1,
        configVersion = "preview",
        defaultLanguage = "en",
        strings = emptyMap(),
    ),
)

private fun previewSegment(startSample: Long, stopSample: Long, taskIndex: Int = 1) = EditorComponent.Segment(
    taskIndex = taskIndex,
    repetition = 1,
    subtype = "",
    startSample = startSample,
    stopSample = stopSample,
    initialStartSample = startSample,
    initialStopSample = stopSample,
    fileTotalSamples = 60L * PREVIEW_SAMPLE_RATE,
)

/** ~5 s of context on each side of a ~7 s utterance, matching `VISIBLE_CONTEXT_SECONDS`. */
private fun previewState(isPlaying: Boolean = false): EditorComponent.State {
    val start = 12L * PREVIEW_SAMPLE_RATE
    val stop = 19L * PREVIEW_SAMPLE_RATE
    val visibleStart = start - 5L * PREVIEW_SAMPLE_RATE
    val visibleStop = stop + 5L * PREVIEW_SAMPLE_RATE
    val range = (visibleStop - visibleStart).toFloat()
    return EditorComponent.State(
        loading = false,
        segments = listOf(
            previewSegment(start, stop),
            previewSegment(24L * PREVIEW_SAMPLE_RATE, 31L * PREVIEW_SAMPLE_RATE, taskIndex = 2),
            previewSegment(38L * PREVIEW_SAMPLE_RATE, 44L * PREVIEW_SAMPLE_RATE, taskIndex = 3),
        ),
        currentIndex = 0,
        visibleStartSample = visibleStart,
        visibleStopSample = visibleStop,
        waveform = previewPeaks(
            bucketCount = EditorComponent.WAVEFORM_BUCKET_COUNT,
            segmentFraction = (start - visibleStart) / range..(stop - visibleStart) / range,
        ),
        isPlaying = isPlaying,
        positionSample = start,
        sampleRate = PREVIEW_SAMPLE_RATE,
    )
}

@Composable
private fun EditorPreview(
    state: EditorComponent.State,
    animatePosition: Boolean = false,
    dark: Boolean = false,
) {
    val component = remember { PreviewEditorComponent(state) }
    if (animatePosition) {
        LaunchedEffect(Unit) {
            var previousFrameMs = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    val elapsed = now - previousFrameMs
                    previousFrameMs = now
                    component.advance(elapsed * PREVIEW_SAMPLE_RATE / 1000)
                }
            }
        }
    }
    ShareTheme(useDarkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            EditorContent(component = component, localization = previewLocalization)
        }
    }
}

/** The normal case: a trimmed utterance with quiet context on both sides. */
@Preview
@Composable
fun EditorIdlePreview() = EditorPreview(previewState())

/** Playing, with the position line sweeping the selection in real time. */
@Preview
@Composable
fun EditorPlayingPreview() = EditorPreview(previewState(isPlaying = true), animatePosition = true)

/** Before the peaks have been read off disk. */
@Preview
@Composable
fun EditorLoadingPreview() = EditorPreview(EditorComponent.State(loading = true))

/** A session with no reviewable VOCAL takes — only the accept path is offered. */
@Preview
@Composable
fun EditorNoSegmentsPreview() = EditorPreview(EditorComponent.State(loading = false))

/** The app runs light, but nothing on this screen may be a hard-coded light-theme color. */
@Preview
@Composable
fun EditorDarkPreview() = EditorPreview(previewState(), dark = true)
