package org.example.app.infrastructure.video

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.video.SessionVideoRecorder
import org.example.app.domain.video.VideoCaptureFormat
import org.example.app.domain.video.VideoError
import org.example.app.domain.video.VideoInputDevice
import org.example.app.domain.video.VideoRecorderState
import org.example.app.infrastructure.HostOs
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

/**
 * Production [SessionVideoRecorder]: one ffmpeg subprocess demuxes the camera and does nothing
 * else — no decoding, scaling, encoding or muxing. The PTZ Pro 2 emits MJPEG natively over USB,
 * so `-c:v copy` moves those bytes untouched and the JVM never pays for a transcode.
 *
 * Threading model, mirroring [org.example.app.infrastructure.audio.JvmContinuousSessionRecorder]:
 * a dedicated `java.lang.Thread` owns the blocking read of the process's stdout for the lifetime
 * of one capture, and never runs on an injected [CoroutineDispatchers] dispatcher. A second
 * daemon thread drains stderr. Suspend entry points hop onto [CoroutineDispatchers.io] to spawn
 * and reap the process.
 *
 * *Java*, not ffmpeg, decides which frames land in the file. That is what makes
 * [startRecording]/[stopRecording] exact: they flip a reference that the reader thread reads at
 * a frame boundary, so recording begins and ends on a whole frame with no process restart, no
 * spawn latency at the moment the examiner presses Start, and no container left unclosed if the
 * app dies mid-take.
 *
 * The reader must never block on a consumer. If it did, ffmpeg would block writing to the pipe,
 * the capture driver's real-time buffer would overflow, and frames would be dropped at the
 * *camera* — corrupting the recording rather than just the preview. Preview frames therefore go
 * into a conflated [MutableStateFlow] that a slow UI simply misses; the file gets every frame.
 */
