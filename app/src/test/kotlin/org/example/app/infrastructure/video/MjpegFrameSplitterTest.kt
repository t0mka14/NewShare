package org.example.app.infrastructure.video

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Builds byte-accurate synthetic JPEGs. The point of these fixtures is the awkward cases a
 * naive `FF D8` / `FF D9` scan gets wrong: stuffed `FF 00` literals and restart markers inside
 * entropy-coded data, and an APP segment carrying a complete embedded thumbnail.
 */
private object Jpeg {

    /** A marker segment: `FF <marker>` then a big-endian length covering itself. */
    fun segment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    }

    /**
     * A complete image. [entropy] is spliced into the scan data verbatim so a test can plant
     * stuffing and restart markers inside it.
     */
    fun image(app: ByteArray = segment(0xE0, "JFIF".toByteArray() + ByteArray(10)), entropy: ByteArray): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
            app +
            segment(0xDB, ByteArray(65) { it.toByte() }) +
            segment(0xDA, ByteArray(10) { it.toByte() }) +
            entropy +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    /** Scan data containing a stuffed literal 0xFF and two restart markers. */
    fun trickyEntropy(seed: Int): ByteArray = byteArrayOf(
        seed.toByte(), 0x11, 0x22,
        0xFF.toByte(), 0x00, // stuffed literal 0xFF, not a marker
        0x33, 0x44,
        0xFF.toByte(), 0xD0.toByte(), // RST0
        0x55,
        0xFF.toByte(), 0xD7.toByte(), // RST7
        0x66, 0x77, seed.toByte(),
    )

    /** An APP1 segment whose payload is an entire JPEG — an EXIF-style embedded thumbnail. */
    fun appWithThumbnail(): ByteArray = segment(0xE1, "Exif".toByteArray() + image(entropy = trickyEntropy(0x7A)))
}

private class Collector {
    val frames = mutableListOf<ByteArray>()
    val splitter = MjpegFrameSplitter { buffer, offset, length ->
        frames += buffer.copyOfRange(offset, offset + length)
    }

    fun feed(bytes: ByteArray, chunk: Int = Int.MAX_VALUE) {
        var offset = 0
        while (offset < bytes.size) {
            val n = minOf(chunk, bytes.size - offset)
            splitter.append(bytes, offset, n)
            offset += n
        }
    }
}

class MjpegFrameSplitterTest {

    @Test
    fun `splits back-to-back frames`() {
        val a = Jpeg.image(entropy = Jpeg.trickyEntropy(0x01))
        val b = Jpeg.image(entropy = Jpeg.trickyEntropy(0x02))
        val collector = Collector()

        collector.feed(a + b)

        assertEquals(2, collector.frames.size)
        assertArrayEquals(a, collector.frames[0])
        assertArrayEquals(b, collector.frames[1])
    }

    @Test
    fun `stuffed bytes and restart markers do not end a frame`() {
        val frame = Jpeg.image(entropy = Jpeg.trickyEntropy(0x5A))
        val collector = Collector()

        collector.feed(frame)

        assertEquals(1, collector.frames.size)
        assertArrayEquals(frame, collector.frames[0])
    }

    @Test
    fun `an embedded thumbnail does not end a frame early`() {
        val frame = Jpeg.image(app = Jpeg.appWithThumbnail(), entropy = Jpeg.trickyEntropy(0x3C))
        val collector = Collector()

        collector.feed(frame)

        assertEquals(1, collector.frames.size, "the thumbnail's own EOI must not terminate the outer frame")
        assertArrayEquals(frame, collector.frames[0])
    }

    /**
     * A pipe hands over arbitrary chunk boundaries, so every marker, length field and the SOI
     * itself has to survive being split. One byte at a time is the worst case.
     */
    @Test
    fun `reassembles frames across arbitrary chunk boundaries`() {
        val a = Jpeg.image(app = Jpeg.appWithThumbnail(), entropy = Jpeg.trickyEntropy(0x11))
        val b = Jpeg.image(entropy = Jpeg.trickyEntropy(0x22))
        val stream = a + b

        for (chunk in intArrayOf(1, 2, 3, 7, 64, 997)) {
            val collector = Collector()
            collector.feed(stream, chunk = chunk)

            assertEquals(2, collector.frames.size, "chunk size $chunk")
            assertArrayEquals(a, collector.frames[0], "chunk size $chunk")
            assertArrayEquals(b, collector.frames[1], "chunk size $chunk")
        }
    }

    @Test
    fun `withholds an incomplete trailing frame`() {
        val a = Jpeg.image(entropy = Jpeg.trickyEntropy(0x01))
        val b = Jpeg.image(entropy = Jpeg.trickyEntropy(0x02))
        val collector = Collector()

        collector.feed(a + b.copyOfRange(0, b.size - 4))

        assertEquals(1, collector.frames.size)
        assertArrayEquals(a, collector.frames[0])
    }

    @Test
    fun `resynchronises past leading garbage`() {
        val frame = Jpeg.image(entropy = Jpeg.trickyEntropy(0x09))
        val collector = Collector()

        collector.feed(byteArrayOf(0x00, 0x13, 0x37, 0xFF.toByte(), 0x4E) + frame)

        assertEquals(1, collector.frames.size)
        assertArrayEquals(frame, collector.frames[0])
        assertTrue(collector.splitter.resyncCount > 0)
    }

    @Test
    fun `recovers the next frame after a truncated one`() {
        val truncated = Jpeg.image(entropy = Jpeg.trickyEntropy(0x01)).let { it.copyOfRange(0, it.size / 2) }
        val good = Jpeg.image(entropy = Jpeg.trickyEntropy(0x02))
        val collector = Collector()

        collector.feed(truncated + good)

        assertEquals(1, collector.frames.size)
        assertArrayEquals(good, collector.frames[0])
    }
}
