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

    // region multi-range cutClip

    @Test
    fun `multi-range cutClip joins the kept ranges bit-exactly and length is the sum`(@TempDir dir: Path) {
        val source = dir.resolve("master.wav")
        val samples = monoRamp(1000)
        writeTestWav(source, mono48k, samples)
        val output = dir.resolve("clips/clip.wav")

        service.cutClip(source, listOf(10L..99L, 200L..299L, 900L..999L), mono48k, output)

        val (format, cut) = readTestWavSamples(output)
        assertEquals(mono48k, format)
        assertEquals(90 + 100 + 100, cut.size)
        val expected = samples.copyOfRange(10, 100) + samples.copyOfRange(200, 300) + samples.copyOfRange(900, 1000)
        assertArrayEquals(expected, cut)
        assertTrue(Files.list(output.parent).noneMatch { it.fileName.toString().endsWith(".tmp") })
    }

    @Test
    fun `multi-range cutClip with stereo to mono conversion keeps length and averages`(@TempDir dir: Path) {
        val source = dir.resolve("master.wav")
        val stereo = ShortArray(2 * 100) { i -> if (i % 2 == 0) 1000 else 3000 } // L=1000, R=3000
        writeTestWav(source, stereo48k, stereo)
        val output = dir.resolve("clip.wav")

        service.cutClip(source, listOf(0L..9L, 50L..79L), mono48k, output)

        val (_, cut) = readTestWavSamples(output)
        assertEquals(40, cut.size)
        assertTrue(cut.all { it == 2000.toShort() })
    }

    @Test
    fun `multi-range cutClip with rate change is within one sample per range of the ideal length`(@TempDir dir: Path) {
        val source = dir.resolve("master.wav")
        val mono24k = CaptureFormat(sampleRate = 24_000, bits = 16, channels = 1)
        writeTestWav(source, mono24k, monoRamp(2400))
        val output = dir.resolve("clip.wav")
        val ranges = listOf(0L..119L, 500L..699L, 1000L..1100L) // 120 + 200 + 101 source frames

        service.cutClip(source, ranges, mono48k, output)

        val (format, cut) = readTestWavSamples(output)
        assertEquals(mono48k, format)
        val ideal = (120 + 200 + 101) * 2
        assertTrue(kotlin.math.abs(cut.size - ideal) <= ranges.size, "length ${cut.size} vs ideal $ideal")
    }

    @Test
    fun `cutClip rejects invalid range lists and writes nothing`(@TempDir dir: Path) {
        val source = dir.resolve("master.wav")
        writeTestWav(source, mono48k, monoRamp(500))
        val output = dir.resolve("clip.wav")
        val invalid = listOf(
            emptyList(),
            listOf(10L until 10L), // empty range
            listOf(-1L..5L),
            listOf(100L..200L, 150L..250L), // overlapping
            listOf(300L..400L, 100L..200L), // descending
            listOf(400L..500L), // exceeds 500 frames
        )
        for (ranges in invalid) {
            assertThrows(IllegalArgumentException::class.java, { service.cutClip(source, ranges, mono48k, output) }, "ranges $ranges")
        }
        assertTrue(!Files.exists(output))
    }

    @Test
    fun `adjacent ranges are accepted`(@TempDir dir: Path) {
        val source = dir.resolve("master.wav")
        val samples = monoRamp(100)
        writeTestWav(source, mono48k, samples)
        val output = dir.resolve("clip.wav")
        service.cutClip(source, listOf(0L..49L, 50L..99L), mono48k, output)
        assertArrayEquals(samples, readTestWavSamples(output).second)
    }

    // endregion

    // region nearestZeroCrossing

    private fun sine(frames: Int, period: Int, amplitude: Int = 10_000, phase: Double = 0.3) =
        ShortArray(frames) { i -> Math.round(amplitude * kotlin.math.sin(2 * Math.PI * i / period + phase)).toInt().toShort() }

    @Test
    fun `nearestZeroCrossing finds the crossing nearest the requested sample on a sine`(@TempDir dir: Path) {
        val source = dir.resolve("sine.wav")
        val s = sine(2000, period = 200)
        writeTestWav(source, mono48k, s)
        val request = 1234L

        val result = service.nearestZeroCrossing(source, request, 100)

        fun isCrossing(i: Int) = i > 0 && (s[i - 1] < 0 && s[i] > 0 || s[i - 1] > 0 && s[i] < 0 || s[i].toInt() == 0)
        assertTrue(isCrossing(result.toInt()), "result $result is not a crossing")
        for (i in 1..1999) {
            if (isCrossing(i)) assertTrue(kotlin.math.abs(i - request) >= kotlin.math.abs(result - request), "$i is closer than $result")
        }
        assertTrue(kotlin.math.abs(result - request) <= 50)
    }

    @Test
    fun `nearestZeroCrossing returns the input when the window has no crossing`(@TempDir dir: Path) {
        val source = dir.resolve("dc.wav")
        writeTestWav(source, mono48k, ShortArray(1000) { 5000 })
        assertEquals(500L, service.nearestZeroCrossing(source, 500, 100))
    }

    @Test
    fun `nearestZeroCrossing treats an exact zero frame as a crossing`(@TempDir dir: Path) {
        val source = dir.resolve("zero.wav")
        val s = ShortArray(1000) { 5000 }
        s[520] = 0
        writeTestWav(source, mono48k, s)
        assertEquals(520L, service.nearestZeroCrossing(source, 500, 100))
        assertEquals(500L, service.nearestZeroCrossing(source, 500, 10)) // outside the radius
    }

    @Test
    fun `nearestZeroCrossing clamps at the file edges`(@TempDir dir: Path) {
        val source = dir.resolve("edge.wav")
        val s = ShortArray(100) { 5000 }
        s[0] = 0 // crossing at the very first frame
        s[99] = -5000 // sign change between frame 98 and 99
        writeTestWav(source, mono48k, s)

        assertEquals(0L, service.nearestZeroCrossing(source, 3, 1000)) // window clamps to [0, 99]; nearest to 3 is 0
        assertEquals(99L, service.nearestZeroCrossing(source, 97, 1000))
        assertEquals(99L, service.nearestZeroCrossing(source, 5000, 100_000)) // sample beyond the file
        assertEquals(5000L, service.nearestZeroCrossing(source, 5000, 10)) // window entirely outside
    }

    @Test
    fun `nearestZeroCrossing mixes stereo down to mono`(@TempDir dir: Path) {
        val source = dir.resolve("stereo.wav")
        // L = +1000 always, R flips from +1000 to -3000 at frame 60: mono mix goes 1000 -> -1000.
        val frames = 200
        val s = ShortArray(frames * 2)
        for (i in 0 until frames) {
            s[i * 2] = 1000
            s[i * 2 + 1] = if (i < 60) 1000 else (-3000).toShort()
        }
        writeTestWav(source, stereo48k, s)
        assertEquals(60L, service.nearestZeroCrossing(source, 80, 50))
    }

    @Test
    fun `nearestZeroCrossing prefers the earlier index on a tie`(@TempDir dir: Path) {
        val source = dir.resolve("tie.wav")
        val s = ShortArray(100) { 5000 }
        s[40] = 0
        s[60] = 0
        writeTestWav(source, mono48k, s)
        assertEquals(40L, service.nearestZeroCrossing(source, 50, 20))
    }

    // endregion
}
