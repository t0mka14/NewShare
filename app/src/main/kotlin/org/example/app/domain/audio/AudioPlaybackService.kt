package org.example.app.domain.audio

import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

/**
 * Example-audio and editor playback through the system output (§5.3).
 * One playback at a time; starting a new one stops the previous.
 */
interface AudioPlaybackService {
    val isPlaying: StateFlow<Boolean>

    /** Current playback position in samples of the source file (editor position line, §8.7). */
    val positionSamples: StateFlow<Long>

    fun play(file: Path)

    /** Play only [startSample, stopSample) of the file (editor segment playback). */
    fun playRange(file: Path, startSample: Long, stopSample: Long)

    fun stop()
}
