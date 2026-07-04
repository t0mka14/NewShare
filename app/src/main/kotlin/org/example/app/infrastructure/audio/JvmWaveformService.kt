package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.audio.WaveformPeaks
import org.example.app.domain.audio.WaveformService
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.math.ceil
import kotlin.math.min

/**
 * Production [WaveformService] (§8.7). Peaks are computed at a fixed "base"
 * resolution (10 ms buckets) once per WAV file, cached to disk under a
 * `waveform_cache/` directory that is a **sibling of the WAV's parent
 * directory** — matching §8.2's session layout exactly
 * (`master/session_master.wav` -> `waveform_cache/`), so the same convention
 * works for every master part without extra parameters. The cache is
 * versioned and keyed to the source file's mtime+size+format; it is rebuilt
 * automatically if missing or stale.
 *
 * [peaks] then serves any requested `(bucketCount, range)` by aggregating
 * base buckets — except when the caller asks for a *finer* resolution than
 * the base cache holds for that range (a close zoom), in which case it reads
 * the exact byte range straight from the source file instead of upsampling
 * cached data.
 *
 * Scope: 16-bit PCM only, same precedent as [AudioConversion]/[LevelMeter].
 */
class JvmWaveformService : WaveformService {

    override fun peaks(wav: Path, bucketCount: Int, startSample: Long, endSample: Long): WaveformPeaks {
        require(bucketCount > 0) { "bucketCount must be > 0, was $bucketCount" }
        require(startSample >= 0) { "startSample must be >= 0, was $startSample" }
        require(startSample < endSample) { "startSample ($startSample) must be < endSample ($endSample)" }

        val header = WavIo.readHeader(wav)
        val format = header.format
        require(format.bits == 16) { "JvmWaveformService supports 16-bit PCM only, was ${format.bits}-bit" }
        val totalFrames = format.framesIn(header.dataSize)
        require(endSample <= totalFrames) {
            "range [$startSample, $endSample) exceeds frame count $totalFrames for $wav"
        }

        val base = loadOrBuildBaseCache(wav, header, totalFrames)
        val baseStart = (startSample / base.bucketFrames).toInt()
        val baseEndExclusive = ceil(endSample.toDouble() / base.bucketFrames).toInt().coerceAtMost(base.bucketCount)
        val baseBucketsInRange = (baseEndExclusive - baseStart).coerceAtLeast(1)

        return if (bucketCount >= baseBucketsInRange) {
            computeDirect(wav, format, startSample, endSample, bucketCount)
        } else {
            aggregateFromBase(base, baseStart, baseBucketsInRange, bucketCount)
        }
    }

    // region direct (uncached) computation — used when zoomed in past the base cache's resolution

    private fun computeDirect(wav: Path, format: CaptureFormat, startSample: Long, endSample: Long, bucketCount: Int): WaveformPeaks {
        val frameCount = endSample - startSample
        val raw = WavIo.readFrameRange(wav, format, startSample, frameCount)
        val mins = FloatArray(bucketCount)
        val maxs = FloatArray(bucketCount)
        for (b in 0 until bucketCount) {
            val bucketStart = (b.toLong() * frameCount) / bucketCount
            val bucketEndExclusive = (((b + 1).toLong() * frameCount) / bucketCount).coerceAtLeast(bucketStart + 1)
            var mn = Float.POSITIVE_INFINITY
            var mx = Float.NEGATIVE_INFINITY
            for (f in bucketStart until min(bucketEndExclusive, frameCount)) {
                for (c in 0 until format.channels) {
                    val s = AudioConversion.readSample(raw, f, c, format.channels).toFloat() / SHORT_FULL_SCALE
                    if (s < mn) mn = s
                    if (s > mx) mx = s
                }
            }
            mins[b] = mn
            maxs[b] = mx
        }
        return WaveformPeaks(bucketCount, mins.toList(), maxs.toList())
    }

    // endregion

    // region base cache: build/load/aggregate

