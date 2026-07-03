package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import javax.sound.sampled.AudioFormat

/**
 * Pure arithmetic helpers around [CaptureFormat] — no hardware/JVM audio-line
 * dependency beyond the plain-data [AudioFormat] constructor, so these are
 * unit-testable without a real capture device.
 */

/** Bytes per sample frame (all channels, one time-slice). */
fun CaptureFormat.frameSize(): Int = channels * (bits / 8)

/** Number of complete frames represented by [byteCount] bytes of this format. */
fun CaptureFormat.framesIn(byteCount: Long): Long = byteCount / frameSize()

/** Byte count covering approximately [millis] milliseconds of audio, rounded
 * down to a whole number of frames. */
fun CaptureFormat.bytesForMillis(millis: Int): Int {
    val frame = frameSize()
    val bytesPerSecond = sampleRate.toLong() * frame
    val raw = (bytesPerSecond * millis) / 1000
    return ((raw / frame) * frame).toInt()
}

/** Milliseconds of audio represented by [byteCount] bytes of this format. */
fun CaptureFormat.millisIn(byteCount: Int): Double {
    val frames = framesIn(byteCount.toLong())
    return frames * 1000.0 / sampleRate
}

/** Maps our negotiated [CaptureFormat] to the `javax.sound.sampled` PCM format
 * used for line I/O and WAV headers: signed, little-endian. */
fun CaptureFormat.toJavaAudioFormat(): AudioFormat = AudioFormat(
    AudioFormat.Encoding.PCM_SIGNED,
    sampleRate.toFloat(),
    bits,
    channels,
    frameSize(),
    sampleRate.toFloat(),
    /* bigEndian = */ false,
)
