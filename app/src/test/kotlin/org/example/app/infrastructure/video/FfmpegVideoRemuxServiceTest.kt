package org.example.app.infrastructure.video

import org.example.app.domain.video.VideoRemuxException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Runs the real bundled ffmpeg. The property that matters is the one the analysis depends on:
 * the MP4 holds *the same JPEG bytes* as the take, every one of them — checked by copying the
 * frames back out of the MP4 and comparing them to the source byte for byte. A re-encode, a
 * dropped frame or a duplicated one would all change those bytes.
 */
class FfmpegVideoRemuxServiceTest {

    private val locator = FfmpegBinaryLocator()
    private lateinit var ffmpeg: Path

    @BeforeEach
    fun requireFfmpeg() {
        val found = locator.locate()
        assumeTrue(found != null, "no ffmpeg available; skipping")
        ffmpeg = found!!
    }

    private fun run(vararg args: String) {
        val process = ProcessBuilder(listOf(ffmpeg.toString(), "-hide_banner", "-loglevel", "error", "-y") + args)
            .redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().decodeToString()
        check(process.waitFor() == 0) { "ffmpeg ${args.joinToString(" ")} failed: $output" }
    }

    /** [frames] distinct JPEGs back to back — the shape of a recorded take. */
    private fun syntheticTake(dir: Path, frames: Int): Path {
        val take = dir.resolve("take.mjpeg")
        run("-f", "lavfi", "-i", "testsrc=size=320x240:rate=30", "-frames:v", "$frames", "-c:v", "mjpeg", "-q:v", "3", "-f", "mjpeg", "$take")
        return take
    }

    /** The JPEG stream inside [mp4], copied back out without decoding. */
    private fun framesOf(mp4: Path, dir: Path): ByteArray {
        val back = dir.resolve("back.mjpeg")
        run("-i", "$mp4", "-map", "0:v", "-c", "copy", "-f", "mjpeg", "$back")
        return Files.readAllBytes(back)
    }

    @Test
    fun `the MP4 holds every frame of the take, byte for byte`(@TempDir dir: Path) {
        val take = syntheticTake(dir, frames = 45)
        val mp4 = dir.resolve("clips/HC001_0_PHONATION_1.mp4")

        FfmpegVideoRemuxService(locator).remuxToMp4(take, fps = 30, output = mp4)

        assertTrue(Files.size(mp4) > 0)
        assertArrayEquals(Files.readAllBytes(take), framesOf(mp4, dir))
        assertFalse(Files.exists(dir.resolve("clips/HC001_0_PHONATION_1.mp4.tmp.mp4")), "no temp file left behind")
    }

    /** A crash mid-write leaves half a JPEG; it must be cut off rather than kept as a corrupt frame. */
    @Test
    fun `a truncated final frame is dropped and the complete ones are kept intact`(@TempDir dir: Path) {
        val take = syntheticTake(dir, frames = 10)
        val complete = Files.readAllBytes(take)
        val firstFrameLength = complete.size / 10
        Files.write(take, complete + complete.copyOfRange(0, firstFrameLength / 2))
        val mp4 = dir.resolve("out.mp4")

        FfmpegVideoRemuxService(locator).remuxToMp4(take, fps = 30, output = mp4)

        assertArrayEquals(complete, framesOf(mp4, dir))
    }

    @Test
    fun `a take with no complete frame is refused`(@TempDir dir: Path) {
        val take = dir.resolve("broken.mjpeg")
        Files.write(take, ByteArray(4096) { 0x42 })

        assertThrows<VideoRemuxException> {
            FfmpegVideoRemuxService(locator).remuxToMp4(take, fps = 30, output = dir.resolve("out.mp4"))
        }
        assertFalse(Files.exists(dir.resolve("out.mp4")))
    }
}
