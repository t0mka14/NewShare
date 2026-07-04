package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * Thin wrapper around a `javax.sound.sampled` output line, so
 * [JvmAudioPlaybackService]'s chunking/position-tracking logic is
 * unit-testable with a fake line — no real audio hardware or output device
 * needed in tests (this codebase's CI runs headless).
 */
interface PlaybackLine {
    fun open(format: CaptureFormat)

    /** Blocking write of [length] bytes from [buffer] at [offset] (same blocking-write
     * rule as the capture side — callers must run this off any shared dispatcher). */
    fun write(buffer: ByteArray, offset: Int, length: Int): Int

    /** Blocks until all queued audio has physically played out. */
    fun drain()
    fun stop()
    fun close()
}

/** Production [PlaybackLine] over the system's default audio output (§5.3, §8.6). */
class SystemPlaybackLine : PlaybackLine {
    private var line: SourceDataLine? = null

    override fun open(format: CaptureFormat) {
        val javaFormat = format.toJavaAudioFormat()
        val info = DataLine.Info(SourceDataLine::class.java, javaFormat)
        val opened = AudioSystem.getLine(info) as SourceDataLine
        opened.open(javaFormat)
        opened.start()
        line = opened
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int): Int =
        line?.write(buffer, offset, length) ?: 0

    override fun drain() {
        line?.drain()
    }

    override fun stop() {
        line?.stop()
    }

    override fun close() {
        line?.close()
        line = null
    }
}
