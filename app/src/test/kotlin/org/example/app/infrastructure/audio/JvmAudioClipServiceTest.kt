package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class JvmAudioClipServiceTest {

    private val service = JvmAudioClipService()
    private val mono48k = CaptureFormat(sampleRate = 48_000, bits = 16, channels = 1)
    private val stereo48k = CaptureFormat(sampleRate = 48_000, bits = 16, channels = 2)

    @Test
    fun `cutClip extracts the exact sample range with no format change`(@TempDir dir: Path) {
        val source = dir.resolve("master/session_master.wav")
        val samples = monoRamp(1000)
        writeTestWav(source, mono48k, samples)

        val output = dir.resolve("clips/clip.wav")
        service.cutClip(source, startSample = 100, stopSample = 300, targetFormat = mono48k, output = output)

        val (format, cut) = readTestWavSamples(output)
        assertEquals(mono48k, format)
        assertEquals(200, cut.size)
        assertArrayEquals(samples.copyOfRange(100, 300), cut)
    }

    @Test
    fun `cutClip at the very start and very end of the file is exact`(@TempDir dir: Path) {
        val source = dir.resolve("master/session_master.wav")
        val samples = monoRamp(500)
        writeTestWav(source, mono48k, samples)

        val startOutput = dir.resolve("start.wav")
        service.cutClip(source, 0, 10, mono48k, startOutput)
        assertArrayEquals(samples.copyOfRange(0, 10), readTestWavSamples(startOutput).second)

        val endOutput = dir.resolve("end.wav")
        service.cutClip(source, 490, 500, mono48k, endOutput)
        assertArrayEquals(samples.copyOfRange(490, 500), readTestWavSamples(endOutput).second)
    }

    @Test
    fun `cutClip downmixes stereo to mono by averaging`(@TempDir dir: Path) {
        val source = dir.resolve("master/session_master.wav")
        // 4 frames, stereo: L/R pairs chosen for exact-average results.
        val interleaved = shortArrayOf(100, 200, -100, -200, 0, 0, 1000, 2000)
        writeTestWav(source, stereo48k, interleaved)

        val output = dir.resolve("clip.wav")
        service.cutClip(source, 0, 4, mono48k, output)

        val (format, mono) = readTestWavSamples(output)
        assertEquals(mono48k, format)
        assertArrayEquals(shortArrayOf(150, -150, 0, 1500), mono)
    }

    @Test
    fun `cutClip resamples when target rate differs`(@TempDir dir: Path) {
        val source = dir.resolve("master/session_master.wav")
        val source24k = CaptureFormat(sampleRate = 24_000, bits = 16, channels = 1)
        val samples = monoRamp(240) // 10ms at 24kHz
        writeTestWav(source, source24k, samples)

        val target48k = CaptureFormat(sampleRate = 48_000, bits = 16, channels = 1)
        val output = dir.resolve("clip.wav")
        service.cutClip(source, 0, 240, target48k, output)

        val (format, resampled) = readTestWavSamples(output)
        assertEquals(target48k, format)
        // Doubling the rate should double the frame count (tolerance: exact for
        // integer ratios, since resampleLinear rounds to the nearest frame).
        assertEquals(480, resampled.size)
    }

    @Test
    fun `cutClip rejects a range exceeding the source frame count`(@TempDir dir: Path) {
        val source = dir.resolve("master/session_master.wav")
        writeTestWav(source, mono48k, monoRamp(100))

        assertThrows(IllegalArgumentException::class.java) {
            service.cutClip(source, 50, 200, mono48k, dir.resolve("clip.wav"))
        }
    }

    @Test
    fun `cutClip rejects an empty or inverted range rather than silently truncating`(@TempDir dir: Path) {
        val source = dir.resolve("master/session_master.wav")
        writeTestWav(source, mono48k, monoRamp(100))

        assertThrows(IllegalArgumentException::class.java) {
            service.cutClip(source, 50, 50, mono48k, dir.resolve("a.wav"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.cutClip(source, 60, 50, mono48k, dir.resolve("b.wav"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.cutClip(source, -5, 50, mono48k, dir.resolve("c.wav"))
        }
    }

    @Test
    fun `convert rewrites a whole file to the target format`(@TempDir dir: Path) {
        val source = dir.resolve("master/session_master.part2.wav")
        val samples = monoRamp(1000, step = 37)
        writeTestWav(source, stereo48k, ShortArray(2000) { samples[it / 2] }) // fake stereo duplicate channel

        val output = dir.resolve("converted.wav")
        service.convert(source, mono48k, output)

        val (format, converted) = readTestWavSamples(output)
        assertEquals(mono48k, format)
        assertArrayEquals(samples, converted)
    }

    @Test
    fun `concatenate joins sources in order and produces one continuous data chunk`(@TempDir dir: Path) {
        val part1 = dir.resolve("part1.wav")
        val part2 = dir.resolve("part2.wav")
        val samples1 = monoRamp(100)
        val samples2 = monoRamp(50, step = -77)
        writeTestWav(part1, mono48k, samples1)
        writeTestWav(part2, mono48k, samples2)

        val output = dir.resolve("archive/session_master.wav")
        service.concatenate(listOf(part1, part2), mono48k, output)

        val (format, joined) = readTestWavSamples(output)
        assertEquals(mono48k, format)
        assertEquals(150, joined.size)
        assertArrayEquals(samples1, joined.copyOfRange(0, 100))
        assertArrayEquals(samples2, joined.copyOfRange(100, 150))
    }

    @Test
    fun `concatenate rejects a source not already in the target format`(@TempDir dir: Path) {
        val part1 = dir.resolve("part1.wav")
        val part2 = dir.resolve("part2.wav")
        writeTestWav(part1, mono48k, monoRamp(10))
        writeTestWav(part2, stereo48k, ShortArray(20))

        assertThrows(IllegalArgumentException::class.java) {
            service.concatenate(listOf(part1, part2), mono48k, dir.resolve("out.wav"))
        }
    }

    @Test
    fun `concatenate rejects an empty source list`(@TempDir dir: Path) {
        assertThrows(IllegalArgumentException::class.java) {
            service.concatenate(emptyList(), mono48k, dir.resolve("out.wav"))
        }
    }

    @Test
    fun `output is written atomically, no tmp file left behind`(@TempDir dir: Path) {
        val source = dir.resolve("master/session_master.wav")
        writeTestWav(source, mono48k, monoRamp(100))

        val output = dir.resolve("clips/clip.wav")
        service.cutClip(source, 0, 50, mono48k, output)

        assertTrue(Files.exists(output))
        assertTrue(Files.list(output.parent).noneMatch { it.fileName.toString().endsWith(".tmp") })
    }
}
