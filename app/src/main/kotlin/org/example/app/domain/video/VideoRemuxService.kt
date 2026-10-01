package org.example.app.domain.video

import java.nio.file.Path

/**
 * Wraps a recorded take — a bare MJPEG elementary stream, which nothing but ffmpeg plays — into a
 * playable MP4 during processing (§8.8). A domain port like
 * [org.example.app.domain.audio.AudioClipService], so `ProcessSessionUseCase` stays testable with
 * a fake.
 *
 * Never re-encodes: the frames are what downstream analysis (cheek movement) measures, so every
 * JPEG is copied into the container byte for byte, none dropped and none duplicated.
 */
interface VideoRemuxService {
    /**
     * Writes [output] (atomically) holding exactly the frames of [source], timed at [fps] — the
     * take's `videoTakes[].captureFormat.fps`, since the stream itself carries no timestamps.
     *
     * @throws VideoRemuxException when no ffmpeg is available, ffmpeg fails, or the MP4 does not
     *   hold the same number of frames as [source].
     */
    fun remuxToMp4(source: Path, fps: Int, output: Path)
}

class VideoRemuxException(val detail: String) : Exception(detail)
