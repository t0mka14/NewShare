package org.example.app.infrastructure.audio

import org.example.app.domain.audio.CaptureFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CaptureFormatMathTest {

    @Test
    fun `frameSize accounts for channels and bit depth`() {
        assertEquals(2, CaptureFormat(48_000, 16, 1).frameSize())
        assertEquals(4, CaptureFormat(48_000, 16, 2).frameSize())
        assertEquals(6, CaptureFormat(48_000, 24, 2).frameSize())
    }

    @Test
    fun `framesIn divides bytes by frame size`() {
        val format = CaptureFormat(48_000, 16, 1)
        assertEquals(500L, format.framesIn(1000L))
        assertEquals(500L, format.framesIn(1001L)) // rounds down, partial frame discarded
    }

    @Test
    fun `bytesForMillis rounds down to whole frames`() {
        val format = CaptureFormat(sampleRate = 48_000, bits = 16, channels = 1)
        // 100ms @ 48kHz mono 16-bit = 4800 frames * 2 bytes = 9600 bytes exactly
        assertEquals(9600, format.bytesForMillis(100))
        // 48kHz odd millis still yields a whole number of frames
        val bytes = format.bytesForMillis(83)
        assertEquals(0, bytes % format.frameSize())
    }

    @Test
    fun `chunk plus line buffer stays within the 200ms budget from spec 5_3_1`() {
        val format = CaptureFormat.PREFERRED
        val chunkBytes = format.bytesForMillis(80)
        val lineBufferBytes = format.bytesForMillis(100)
        val totalMs = format.millisIn(chunkBytes) + format.millisIn(lineBufferBytes)
        assertEquals(true, totalMs <= 200.0)
    }

    @Test
    fun `millisIn is the inverse of bytesForMillis at whole-frame boundaries`() {
        val format = CaptureFormat(sampleRate = 16_000, bits = 16, channels = 1)
        val bytes = format.bytesForMillis(250)
        assertEquals(250.0, format.millisIn(bytes), 0.001)
    }

    @Test
    fun `toJavaAudioFormat maps fields directly and forces little-endian PCM signed`() {
        val format = CaptureFormat(sampleRate = 44_100, bits = 16, channels = 2)
        val java = format.toJavaAudioFormat()
        assertEquals(44_100f, java.sampleRate)
        assertEquals(16, java.sampleSizeInBits)
        assertEquals(2, java.channels)
        assertEquals(false, java.isBigEndian)
        assertEquals(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED, java.encoding)
    }
}
