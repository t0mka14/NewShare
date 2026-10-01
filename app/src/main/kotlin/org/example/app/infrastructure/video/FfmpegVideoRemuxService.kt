package org.example.app.infrastructure.video

import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.app.domain.video.VideoRemuxException
import org.example.app.domain.video.VideoRemuxService
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val logger = KotlinLogging.logger {}

/**
 * [VideoRemuxService] over the bundled ffmpeg, by stream copy:
 * `-f mjpeg -framerate <fps> -i <take> -c:v copy out.mp4`. ffmpeg only wraps each JPEG in the
 * container and stamps it `n / fps`; it never decodes, so the MP4's frames are the camera's own
 * bytes.
 *
 * The frame count is checked independently afterwards — [MjpegFrameSplitter] over the source
 * against ffmpeg's packet count of the MP4 — because the frames are the data, and a short copy
 * must fail processing rather than ship.
 *
 * A take cut short by a crash can end in half a JPEG. `-frames:v` stops the copy at the last
 * complete frame, so that tail is dropped instead of becoming a corrupt final frame.
 */
class FfmpegVideoRemuxService(
    private val locator: FfmpegBinaryLocator = FfmpegBinaryLocator(),
) : VideoRemuxService {

    override fun remuxToMp4(source: Path, fps: Int, output: Path) {
        val ffmpeg = locator.locate() ?: throw VideoRemuxException("no ffmpeg found")
        if (fps <= 0) throw VideoRemuxException("invalid frame rate $fps for $source")

        val sourceFrames = countSourceFrames(source)
        if (sourceFrames == 0) throw VideoRemuxException("$source holds no complete frame")

        Files.createDirectories(output.toAbsolutePath().parent)
        // `.tmp.mp4` rather than `.tmp`: ffmpeg picks the muxer from the extension.
        val temp = output.resolveSibling("${output.fileName}.tmp.mp4")
        try {
            val remuxLog = readWithTimeout(
                listOf(
                    ffmpeg.toString(), "-hide_banner", "-nostdin", "-y",
                    "-f", "mjpeg", "-framerate", fps.toString(), "-i", source.toString(),
                    "-map", "0:v", "-c:v", "copy", "-frames:v", sourceFrames.toString(),
                    "-movflags", "+faststart", temp.toString(),
                ),
                timeoutFor(source),
                "video remux of ${source.fileName}",
            )
            if (!Files.isRegularFile(temp) || Files.size(temp) == 0L) {
                throw VideoRemuxException("ffmpeg wrote no output for $source: ${remuxLog.takeLast(LOG_TAIL)}")
            }

            val mp4Frames = countMp4Frames(ffmpeg, temp)
            if (mp4Frames != sourceFrames) {
                throw VideoRemuxException(
                    "frame count mismatch for $source: source $sourceFrames, mp4 ${mp4Frames ?: "unreadable"}",
                )
            }

            Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            logger.info { "remuxed ${source.fileName} -> ${output.fileName}: $sourceFrames frames at $fps fps" }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    /** Complete JPEGs in [source], streamed through the same splitter capture uses. */
    private fun countSourceFrames(source: Path): Int {
        var frames = 0
        val splitter = MjpegFrameSplitter { _, _, _ -> frames++ }
        Files.newInputStream(source).use { input ->
            val chunk = ByteArray(READ_CHUNK_BYTES)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                splitter.append(chunk, 0, read)
            }
        }
        if (splitter.pendingBytes > 0) {
            logger.warn { "${source.fileName} ends in ${splitter.pendingBytes} bytes of an incomplete frame; dropping them" }
        }
        if (splitter.resyncCount > 0) {
            logger.warn { "${source.fileName} needed ${splitter.resyncCount} resyncs; the frame check will tell if ffmpeg agrees" }
        }
        return frames
    }

    /** Video packets ffmpeg reads back out of [mp4], or null when it cannot read the file. */
    private fun countMp4Frames(ffmpeg: Path, mp4: Path): Int? {
        val log = readWithTimeout(
            listOf(ffmpeg.toString(), "-hide_banner", "-nostdin", "-i", mp4.toString(), "-map", "0:v", "-c", "copy", "-f", "null", "-"),
            timeoutFor(mp4),
            "frame count of ${mp4.fileName}",
        )
        return FRAME_PROGRESS.findAll(log).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
    }

    /** A copy runs at disk speed, but a long take is still a large file, so the bound grows with it. */
    private fun timeoutFor(file: Path): Long =
        BASE_TIMEOUT_MS + Files.size(file) / BYTES_PER_EXTRA_SECOND * 1000

    private companion object {
        const val READ_CHUNK_BYTES = 1 shl 20
        const val BASE_TIMEOUT_MS = 60_000L
        const val BYTES_PER_EXTRA_SECOND = 20L * 1024 * 1024
        const val LOG_TAIL = 500
        val FRAME_PROGRESS = Regex("""frame=\s*(\d+)""")
    }
}
