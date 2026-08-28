package org.example.app.infrastructure.video

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * The bound on a short-lived ffmpeg query. Worth its own test because the version this replaced
 * *looked* bounded — it passed a timeout to `waitFor` — while reading the output to end of stream
 * first, so the timeout could never be reached. Camera enumeration is called on the Swing EDT, so
 * that made an unbounded UI freeze one uncooperative child process away.
 */
class FfmpegProcessOutputTest {

    private val binary = FfmpegBinaryLocator().locate()

    private fun assumeFfmpegAvailable() {
        assumeTrue(binary != null, "no ffmpeg binary; run :app:unpackVideoNatives")
    }

    /** A capture that never ends: without a reachable timeout this call would never return. */
    @Test
    fun `returns within the timeout even when the process never closes its output`() {
        assumeFfmpegAvailable()

        val elapsed = measureTime {
            readWithTimeout(
                command = listOf(
                    binary.toString(), "-hide_banner", "-loglevel", "quiet",
                    "-f", "lavfi", "-i", "testsrc=size=64x64:rate=30",
                    "-c:v", "mjpeg", "-f", "mjpeg", "pipe:1",
                ),
                timeoutMs = 500,
                what = "never-ending query",
            )
        }

        assertTrue(elapsed < 10.seconds, "should have been abandoned after ~0.5s, took $elapsed")
    }

    @Test
    fun `returns what a query printed before exiting`() {
        assumeFfmpegAvailable()

        val output = readWithTimeout(
            command = listOf(binary.toString(), "-hide_banner", "-version"),
            timeoutMs = 10_000,
            what = "version query",
        )

        assertTrue(output.contains("ffmpeg version"), "unexpected output: $output")
    }

    /**
     * Both real callers read a listing that ffmpeg prints on its way to failing, so a non-zero
     * exit must still yield the text rather than being treated as no answer.
     */
    @Test
    fun `keeps the output of a query that exits non-zero`() {
        assumeFfmpegAvailable()

        val output = readWithTimeout(
            command = listOf(binary.toString(), "-hide_banner", "-i", "no-such-input-file.mkv"),
            timeoutMs = 10_000,
            what = "failing query",
        )

        assertTrue(output.isNotBlank(), "ffmpeg's complaint is the payload here")
    }
}
