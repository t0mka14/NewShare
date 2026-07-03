package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Canonical 44-byte PCM WAV header (§8.4): build/patch bytes for a given
 * [CaptureFormat] + data size, and parse them back for recovery/testing. Pure
 * byte-array logic — no file I/O — so it is unit-testable without a temp dir.
 */
object WavHeader {
    const val HEADER_SIZE = 44
    private const val PCM_FMT_CHUNK_SIZE = 16
    private const val PCM_AUDIO_FORMAT = 1

    /** Builds a 44-byte canonical WAV header for [format] with [dataSize] bytes of PCM data. */
    fun build(format: CaptureFormat, dataSize: Long): ByteArray {
        require(dataSize >= 0) { "dataSize must be >= 0, was $dataSize" }
        val byteRate = format.sampleRate * format.channels * (format.bits / 8)
        val blockAlign = format.channels * (format.bits / 8)
        val buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt((36 + dataSize).toInt())
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(PCM_FMT_CHUNK_SIZE)
        buffer.putShort(PCM_AUDIO_FORMAT.toShort())
        buffer.putShort(format.channels.toShort())
        buffer.putInt(format.sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(format.bits.toShort())

        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize.toInt())

        return buffer.array()
    }

    /** Parsed view of a WAV header, used by tests and (eventually) recovery. */
    data class Parsed(
        val sampleRate: Int,
        val bits: Int,
        val channels: Int,
        val dataSize: Long,
        val riffChunkSize: Long,
    ) {
        val format: CaptureFormat get() = CaptureFormat(sampleRate, bits, channels)
    }

    /** Parses a 44-byte canonical PCM WAV header. Throws [IllegalArgumentException]
     * if the bytes are not a well-formed RIFF/WAVE/fmt /data header. */
    fun parse(bytes: ByteArray): Parsed {
        require(bytes.size >= HEADER_SIZE) { "header too short: ${bytes.size} bytes" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val riff = ByteArray(4).also { buffer.get(it) }
        require(String(riff, Charsets.US_ASCII) == "RIFF") { "not a RIFF file" }
        val riffChunkSize = buffer.int.toLong() and 0xFFFFFFFFL

        val wave = ByteArray(4).also { buffer.get(it) }
        require(String(wave, Charsets.US_ASCII) == "WAVE") { "not a WAVE file" }

        val fmt = ByteArray(4).also { buffer.get(it) }
        require(String(fmt, Charsets.US_ASCII) == "fmt ") { "missing fmt chunk" }
        val fmtChunkSize = buffer.int
        require(fmtChunkSize >= PCM_FMT_CHUNK_SIZE) { "unexpected fmt chunk size: $fmtChunkSize" }
        val audioFormat = buffer.short.toInt()
        require(audioFormat == PCM_AUDIO_FORMAT) { "not PCM (audioFormat=$audioFormat)" }
        val channels = buffer.short.toInt()
        val sampleRate = buffer.int
        buffer.int // byteRate, derivable
        buffer.short // blockAlign, derivable
        val bits = buffer.short.toInt()
        if (fmtChunkSize > PCM_FMT_CHUNK_SIZE) {
            buffer.position(buffer.position() + (fmtChunkSize - PCM_FMT_CHUNK_SIZE))
        }

        val data = ByteArray(4).also { buffer.get(it) }
        require(String(data, Charsets.US_ASCII) == "data") { "missing data chunk" }
        val dataSize = buffer.int.toLong() and 0xFFFFFFFFL

        return Parsed(sampleRate, bits, channels, dataSize, riffChunkSize)
    }
}
