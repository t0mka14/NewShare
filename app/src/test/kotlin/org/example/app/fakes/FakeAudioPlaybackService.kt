package org.example.app.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.app.domain.audio.AudioPlaybackService
import java.nio.file.Path

/**
 * Records every play/playRange/stop call for assertions (example-audio playback,
 * editor segment playback with a moving position line, §8.7).
 */
class FakeAudioPlaybackService : AudioPlaybackService {
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionSamples = MutableStateFlow(0L)
    override val positionSamples: StateFlow<Long> = _positionSamples.asStateFlow()

    val playCalls = mutableListOf<Path>()
    val playRangeCalls = mutableListOf<PlayRangeCall>()
    var stopCallCount = 0
        private set

    override fun play(file: Path) {
        // NB: java.nio.file.Path implements Iterable<Path> (its path segments), which
        // makes `list += path` ambiguous between plusAssign(T) and plusAssign(Iterable<T>)
        // — use .add(...) explicitly to avoid it.
        playCalls.add(file)
        _positionSamples.value = 0L
        _isPlaying.value = true
    }

    override fun playRange(file: Path, startSample: Long, stopSample: Long) {
        require(startSample < stopSample) { "startSample ($startSample) must be < stopSample ($stopSample)" }
        playRangeCalls += PlayRangeCall(file, startSample, stopSample)
        _positionSamples.value = startSample
        _isPlaying.value = true
    }

    override fun stop() {
        stopCallCount++
        _isPlaying.value = false
    }

    /** Test-only: simulate the position line advancing during playback. */
    fun setPosition(sample: Long) {
        _positionSamples.value = sample
    }

    data class PlayRangeCall(val file: Path, val startSample: Long, val stopSample: Long)
}
