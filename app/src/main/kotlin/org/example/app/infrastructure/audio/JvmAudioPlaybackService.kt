package org.example.app.infrastructure.audio

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.app.domain.audio.AudioPlaybackService
import org.example.app.domain.audio.CaptureFormat
import java.io.RandomAccessFile
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Production [AudioPlaybackService] (§5.3, §8.6, §8.7) over a [PlaybackLine].
 * Example-audio and editor segment playback.
 *
 * Threading model: [play]/[playRange] are non-suspend, fire-and-forget entry
 * points (per the frozen port signature) that start a dedicated
 * `java.lang.Thread` doing the blocking `PlaybackLine.write()` loop — the
 * same rule as the capture side (`JvmContinuousSessionRecorder`): blocking
 * I/O never runs on a shared coroutine dispatcher. Since every method here is
 * already synchronous (no suspend functions to hop dispatchers from), there
 * is no [org.example.app.domain.CoroutineDispatchers] dependency to inject —
 * unlike the recorder, which uses it for suspend `open`/`close` calls and a
 * watchdog coroutine.
 *
 * One playback at a time: starting a new [play]/[playRange] stops whatever
 * was previously playing (port contract). [positionSamples] tracks samples
 * *handed to the line*, not physically-sounding samples — the same bounded
 * skew the recorder documents for `writtenSamples` (§5.3.1), accepted here
 * for the same reason: the moving position line only needs to be
 * editor-frame-accurate, not output-latency-exact.
 */
class JvmAudioPlaybackService(
    private val lineFactory: () -> PlaybackLine = { SystemPlaybackLine() },
) : AudioPlaybackService {

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionSamples = MutableStateFlow(0L)
    override val positionSamples: StateFlow<Long> = _positionSamples.asStateFlow()

    @Volatile private var playbackThread: Thread? = null
    @Volatile private var currentLine: PlaybackLine? = null

    override fun play(file: Path) {
        // Fire-and-forget contract (class doc): a missing/corrupt file must degrade to a logged
        // no-op, never an exception on the calling (UI) thread.
        val header = try {
            WavIo.readHeader(file)
        } catch (e: Exception) {
            logger.warn(e) { "cannot start playback for $file" }
            return
        }
        val totalFrames = header.format.framesIn(header.dataSize)
        startPlayback(file, header.format, 0L, totalFrames)
    }

    override fun playRange(file: Path, startSample: Long, stopSample: Long) {
        try {
            require(startSample >= 0) { "startSample must be >= 0, was $startSample" }
            require(startSample < stopSample) { "startSample ($startSample) must be < stopSample ($stopSample)" }
            val header = WavIo.readHeader(file)
            val totalFrames = header.format.framesIn(header.dataSize)
            require(stopSample <= totalFrames) {
                "range [$startSample, $stopSample) exceeds frame count $totalFrames for $file"
            }
            startPlayback(file, header.format, startSample, stopSample)
        } catch (e: Exception) {
            logger.warn(e) { "cannot start range playback for $file" }
        }
    }

    override fun stop() {
        stopPlaybackThreadAndLine()
        _isPlaying.value = false
    }

    private fun startPlayback(file: Path, format: CaptureFormat, startFrame: Long, stopFrame: Long) {
        stopPlaybackThreadAndLine() // port contract: starting a new playback stops the previous one
        _positionSamples.value = startFrame
        _isPlaying.value = true

        val line = lineFactory()
        currentLine = line
        val thread = Thread({ playbackLoop(file, format, startFrame, stopFrame, line) }, "audio-playback")
        thread.isDaemon = true
        playbackThread = thread
        thread.start()
    }

    private fun playbackLoop(file: Path, format: CaptureFormat, startFrame: Long, stopFrame: Long, line: PlaybackLine) {
        try {
            line.open(format)
            val frameSize = format.frameSize()
            val chunkFrames = (format.bytesForMillis(CHUNK_MS).coerceAtLeast(frameSize) / frameSize).coerceAtLeast(1)
            RandomAccessFile(file.toFile(), "r").use { raf ->
                raf.seek(WavHeader.HEADER_SIZE.toLong() + startFrame * frameSize)
                val buffer = ByteArray(chunkFrames * frameSize)
                var frame = startFrame
                while (frame < stopFrame && !Thread.currentThread().isInterrupted) {
                    val framesToRead = minOf(chunkFrames.toLong(), stopFrame - frame).toInt()
                    val bytesToRead = framesToRead * frameSize
                    raf.readFully(buffer, 0, bytesToRead)
                    line.write(buffer, 0, bytesToRead)
                    frame += framesToRead
                    _positionSamples.value = frame
                }
                if (!Thread.currentThread().isInterrupted) line.drain()
            }
        } catch (e: InterruptedException) {
            // expected on stop() — the blocking line.write()/read may throw this
        } catch (e: Exception) {
            logger.warn(e) { "playback failed for $file" }
        } finally {
            try {
                line.stop()
                line.close()
            } catch (e: Exception) {
                logger.warn(e) { "error closing playback line" }
            }
            _isPlaying.value = false
        }
    }

    private fun stopPlaybackThreadAndLine() {
        val thread = playbackThread
        playbackThread = null
        val line = currentLine
        currentLine = null
        try {
            line?.stop()
            line?.close()
        } catch (e: Exception) {
            logger.warn(e) { "error closing playback line on stop" }
        }
        thread?.interrupt()
        try {
            thread?.join(THREAD_JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            // best-effort join
        }
    }

    private companion object {
        const val CHUNK_MS = 100
        const val THREAD_JOIN_TIMEOUT_MS = 2000L
    }
}
