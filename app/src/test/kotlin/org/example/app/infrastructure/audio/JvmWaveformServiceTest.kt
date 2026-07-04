package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class JvmWaveformServiceTest {

    private val service = JvmWaveformService()

    // sampleRate 1000 -> baseBucketFramesFor = 1000/100 = 10 frames per base bucket.
    private val format = CaptureFormat(sampleRate = 1000, bits = 16, channels = 1)

    private fun rampWav(dir: Path, frameCount: Int = 500): Path {
        val source = dir.resolve("master/session_master.wav")
        writeTestWav(source, format, ShortArray(frameCount) { it.toShort() })
        return source
    }

    @Test
    fun `peaks computed directly when zoomed in past the base cache resolution`(@TempDir dir: Path) {
        val source = rampWav(dir)

        // Range [0,10) has exactly 1 base bucket; requesting 10 buckets is finer
        // than the cache holds, so this must recompute exactly from the file.
        val result = service.peaks(source, bucketCount = 10, startSample = 0, endSample = 10)

        assertEquals(10, result.bucketCount)
        for (i in 0 until 10) {
            val expected = i / 32768f
            assertEquals(expected, result.min[i], 1e-6f)
            assertEquals(expected, result.max[i], 1e-6f)
        }
    }

    @Test
    fun `peaks aggregated from the base cache match hand-computed bucket boundaries`(@TempDir dir: Path) {
        val source = rampWav(dir, frameCount = 500)

        // Full range, 5 buckets over 50 base buckets (10 frames each) -> each
        // output bucket spans 100 frames.
        val result = service.peaks(source, bucketCount = 5, startSample = 0, endSample = 500)

        assertEquals(5, result.bucketCount)
        for (b in 0 until 5) {
            val expectedMin = (b * 100) / 32768f
            val expectedMax = (b * 100 + 99) / 32768f
            assertEquals(expectedMin, result.min[b], 1e-6f)
            assertEquals(expectedMax, result.max[b], 1e-6f)
        }
    }

    @Test
    fun `peaks over a sub-range offsets correctly`(@TempDir dir: Path) {
        val source = rampWav(dir, frameCount = 500)

        // frames [200,400): 20 base buckets of 10 frames; 2 output buckets -> 100 frames each.
        val result = service.peaks(source, bucketCount = 2, startSample = 200, endSample = 400)

        assertEquals(200 / 32768f, result.min[0], 1e-6f)
        assertEquals(299 / 32768f, result.max[0], 1e-6f)
        assertEquals(300 / 32768f, result.min[1], 1e-6f)
        assertEquals(399 / 32768f, result.max[1], 1e-6f)
    }

    @Test
    fun `cache file is created next to a waveform_cache directory sibling of the wav's parent`(@TempDir dir: Path) {
        val source = rampWav(dir)

        service.peaks(source, bucketCount = 5, startSample = 0, endSample = 500)

        val cachePath = dir.resolve("waveform_cache/session_master.peaks.cache")
        assertTrue(Files.exists(cachePath), "expected cache file at $cachePath")
    }

    @Test
    fun `cache round-trip returns identical results on a second call`(@TempDir dir: Path) {
        val source = rampWav(dir)

        val first = service.peaks(source, bucketCount = 5, startSample = 0, endSample = 500)
        val second = service.peaks(source, bucketCount = 5, startSample = 0, endSample = 500)

        assertEquals(first, second)
    }

    @Test
    fun `cache regenerates transparently if the cache file is deleted`(@TempDir dir: Path) {
        val source = rampWav(dir)
        val first = service.peaks(source, bucketCount = 5, startSample = 0, endSample = 500)

        val cachePath = dir.resolve("waveform_cache/session_master.peaks.cache")
        Files.delete(cachePath)
        assertFalse(Files.exists(cachePath))

        val second = service.peaks(source, bucketCount = 5, startSample = 0, endSample = 500)

        assertEquals(first, second)
        assertTrue(Files.exists(cachePath), "cache should be rebuilt on demand")
    }

    @Test
    fun `cache is invalidated when the source file changes`(@TempDir dir: Path) {
        val source = rampWav(dir, frameCount = 500)
        val stale = service.peaks(source, bucketCount = 5, startSample = 0, endSample = 500)

        // Overwrite with a different (reversed) ramp and force the mtime forward,
        // independent of filesystem timestamp-resolution flakiness.
        writeTestWav(source, format, ShortArray(500) { (499 - it).toShort() })
        val bumped = Files.getLastModifiedTime(source).toMillis() + 60_000
        Files.setLastModifiedTime(source, FileTime.fromMillis(bumped))

        val fresh = service.peaks(source, bucketCount = 5, startSample = 0, endSample = 500)

        assertTrue(stale != fresh, "stale cache must not be reused once the source file changed")
        // Reversed ramp: first output bucket now holds the *highest* values.
        assertEquals(499 / 32768f, fresh.max[0], 1e-6f)
    }

    @Test
    fun `peaks rejects an out-of-range end sample`(@TempDir dir: Path) {
        val source = rampWav(dir, frameCount = 100)
        assertThrows(IllegalArgumentException::class.java) {
            service.peaks(source, 10, 0, 200)
        }
    }

    @Test
    fun `peaks rejects an inverted or empty range`(@TempDir dir: Path) {
        val source = rampWav(dir, frameCount = 100)
        assertThrows(IllegalArgumentException::class.java) {
            service.peaks(source, 10, 50, 50)
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.peaks(source, 10, 60, 50)
        }
    }

    @Test
    fun `peaks rejects a non-positive bucketCount`(@TempDir dir: Path) {
        val source = rampWav(dir, frameCount = 100)
        assertThrows(IllegalArgumentException::class.java) {
            service.peaks(source, 0, 0, 50)
        }
    }

    @Test
    fun `peaks rejects formats other than 16-bit PCM`(@TempDir dir: Path) {
        val source = dir.resolve("master/session_master.wav")
        val format8bit = CaptureFormat(sampleRate = 8000, bits = 8, channels = 1)
        Files.createDirectories(source.parent)
        RandomAccessFile(source.toFile(), "rw").use { raf ->
            raf.write(WavHeader.build(format8bit, 100))
            raf.write(ByteArray(100))
        }

        assertThrows(IllegalArgumentException::class.java) {
            service.peaks(source, 10, 0, 50)
        }
    }
}