    private fun aggregateFromBase(base: BaseCache, baseStart: Int, baseBucketsInRange: Int, bucketCount: Int): WaveformPeaks {
        val mins = FloatArray(bucketCount)
        val maxs = FloatArray(bucketCount)
        for (b in 0 until bucketCount) {
            val lo = (baseStart + (b.toLong() * baseBucketsInRange / bucketCount).toInt())
                .coerceIn(0, base.bucketCount - 1)
            val hi = (baseStart + ((b + 1).toLong() * baseBucketsInRange / bucketCount).toInt())
                .coerceAtLeast(lo + 1).coerceAtMost(base.bucketCount)
            var mn = Float.POSITIVE_INFINITY
            var mx = Float.NEGATIVE_INFINITY
            for (i in lo until hi) {
                if (base.mins[i] < mn) mn = base.mins[i]
                if (base.maxs[i] > mx) mx = base.maxs[i]
            }
            mins[b] = mn
            maxs[b] = mx
        }
        return WaveformPeaks(bucketCount, mins.toList(), maxs.toList())
    }

    private fun loadOrBuildBaseCache(wav: Path, header: WavHeader.Parsed, totalFrames: Long): BaseCache {
        val cachePath = cachePathFor(wav)
        val sourceMtime = Files.getLastModifiedTime(wav).toMillis()
        val sourceSize = Files.size(wav)

        val cached = readCacheFile(cachePath)
        if (cached != null &&
            cached.sourceMtime == sourceMtime &&
            cached.sourceSize == sourceSize &&
            cached.format == header.format &&
            cached.totalFrames == totalFrames
        ) {
            return BaseCache(cached.bucketFrames, cached.bucketCount, cached.mins, cached.maxs)
        }

        val bucketFrames = baseBucketFramesFor(header.format.sampleRate)
        val fresh = buildBasePeaks(wav, header.format, totalFrames, bucketFrames)
        writeCacheFile(cachePath, header.format, sourceMtime, sourceSize, totalFrames, fresh)
        return fresh
    }

    /** Streams the file in chunks aligned to whole base buckets, so peaks never split
     * across a chunk boundary — bounds memory well below the full file size. */
    private fun buildBasePeaks(wav: Path, format: CaptureFormat, totalFrames: Long, bucketFrames: Int): BaseCache {
        val bucketCount = ceil(totalFrames.toDouble() / bucketFrames).toInt().coerceAtLeast(1)
        val mins = FloatArray(bucketCount) { Float.POSITIVE_INFINITY }
        val maxs = FloatArray(bucketCount) { Float.NEGATIVE_INFINITY }

        if (totalFrames > 0) {
            val frameSize = format.frameSize()
            val chunkFrames = (bucketFrames.toLong() * BASE_BUCKETS_PER_CHUNK).coerceAtMost(totalFrames)
            RandomAccessFile(wav.toFile(), "r").use { raf ->
                raf.seek(WavHeader.HEADER_SIZE.toLong())
                var frameCursor = 0L
                val buf = ByteArray((chunkFrames * frameSize).toInt())
                while (frameCursor < totalFrames) {
                    val framesThisChunk = min(chunkFrames, totalFrames - frameCursor)
                    val bytesToRead = (framesThisChunk * frameSize).toInt()
                    raf.readFully(buf, 0, bytesToRead)
                    for (localFrame in 0 until framesThisChunk) {
                        val globalFrame = frameCursor + localFrame
                        val bucket = (globalFrame / bucketFrames).toInt()
                        for (c in 0 until format.channels) {
                            val s = AudioConversion.readSample(buf, localFrame, c, format.channels).toFloat() / SHORT_FULL_SCALE
                            if (s < mins[bucket]) mins[bucket] = s
                            if (s > maxs[bucket]) maxs[bucket] = s
                        }
                    }
                    frameCursor += framesThisChunk
                }
            }
        }
        for (i in mins.indices) {
            if (mins[i] == Float.POSITIVE_INFINITY) {
                mins[i] = 0f
                maxs[i] = 0f
            }
        }
        return BaseCache(bucketFrames, bucketCount, mins, maxs)
    }

