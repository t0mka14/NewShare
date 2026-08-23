package org.example.app.domain.video

import kotlinx.serialization.Serializable

/**
 * Requested capture format for a VIDEO task. Persisted alongside the recording so the
 * frame rate assumed at remux time is recoverable (a bare MJPEG elementary stream carries
 * no timestamps of its own).
 */
@Serializable
data class VideoCaptureFormat(
    val width: Int,
    val height: Int,
    val fps: Int,
) {
    companion object {
        /** What the PTZ Pro 2 delivers natively over USB, so `-c:v copy` is a byte copy. */
        val PREFERRED = VideoCaptureFormat(width = 1920, height = 1080, fps = 30)
    }
}
