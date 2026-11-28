package org.example.app.infrastructure.audio

import org.example.app.domain.audio.AudioClipService
import org.example.app.domain.audio.CaptureFormat
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Production [AudioClipService] (§8.5, §8.8): sample-accurate cutting,
 * format conversion, and concatenation of WAV master (parts) using
 * [WavIo] for reads and [AudioConversion] for the PCM16 resample/downmix
 * math. Every output is written to a `.tmp` sibling and atomically renamed
 * into place, so a crash mid-export never leaves a half-written clip at the
 * target path.
 *
 * Stateless and safe to share across sessions — no session-scoped fields.
 */
class JvmAudioClipService : AudioClipService {

    override fun cutClip(sourceWav: Path, keepRanges: List<LongRange>, targetFormat: CaptureFormat, output: Path) {
        require(keepRanges.isNotEmpty()) { "keepRanges must not be empty" }
        val header = WavIo.readHeader(sourceWav)
        val sourceFormat = header.format
        val totalFrames = sourceFormat.framesIn(header.dataSize)
        var previousEnd = 0L // exclusive end of the previous range
        for (r in keepRanges) {
            require(r.first >= 0) { "range start must be >= 0, was ${r.first}" }
            require(r.first <= r.last) { "range $r is empty" }
            require(r.first >= previousEnd) { "ranges must be ascending and non-overlapping, but $r starts before $previousEnd" }
            require(r.last + 1 <= totalFrames) {
                "range [${r.first}, ${r.last + 1}) exceeds source frame count $totalFrames for $sourceWav"
            }
            previousEnd = r.last + 1
        }

        val pieces = keepRanges.map { r ->
            val count = r.last - r.first + 1
            val raw = WavIo.readFrameRange(sourceWav, sourceFormat, r.first, count)
            convertPcm(raw, count, sourceFormat, targetFormat)
        }
        val joined = ByteArray(pieces.sumOf { it.size })
        var offset = 0
        for (piece in pieces) {
            piece.copyInto(joined, offset)
            offset += piece.size
        }
        writeWav(output, targetFormat, joined)
    }

    override fun nearestZeroCrossing(sourceWav: Path, sample: Long, maxRadiusSamples: Long): Long {
        require(maxRadiusSamples >= 0) { "maxRadiusSamples must be >= 0, was $maxRadiusSamples" }
        val header = WavIo.readHeader(sourceWav)
        val format = header.format
        require(format.bits == 16) { "nearestZeroCrossing supports 16-bit PCM only (was ${format.bits}-bit)" }
        val totalFrames = format.framesIn(header.dataSize)
        if (totalFrames == 0L) return sample

        val lo = (sample - maxRadiusSamples).coerceAtLeast(0)
        val hi = (sample + maxRadiusSamples).coerceAtMost(totalFrames - 1)
        if (lo > hi) return sample

        // Read one extra frame before the window so a crossing exactly at `lo` is detectable.
        val readStart = if (lo > 0) lo - 1 else 0L
        val bytes = WavIo.readFrameRange(sourceWav, format, readStart, hi - readStart + 1)
        val channels = format.channels
        val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        fun mono(frame: Long): Int {
            var sum = 0
            val base = ((frame - readStart) * channels).toInt() * 2
            for (c in 0 until channels) sum += bb.getShort(base + c * 2).toInt()
            return sum / channels
        }

        var best = -1L
        var bestDist = Long.MAX_VALUE
        for (i in lo..hi) {
            val cur = mono(i)
            var crossing = cur == 0
            if (!crossing && i > readStart) {
                val prev = mono(i - 1)
                crossing = (prev < 0 && cur > 0) || (prev > 0 && cur < 0)
            }
            if (!crossing) continue
            val dist = kotlin.math.abs(i - sample)
            if (dist < bestDist) { // ascending scan: first hit at equal distance is the earlier index
                best = i
                bestDist = dist
            }
        }
        return if (best >= 0) best else sample
    }

    override fun convert(sourceWav: Path, targetFormat: CaptureFormat, output: Path) {
        val header = WavIo.readHeader(sourceWav)
        val sourceFormat = header.format
        val totalFrames = sourceFormat.framesIn(header.dataSize)

        val raw = WavIo.readFrameRange(sourceWav, sourceFormat, 0, totalFrames)
        val converted = convertPcm(raw, totalFrames, sourceFormat, targetFormat)
        writeWav(output, targetFormat, converted)
    }

    override fun concatenate(sources: List<Path>, targetFormat: CaptureFormat, output: Path) {
        require(sources.isNotEmpty()) { "concatenate requires at least one source" }
        val headers = sources.map { src ->
            val header = WavIo.readHeader(src)
            require(header.format == targetFormat) {
                "source $src is in format ${header.format}, expected $targetFormat " +
                    "— convert() each part to the target format before concatenating (§8.5)"
            }
            header
        }

        output.parent?.let { Files.createDirectories(it) }
        val tmp = tmpSiblingOf(output)
        var totalDataBytes = 0L
        RandomAccessFile(tmp.toFile(), "rw").use { raf ->
            raf.setLength(0)
            raf.write(WavHeader.build(targetFormat, 0L)) // placeholder, patched below once the real size is known
            for ((index, src) in sources.withIndex()) {
                val header = headers[index]
                RandomAccessFile(src.toFile(), "r").use { srcRaf ->
                    srcRaf.seek(WavHeader.HEADER_SIZE.toLong())
                    val buf = ByteArray(COPY_BUFFER_BYTES)
                    var remaining = header.dataSize
                    while (remaining > 0) {
                        val n = srcRaf.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        remaining -= n
                        totalDataBytes += n
                    }
                }
            }
            raf.seek(0)
            raf.write(WavHeader.build(targetFormat, totalDataBytes))
            raf.fd.sync()
        }
        Files.move(tmp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Downmixes (if channel counts differ) then resamples (if rates differ) [raw] PCM16
     * data from [source] to [target]. A no-op copy when the formats already match. */
    private fun convertPcm(raw: ByteArray, frameCount: Long, source: CaptureFormat, target: CaptureFormat): ByteArray {
        require(source.bits == 16 && target.bits == 16) {
            "AudioConversion supports 16-bit PCM only (source=${source.bits}-bit, target=${target.bits}-bit)"
        }
        var data = raw
        var channels = source.channels
        if (channels != target.channels) {
            require(target.channels == 1) {
                "only downmixing to mono is supported (target requested ${target.channels} channels)"
            }
            data = AudioConversion.downmixToMono(data, frameCount, channels)
            channels = 1
        }
        if (source.sampleRate != target.sampleRate) {
            data = AudioConversion.resampleLinear(data, frameCount, channels, source.sampleRate, target.sampleRate)
        }
        return data
    }

    private fun writeWav(output: Path, format: CaptureFormat, data: ByteArray) {
        output.parent?.let { Files.createDirectories(it) }
        val tmp = tmpSiblingOf(output)
        RandomAccessFile(tmp.toFile(), "rw").use { raf ->
            raf.setLength(0)
            raf.write(WavHeader.build(format, data.size.toLong()))
            raf.write(data)
            raf.fd.sync()
        }
        Files.move(tmp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun tmpSiblingOf(path: Path): Path = path.resolveSibling(path.fileName.toString() + ".tmp")

    private companion object {
        const val COPY_BUFFER_BYTES = 1 shl 20 // 1 MiB
    }
}