    private fun baseBucketFramesFor(sampleRate: Int): Int = (sampleRate / BASE_BUCKETS_PER_SECOND).coerceAtLeast(1)

    private fun cachePathFor(wav: Path): Path {
        val cacheDir = wav.parent?.resolveSibling(CACHE_DIR_NAME) ?: Path.of(CACHE_DIR_NAME)
        val name = wav.fileName.toString().substringBeforeLast(".", wav.fileName.toString())
        return cacheDir.resolve("$name.peaks.cache")
    }

    private fun readCacheFile(path: Path): CacheFileContents? {
        if (!Files.exists(path)) return null
        return try {
            val bytes = Files.readAllBytes(path)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.remaining() < CACHE_HEADER_BYTES) return null
            val magic = buffer.int
            if (magic != CACHE_MAGIC) return null
            val version = buffer.int
            if (version != CACHE_VERSION) return null
            val sourceMtime = buffer.long
            val sourceSize = buffer.long
            val sampleRate = buffer.int
            val channels = buffer.int
            val bits = buffer.int
            val totalFrames = buffer.long
            val bucketFrames = buffer.int
            val bucketCount = buffer.int
            if (bucketCount < 0 || buffer.remaining() < bucketCount * 8) return null
            val mins = FloatArray(bucketCount)
            val maxs = FloatArray(bucketCount)
            for (i in 0 until bucketCount) {
                mins[i] = buffer.float
                maxs[i] = buffer.float
            }
            CacheFileContents(
                sourceMtime, sourceSize, CaptureFormat(sampleRate, bits, channels),
                totalFrames, bucketFrames, bucketCount, mins, maxs,
            )
        } catch (e: Exception) {
            null // treat any corrupt/truncated cache as absent — rebuild from source
        }
    }

    private fun writeCacheFile(
        path: Path,
        format: CaptureFormat,
        sourceMtime: Long,
        sourceSize: Long,
        totalFrames: Long,
        cache: BaseCache,
    ) {
        path.parent?.let { Files.createDirectories(it) }
        val buffer = ByteBuffer.allocate(CACHE_HEADER_BYTES + cache.bucketCount * 8).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(CACHE_MAGIC)
        buffer.putInt(CACHE_VERSION)
        buffer.putLong(sourceMtime)
        buffer.putLong(sourceSize)
        buffer.putInt(format.sampleRate)
        buffer.putInt(format.channels)
        buffer.putInt(format.bits)
        buffer.putLong(totalFrames)
        buffer.putInt(cache.bucketFrames)
        buffer.putInt(cache.bucketCount)
        for (i in 0 until cache.bucketCount) {
            buffer.putFloat(cache.mins[i])
            buffer.putFloat(cache.maxs[i])
        }
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.write(tmp, buffer.array())
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    // endregion

    private data class BaseCache(val bucketFrames: Int, val bucketCount: Int, val mins: FloatArray, val maxs: FloatArray)

    private data class CacheFileContents(
        val sourceMtime: Long,
        val sourceSize: Long,
        val format: CaptureFormat,
        val totalFrames: Long,
        val bucketFrames: Int,
        val bucketCount: Int,
        val mins: FloatArray,
        val maxs: FloatArray,
    )

    companion object {
        /** `waveform_cache/` sits next to the WAV's own parent dir (§8.2). */
        const val CACHE_DIR_NAME = "waveform_cache"
        private const val CACHE_MAGIC = 0x53574643 // "SWFC"
        private const val CACHE_VERSION = 1
        private const val CACHE_HEADER_BYTES = 4 + 4 + 8 + 8 + 4 + 4 + 4 + 8 + 4 + 4
        private const val BASE_BUCKETS_PER_SECOND = 100 // 10 ms base buckets
        private const val BASE_BUCKETS_PER_CHUNK = 10_000 // chunk = 10_000 * 10ms = 100s of audio per read
        private const val SHORT_FULL_SCALE = 32768f
    }
}
