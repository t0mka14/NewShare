package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test-only WAV synthesis/read-back with known PCM16 sample values, shared by
 * the cutting/conversion/waveform test suites (§10.1/§10.2: assert exact
 * samples, not just "it ran").
 */

internal fun writeTestWav(path: Path, format: CaptureFormat, interleavedSamples: ShortArray) {
    require(format.bits == 16) { "test fixture only synthesizes 16-bit PCM" }
    path.parent?.let { Files.createDirectories(it) }
    val data = ByteArray(interleavedSamples.size * 2)
    for (i in interleavedSamples.indices) {
        val v = interleavedSamples[i].toInt()
        data[i * 2] = (v and 0xFF).toByte()
        data[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
    }
    RandomAccessFile(path.toFile(), "rw").use { raf ->
        raf.setLength(0)
        raf.write(WavHeader.build(format, data.size.toLong()))
        raf.write(data)
    }
}

internal fun readTestWavSamples(path: Path): Pair<CaptureFormat, ShortArray> {
    val bytes = Files.readAllBytes(path)
    val header = WavHeader.parse(bytes)
    val dataBytes = bytes.copyOfRange(WavHeader.HEADER_SIZE, WavHeader.HEADER_SIZE + header.dataSize.toInt())
    val samples = ShortArray(dataBytes.size / 2)
    for (i in samples.indices) {
        val lo = dataBytes[i * 2].toInt() and 0xFF
        val hi = dataBytes[i * 2 + 1].toInt() and 0xFF
        samples[i] = ((hi shl 8) or lo).toShort()
    }
    return header.format to samples
}

/** A mono ramp 0, step, 2*step, ... wrapping through Short range — deterministic,
 * easy to hand-verify at any index. */
internal fun monoRamp(frameCount: Int, step: Int = 100): ShortArray =
    ShortArray(frameCount) { i -> ((i * step) % 65536 - 32768).toShort() }
