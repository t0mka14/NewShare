package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

/**
 * Seam between [JvmContinuousSessionRecorder] and `javax.sound.sampled`, so the
 * open-and-fall-back logic (§5.3.1) is testable without audio hardware.
 */
interface CaptureLineOpener {
    /** False when the device is gone or its mixer cannot be obtained. */
    fun deviceExists(deviceId: String): Boolean

    /** Cheap pre-filter only: DirectSound answers true for every format, so it never confirms one. */
    fun isLineSupported(deviceId: String, format: CaptureFormat): Boolean

    /**
     * Opens and starts a line in exactly [format]. Throws `LineUnavailableException` or
     * `IllegalArgumentException` when the device rejects the format (or is busy); a line
     * that was obtained but failed to open/start is closed before the exception propagates.
     */
    fun open(deviceId: String, format: CaptureFormat, bufferMs: Int): TargetDataLine
}

/** Production [CaptureLineOpener] over the system mixers. */
object SystemCaptureLineOpener : CaptureLineOpener {

    override fun deviceExists(deviceId: String): Boolean = try {
        MixerIds.findByStableId(deviceId)?.let { AudioSystem.getMixer(it) } != null
    } catch (e: Exception) {
        false
    }

    override fun isLineSupported(deviceId: String, format: CaptureFormat): Boolean = try {
        val info = MixerIds.findByStableId(deviceId) ?: return false
        AudioSystem.getMixer(info).isLineSupported(DataLine.Info(TargetDataLine::class.java, format.toJavaAudioFormat()))
    } catch (e: Exception) {
        false
    }

    override fun open(deviceId: String, format: CaptureFormat, bufferMs: Int): TargetDataLine {
        val mixerInfo = MixerIds.findByStableId(deviceId) ?: throw IllegalStateException("device vanished")
        val javaFormat = format.toJavaAudioFormat()
        val line = AudioSystem.getMixer(mixerInfo)
            .getLine(DataLine.Info(TargetDataLine::class.java, javaFormat)) as TargetDataLine
        try {
            line.open(javaFormat, format.bytesForMillis(bufferMs))
            line.start()
        } catch (e: Throwable) {
            try { line.close() } catch (_: Exception) { /* best-effort */ }
            throw e
        }
        return line
    }
}
