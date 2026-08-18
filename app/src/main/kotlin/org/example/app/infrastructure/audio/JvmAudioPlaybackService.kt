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
 * the output device has **physically rendered** ([PlaybackLine.framePosition]),
 * not samples handed to the line: writes return immediately until the line's
 * buffer is full, so a written-frame count leaps ahead of the sound by the
 * whole buffer depth (0.25-0.5 s on typical devices) and stays there. The
 * editor draws its position line straight onto the waveform (§8.7), where that
 * skew is plainly visible, so the accurate clock is worth the extra call.
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
                    // Once the line's buffer is full each write returns after roughly CHUNK_MS of
                    // real time, so this publishes the rendered position ~50x/s. While the buffer
                    // is still filling the writes return instantly and the position correctly
                    // stays parked at startFrame — the sound has not begun yet.
                    publishPosition(line, startFrame, stopFrame, writtenFrame = frame)
                }
                if (!Thread.currentThread().isInterrupted) {
                    drainTracking(line, startFrame, stopFrame)
                }
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

    /**
     * Publishes the position the device is actually sounding. [writtenFrame] is only the fallback
     * for lines with no position clock (test fakes) — see [PlaybackLine.framePosition].
     */
    private fun publishPosition(line: PlaybackLine, startFrame: Long, stopFrame: Long, writtenFrame: Long) {
        val rendered = line.framePosition()
        _positionSamples.value = if (rendered >= 0) {
            (startFrame + rendered).coerceIn(startFrame, stopFrame)
        } else {
            writtenFrame
        }
    }

    /**
     * Plays out the buffered tail while keeping [positionSamples] moving. A bare
     * `PlaybackLine.drain()` blocks silently, which used to pin the editor's position line at the
     * segment end for the last buffer's worth of audio.
     */
    private fun drainTracking(line: PlaybackLine, startFrame: Long, stopFrame: Long) {
        if (line.framePosition() < 0) {
            line.drain()
            return
        }
        val totalFrames = stopFrame - startFrame
        while (!Thread.currentThread().isInterrupted && line.framePosition() < totalFrames) {
            publishPosition(line, startFrame, stopFrame, writtenFrame = stopFrame)
            Thread.sleep(POSITION_TICK_MS)
        }
        if (!Thread.currentThread().isInterrupted) {
            publishPosition(line, startFrame, stopFrame, writtenFrame = stopFrame)
            line.drain()
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
        const val CHUNK_MS = 20
        /** ~60 Hz position updates while the buffered tail plays out. */
        const val POSITION_TICK_MS = 16L
        const val THREAD_JOIN_TIMEOUT_MS = 2000L
    }
}
