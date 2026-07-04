package org.example.app.fakes

import org.example.app.domain.audio.WaveformPeaks
import org.example.app.domain.audio.WaveformService
import java.nio.file.Path

/**
 * Deterministic [WaveformService] for editor/UI unit tests (§10.1, §8.7):
 * returns a flat zero waveform by default, or a scripted [WaveformPeaks] set
 * via [respondWith]. Records every [peaks] call for assertions. Real
 * downsampling/caching correctness is covered by integration tests against
 * [org.example.app.infrastructure.audio.JvmWaveformService].
 */
class FakeWaveformService : WaveformService {
    val peaksCalls = mutableListOf<PeaksCall>()
    private var scripted: WaveformPeaks? = null

    /** Test setup: subsequent [peaks] calls return [response] until changed again. */
    fun respondWith(response: WaveformPeaks) {
        scripted = response
    }

    override fun peaks(wav: Path, bucketCount: Int, startSample: Long, endSample: Long): WaveformPeaks {
        require(bucketCount > 0) { "bucketCount must be > 0, was $bucketCount" }
        require(startSample >= 0) { "startSample must be >= 0, was $startSample" }
        require(startSample < endSample) { "startSample ($startSample) must be < endSample ($endSample)" }
        peaksCalls += PeaksCall(wav, bucketCount, startSample, endSample)
        return scripted ?: WaveformPeaks(bucketCount, List(bucketCount) { 0f }, List(bucketCount) { 0f })
    }

    data class PeaksCall(val wav: Path, val bucketCount: Int, val startSample: Long, val endSample: Long)
}
