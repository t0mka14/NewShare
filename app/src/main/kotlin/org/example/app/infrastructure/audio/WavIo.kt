package org.example.app.infrastructure.audio

import java.io.RandomAccessFile
import java.nio.file.Path

/**
 * Shared read-only WAV file access for [JvmAudioClipService] and
 * [JvmWaveformService]: parses the 44-byte header and reads arbitrary
 * frame ranges out of the `data` chunk. No write path here — writing goes
 * through [WavFileWriter] (live capture) or the one-shot writers in
 * `JvmAudioClipService` (processed output).
 */
internal object WavIo {

    /** Parses the header of [wav] without reading its data chunk. */
    fun readHeader(wav: Path): WavHeader.Parsed {
        RandomAccessFile(wav.toFile(), "r").use { raf ->
            val bytes = ByteArray(WavHeader.HEADER_SIZE)
            raf.readFully(bytes)
            return WavHeader.parse(bytes)
        }
    }

    /** Reads [frameCount] frames of raw PCM starting at [startFrame] (frame indices in [format]). */
    fun readFrameRange(wav: Path, format: org.example.app.domain.audio.CaptureFormat, startFrame: Long, frameCount: Long): ByteArray {
        val frameSize = format.frameSize()
        val byteOffset = startFrame * frameSize
        val byteLength = frameCount * frameSize
        RandomAccessFile(wav.toFile(), "r").use { raf ->
            raf.seek(WavHeader.HEADER_SIZE.toLong() + byteOffset)
            val buf = ByteArray(byteLength.toInt())
            raf.readFully(buf)
            return buf
        }
    }
}