class FfmpegSessionVideoRecorder internal constructor(
    private val dispatchers: CoroutineDispatchers,
    private val locator: FfmpegBinaryLocator = FfmpegBinaryLocator(),
    private val hostOs: HostOs = HostOs.current,
    private val requestedFormat: VideoCaptureFormat = VideoCaptureFormat.PREFERRED,
    /**
     * Builds the ffmpeg *input* arguments. Only the platform capture source varies; everything
     * after it — the mjpeg elementary-stream output, the pipe, the reader thread, the splitter
     * and the file sink — is the same code on every path, which lets tests drive the real
     * pipeline from a synthetic source instead of a camera.
     */
    private val captureInput: CaptureInput = PlatformCaptureInput(hostOs),
) : SessionVideoRecorder {

    constructor(
        dispatchers: CoroutineDispatchers,
        locator: FfmpegBinaryLocator = FfmpegBinaryLocator(),
        requestedFormat: VideoCaptureFormat = VideoCaptureFormat.PREFERRED,
    ) : this(dispatchers, locator, HostOs.current, requestedFormat, PlatformCaptureInput(HostOs.current))

    private val _state = MutableStateFlow<VideoRecorderState>(VideoRecorderState.Idle)
    override val state: StateFlow<VideoRecorderState> = _state.asStateFlow()

    private val _previewFrames = MutableStateFlow<ByteArray?>(null)
    override val previewFrames: StateFlow<ByteArray?> = _previewFrames.asStateFlow()

    private val _captureFormat = MutableStateFlow<VideoCaptureFormat?>(null)
    override val captureFormat: StateFlow<VideoCaptureFormat?> = _captureFormat.asStateFlow()

    private val _framesWritten = MutableStateFlow(0L)
    override val framesWritten: StateFlow<Long> = _framesWritten.asStateFlow()

    /** Flipped by [startRecording]/[stopRecording]; read by the reader thread per frame. */
    private val sink = AtomicReference<RecordingSink?>(null)

    @Volatile private var process: Process? = null
    @Volatile private var readerThread: Thread? = null
    private val stderrTail = StderrTail()

    /** Bytes the reader has taken off the pipe during the current attempt; see [awaitFirstFrame]. */
    private val bytesRead = AtomicLong()

    // region SessionVideoRecorder

    override suspend fun startPreview(device: VideoInputDevice) = withContext(dispatchers.io) {
        check(_state.value !is VideoRecorderState.Recording) { "cannot restart preview while recording" }
        teardown()

        val binary = locator.locate()
        if (binary == null) {
            _state.value = VideoRecorderState.Failed(VideoError.CaptureBackendUnavailable)
            return@withContext
        }
        if (!captureInput.isSupported) {
            logger.warn { "camera capture is not supported on ${HostOs.current}" }
            _state.value = VideoRecorderState.Failed(VideoError.CaptureBackendUnavailable)
            return@withContext
        }

        for (attempt in negotiationLadder(binary, device)) {
            if (tryStart(binary, device, attempt)) {
                logger.info { "capturing '${device.name}' as ${attempt.describe}" }
                return@withContext
            }
            logger.info { "'${device.name}' rejected ${attempt.describe}, trying the next mode" }
        }

        _state.value = VideoRecorderState.Failed(
            if (stderrTail.mentionsDeviceProblem()) {
                VideoError.DeviceUnavailable(device.id)
            } else {
                VideoError.CaptureStartFailed(stderrTail.snapshot())
            },
        )
    }

    override suspend fun startRecording(file: Path) = withContext(dispatchers.io) {
        check(_state.value == VideoRecorderState.Previewing) {
            "startRecording is only valid while Previewing, was ${_state.value}"
        }
        try {
            Files.createDirectories(file.parent)
            val stream = BufferedOutputStream(Files.newOutputStream(file), FILE_BUFFER_BYTES)
            _framesWritten.value = 0L
            sink.set(RecordingSink(file, stream))
            _state.value = VideoRecorderState.Recording
            logger.info { "recording video to $file" }
        } catch (e: Exception) {
            logger.error(e) { "could not open $file for video" }
            _state.value = VideoRecorderState.Failed(VideoError.DiskWriteFailed(e.message ?: e.toString()))
        }
    }

    override suspend fun stopRecording() = withContext(dispatchers.io) {
        val closed = sink.getAndSet(null) ?: return@withContext
        closed.close()
        logger.info { "stopped recording ${closed.file} after ${_framesWritten.value} frames" }
        if (_state.value == VideoRecorderState.Recording) _state.value = VideoRecorderState.Previewing
    }

    override suspend fun stop() = withContext(dispatchers.io) {
        teardown()
        _state.value = VideoRecorderState.Stopped
    }

    // endregion

    private fun tryStart(binary: Path, device: VideoInputDevice, attempt: CaptureAttempt): Boolean {
        val command = buildCommand(binary, device, attempt)
        logger.debug { "starting capture: ${command.joinToString(" ")}" }

        val started = try {
            ProcessBuilder(command).start()
        } catch (e: Exception) {
            logger.warn(e) { "could not spawn ffmpeg" }
            return false
        }
        process = started
        stderrTail.drain(started)

        bytesRead.set(0)
        val firstFrame = CountDownLatch(1)
        readerThread = Thread({ readLoop(started, firstFrame) }, "video-capture-reader").apply {
            isDaemon = true
            start()
        }

        // A camera that cannot honour the request makes ffmpeg exit within a few hundred ms;
        // a working one delivers a frame in about the same time. Wait for whichever comes
        // first rather than always burning the whole timeout, so the passthrough attempt
        // costs nothing when the camera has no MJPEG mode.
        if (awaitFirstFrame(started, firstFrame)) {
            // Resolution as ffmpeg reports the camera actually opened it, not what we asked for.
            //
            // The frame rate has to describe the *file*, because `examination.json` copies it into
            // the take's record and a bare MJPEG elementary stream carries no timestamps — it is
            // the only record of the rate the footage plays back at. Which value that is depends on
            // who chose the rate:
            //
            // - Encoding: `-r` governs it. ffmpeg duplicates or drops frames to hit the requested
            //   rate, so a camera opening at 25 while 30 was asked for still yields a 30 fps
            //   stream, and reporting the camera's 25 would play it slow.
            // - Passthrough: `-r` cannot re-time a stream it is only copying, so the file keeps the
            //   camera's own rate whatever the output banner claims. Reporting the requested rate
            //   here would play the footage fast.
            val opened = stderrTail.detectedFormat() ?: attempt.format ?: requestedFormat
            _captureFormat.value = if (attempt.passthrough) opened else opened.copy(fps = outputFps(attempt))
            _state.value = VideoRecorderState.Previewing
            return true
        }

        logger.debug { "no frames for ${attempt.describe}: ${stderrTail.snapshot()}" }
        teardown()
        return false
    }

    /**
     * Modes to try, best first.
     *
     * Requesting a `-video_size`/`-framerate` a camera cannot produce is a hard *open failure*
     * rather than a silent downgrade, so the request has to match something the device really
     * offers. The camera is asked what it supports and its own best mode is tried first; the
     * blind rungs stay on the end so an unreadable or absent listing still recovers, and the
     * last rung constrains nothing at all, which any camera satisfies.
     *
     * Each resolution is tried as MJPEG passthrough (a byte copy, no encoding anywhere on the
     * capture path) before letting ffmpeg encode it — but only where the platform can actually
     * deliver MJPEG, which AVFoundation cannot; see [CaptureInput.supportsPassthrough].
     */
    private fun negotiationLadder(binary: Path, device: VideoInputDevice): List<CaptureAttempt> {
        val advertised = probeModes(binary, device)
        if (advertised.isNotEmpty()) {
            logger.debug { "'${device.name}' advertises ${advertised.size} modes" }
        } else {
            logger.info { "'${device.name}' reported no usable mode list; falling back to fixed modes" }
        }

        val probed = rankModes(advertised, requestedFormat)
            .take(MAX_PROBED_MODES)
            .map { it.toFormat(requestedFormat.fps) }

        val blind = listOf(requestedFormat, FALLBACK_720P, FALLBACK_480P)
        val formats = (probed + blind).distinct().plus(null) // null: whatever the driver defaults to

        return formats.flatMap { format ->
            if (captureInput.supportsPassthrough) {
                listOf(CaptureAttempt(format, passthrough = true), CaptureAttempt(format, passthrough = false))
            } else {
                listOf(CaptureAttempt(format, passthrough = false))
            }
        }
    }

    /**
     * Asks the camera what it supports. Both platforms print the list only as a side effect of
     * a command that then fails, so the exit code is ignored and stderr is what matters.
     * Costs one short-lived process per session, and never throws — an unreadable listing just
     * means the blind ladder does the work.
     */
    private fun probeModes(binary: Path, device: VideoInputDevice): List<CameraMode> {
        val probe = captureInput.probeArgs(device) ?: return emptyList()
        return try {
            val output = readWithTimeout(
                command = listOf(binary.toString()) + probe,
                timeoutMs = PROBE_TIMEOUT_MS,
                what = "capture-mode probe for '${device.name}'",
            )
            captureInput.parseModes(output)
        } catch (e: Exception) {
            logger.warn(e) { "could not probe capture modes for '${device.name}'" }
            emptyList()
        }
    }

    /** True once a frame arrives; false if ffmpeg exits first or the timeout elapses. */
    private fun awaitFirstFrame(started: Process, firstFrame: CountDownLatch): Boolean {
        val deadline = System.nanoTime() + FIRST_FRAME_TIMEOUT_MS * 1_000_000
        while (System.nanoTime() < deadline) {
            if (firstFrame.await(START_POLL_MS, TimeUnit.MILLISECONDS)) return true
            // A dead process can still have buffered frames in the pipe; let the reader
            // finish draining before concluding that the attempt produced nothing.
            if (!started.isAlive) return firstFrame.await(DRAIN_GRACE_MS, TimeUnit.MILLISECONDS)
            // A rung that is streaming hard while producing no frame is not slow, it is wrong:
            // the pipe is carrying something that is not an MJPEG stream. Waiting out the rest
            // of the timeout only pushes more of it through the reader and the frame splitter,
            // so abandon it as soon as the volume proves the point.
            if (bytesRead.get() > NO_FRAME_BYTE_BUDGET) {
                logger.warn {
                    "abandoning this mode: ${bytesRead.get() / 1_048_576} MB read with no frame — " +
                        "the stream is not MJPEG"
                }
                return false
            }
        }
        return false
    }

    private fun buildCommand(binary: Path, device: VideoInputDevice, attempt: CaptureAttempt): List<String> {
        // `info`, not `warning`: the input stream line it prints is where the negotiated
        // resolution and frame rate come from. It is logged at debug and otherwise discarded.
        val command = mutableListOf(binary.toString(), "-hide_banner", "-loglevel", "info")
        command += captureInput.args(device, attempt)
        command += listOf("-an", "-map", "0:v")
        command += if (attempt.passthrough) listOf("-c:v", "copy") else listOf("-c:v", "mjpeg", "-q:v", "3")
        // Constrain the *output* rate explicitly.
        //
        // Without this ffmpeg targets the input's declared frame rate, and AVFoundation declares
        // `1000k tbr` for a camera it could not estimate a rate for ("not enough frames to
        // estimate rate"). ffmpeg then duplicates every frame to fill that rate: measured on a
        // FaceTime HD Camera, 1080p30 came out as ~1100 fps and ~20 MB/s with `dup=33349`,
        // ffmpeg alone burning nine of twelve cores. Nothing fails, so nothing is logged — the
        // app simply stops responding on the VIDEO screen.
        //
        // `-r` and not `-fpsmax`: the elementary stream carries no timestamps and the
        // processing-time remux applies the negotiated rate to it, so a constant rate is not
        // just cheaper, it is the only rate that plays back at the right speed.
        // Encoding only. A copied stream cannot be re-timed, so on the passthrough path `-r` changes
        // no bytes and merely stamps the output banner with a rate the file does not have — which
        // `parseNegotiatedFormat` would then believe if the input banner had scrolled out of the
        // retained stderr lines.
        if (!attempt.passthrough) command += listOf("-r", outputFps(attempt).toString())
        // A bare elementary stream: whole JPEGs back to back, no container to close.
        command += listOf("-f", "mjpeg", "pipe:1")
        return command
    }

    /**
     * The rate the written stream will actually have. Single source for both the `-r` argument and
     * the reported [captureFormat], so the number recorded alongside a take cannot drift from the
     * number the file was written at.
     */
    private fun outputFps(attempt: CaptureAttempt): Int = attempt.format?.fps ?: requestedFormat.fps

    /**
     * Owns the process's stdout for the lifetime of one capture. Every frame is offered to the
     * file first and the preview second, so a display that cannot keep up never costs the
     * recording a frame.
     */
    private fun readLoop(started: Process, firstFrame: CountDownLatch) {
        val splitter = MjpegFrameSplitter { buffer, offset, length ->
            sink.get()?.let { open ->
                try {
                    open.stream.write(buffer, offset, length)
                    _framesWritten.value = ++open.frames
                } catch (e: Exception) {
                    logger.error(e) { "video write failed for ${open.file}" }
                    sink.compareAndSet(open, null)
                    runCatching { open.close() }
                    _state.value = VideoRecorderState.Failed(
                        VideoError.DiskWriteFailed(e.message ?: e.toString()),
                    )
                }
            }
            _previewFrames.value = buffer.copyOfRange(offset, offset + length)
            firstFrame.countDown()
        }

        val chunk = ByteArray(READ_CHUNK_BYTES)
        try {
            started.inputStream.use { stream ->
                while (!Thread.currentThread().isInterrupted) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    bytesRead.addAndGet(read.toLong())
                    splitter.append(chunk, 0, read)
                }
            }
        } catch (e: Exception) {
            if (!Thread.currentThread().isInterrupted) {
                logger.warn(e) { "capture stream ended unexpectedly" }
            }
        }

        if (splitter.resyncCount > 0) {
            logger.warn { "capture stream needed ${splitter.resyncCount} resynchronisation(s)" }
        }

        // Reaching here while still Previewing/Recording means ffmpeg died on its own.
        val current = _state.value
        if (current == VideoRecorderState.Recording || current == VideoRecorderState.Previewing) {
            logger.error { "capture ended while $current: ${stderrTail.snapshot()}" }
            sink.getAndSet(null)?.let { runCatching { it.close() } }
            _state.value = VideoRecorderState.Failed(VideoError.CaptureInterrupted(stderrTail.snapshot()))
        }
    }

    /** Closes the file, terminates ffmpeg and joins the reader. Safe to call when idle. */
    private fun teardown() {
        sink.getAndSet(null)?.let { runCatching { it.close() } }

        val running = process
        process = null
        if (running != null && running.isAlive) {
            // 'q' is ffmpeg's graceful quit. It matters far less here than it would if ffmpeg
            // owned the output file — we closed that ourselves above — so do not wait long.
            runCatching {
                running.outputStream.write('q'.code)
                running.outputStream.write('\n'.code)
                running.outputStream.flush()
            }
            if (!running.waitFor(GRACEFUL_EXIT_MS, TimeUnit.MILLISECONDS)) {
                running.destroy()
                if (!running.waitFor(GRACEFUL_EXIT_MS, TimeUnit.MILLISECONDS)) running.destroyForcibly()
            }
        }

        readerThread?.let { thread ->
            thread.interrupt()
            thread.join(READER_JOIN_MS)
        }
        readerThread = null

        _previewFrames.value = null
        _framesWritten.value = 0L
        _captureFormat.value = null
    }

    private class RecordingSink(val file: Path, val stream: OutputStream) {
        var frames: Long = 0
        fun close() {
            stream.flush()
            stream.close()
        }
    }

    /** Keeps the last few stderr lines so a failure can say what ffmpeg actually complained about. */
    private class StderrTail {
        private val lines = ArrayDeque<String>()

        fun drain(process: Process) {
            Thread({
                runCatching {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        if (line.isNotBlank()) {
                            synchronized(lines) {
                                lines.addLast(line)
                                while (lines.size > MAX_LINES) lines.removeFirst()
                            }
                            logger.debug { "ffmpeg: $line" }
                        }
                    }
                }
            }, "video-capture-stderr").apply { isDaemon = true }.start()
        }

        fun snapshot(): String = synchronized(lines) { lines.joinToString(" | ") }

        fun detectedFormat(): VideoCaptureFormat? =
            parseNegotiatedFormat(synchronized(lines) { lines.toList() })

        fun mentionsDeviceProblem(): Boolean = snapshot().let { text ->
            DEVICE_HINTS.any { text.contains(it, ignoreCase = true) }
        }

        private companion object {
            const val MAX_LINES = 10

            val DEVICE_HINTS = listOf("could not find", "no such device", "device or resource busy", "I/O error")
        }
    }

    private companion object {
        const val READ_CHUNK_BYTES = 64 * 1024
        const val FILE_BUFFER_BYTES = 1 shl 20
        val FALLBACK_720P = VideoCaptureFormat(1280, 720, 30)
        val FALLBACK_480P = VideoCaptureFormat(640, 480, 30)
        const val FIRST_FRAME_TIMEOUT_MS = 5_000L
        const val PROBE_TIMEOUT_MS = 5_000L

        /**
         * Read volume that disproves an MJPEG stream. Comfortably more than the few hundred
         * kilobytes a real first frame takes, and small enough that a raw 1080p stream (~100 MB/s
         * measured) trips it in well under half a second.
         */
        const val NO_FRAME_BYTE_BUDGET = 32L * 1024 * 1024

        /** Enough to cover a camera's realistic best options without a long start-up walk. */
        const val MAX_PROBED_MODES = 4
        const val START_POLL_MS = 50L
        const val DRAIN_GRACE_MS = 250L
        const val GRACEFUL_EXIT_MS = 500L
        const val READER_JOIN_MS = 2_000L
    }
}
