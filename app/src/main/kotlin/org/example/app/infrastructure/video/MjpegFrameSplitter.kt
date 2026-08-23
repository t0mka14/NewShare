package org.example.app.infrastructure.video

/**
 * Splits an MJPEG elementary stream — complete JPEG images written back to back with nothing
 * between them — into individual frames.
 *
 * This walks the JPEG marker structure rather than scanning for the `FF D8` / `FF D9`
 * byte pairs. A naive scan is wrong on two counts: an APP0/APP1 segment may embed a
 * thumbnail, which is itself a complete JPEG carrying its own SOI and EOI, and entropy-coded
 * data legitimately contains `FF 00` stuffing and `FF D0`–`FF D7` restart markers. Stepping
 * over each segment by its declared length skips embedded thumbnails whole, so only the real
 * end-of-image terminates a frame.
 *
 * Frames are handed to [onFrame] as a range inside this splitter's own buffer, valid only for
 * the duration of the call — the caller writes it out and copies it if it needs to keep it.
 * That keeps the hot path free of a per-frame allocation at 30 fps.
 *
 * Not thread-safe: one instance belongs to one capture reader thread.
 */
internal class MjpegFrameSplitter(
    initialCapacity: Int = DEFAULT_CAPACITY,
    private val onFrame: (buffer: ByteArray, offset: Int, length: Int) -> Unit,
) {
    private var buffer = ByteArray(initialCapacity)

    /** Bytes held in [buffer], starting at index 0. */
    private var size = 0

    /** Frames dropped because the stream could not be parsed and had to be resynchronised. */
    var resyncCount: Long = 0
        private set

    fun append(src: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        ensureCapacity(size + length)
        src.copyInto(buffer, destinationOffset = size, startIndex = offset, endIndex = offset + length)
        size += length
        drainFrames()
    }

    fun reset() {
        size = 0
    }

    private fun drainFrames() {
        var start = 0
        while (true) {
            if (size - start < MIN_FRAME_BYTES) break

            if (!isSoi(start)) {
                val resync = indexOfSoi(start)
                if (resync < 0) {
                    // Nothing usable in what we hold. Keep the trailing two bytes: a SOI
                    // may straddle this read and the next.
                    resyncCount++
                    start = maxOf(start, size - 2)
                    break
                }
                resyncCount++
                start = resync
                continue
            }

            val length = frameLengthAt(start)
            if (length == INCOMPLETE) break
            if (length == MALFORMED) {
                val resync = indexOfSoi(start + 2)
                if (resync < 0) {
                    resyncCount++
                    start = size
                    break
                }
                resyncCount++
                start = resync
                continue
            }

            onFrame(buffer, start, length)
            start += length
        }
        compact(start)
    }

    /**
     * Length of the complete JPEG beginning at [start], or [INCOMPLETE] when more bytes are
     * needed, or [MALFORMED] when the marker structure does not parse.
     */
    private fun frameLengthAt(start: Int): Int {
        var p = start + 2 // past SOI
        while (true) {
            if (p + 1 >= size) return INCOMPLETE

            if (u8(p) != 0xFF) return MALFORMED
            // Fill bytes: any number of 0xFF may precede a marker code.
            while (p + 1 < size && u8(p + 1) == 0xFF) p++
            if (p + 1 >= size) return INCOMPLETE

            val marker = u8(p + 1)
            when {
                marker == EOI -> return (p + 2) - start
                marker == SOS -> {
                    if (p + 3 >= size) return INCOMPLETE
                    p += 2 + be16(p + 2)
                    val end = scanEntropyCodedData(p)
                    if (end == INCOMPLETE) return INCOMPLETE
                    p = end
                }
                // Standalone markers carry no length payload.
                marker == TEM || marker in RST_FIRST..RST_LAST -> p += 2
                marker == SOI -> return MALFORMED // a nested SOI outside a segment is corruption
                else -> {
                    if (p + 3 >= size) return INCOMPLETE
                    val segmentLength = be16(p + 2)
                    if (segmentLength < 2) return MALFORMED
                    p += 2 + segmentLength
                }
            }
            if (p > size) return INCOMPLETE
        }
    }

    /**
     * Walks entropy-coded data from [from] to the marker that ends it, returning that marker's
     * position. `FF 00` is a stuffed literal 0xFF and `FF D0`–`FF D7` are restart markers;
     * neither terminates the scan.
     */
    private fun scanEntropyCodedData(from: Int): Int {
        var p = from
        while (true) {
            if (p + 1 >= size) return INCOMPLETE
            if (u8(p) != 0xFF) {
                p++
                continue
            }
            val next = u8(p + 1)
            if (next == 0xFF) {
                p++ // fill byte; the next byte may be the marker code
                continue
            }
            if (next == 0x00 || next in RST_FIRST..RST_LAST) {
                p += 2 // stuffed literal 0xFF, or a restart marker: neither ends the scan
                continue
            }
            return p
        }
    }

    private fun isSoi(index: Int): Boolean =
        index + 2 < size && u8(index) == 0xFF && u8(index + 1) == SOI && u8(index + 2) == 0xFF

    private fun indexOfSoi(from: Int): Int {
        var i = maxOf(from, 0)
        while (i + 2 < size) {
            if (u8(i) == 0xFF && u8(i + 1) == SOI && u8(i + 2) == 0xFF) return i
            i++
        }
        return -1
    }

    private fun compact(consumed: Int) {
        if (consumed <= 0) return
        val remaining = size - consumed
        if (remaining > 0) buffer.copyInto(buffer, destinationOffset = 0, startIndex = consumed, endIndex = size)
        size = remaining
    }

    private fun ensureCapacity(required: Int) {
        if (required <= buffer.size) return
        var capacity = buffer.size
        while (capacity < required) capacity *= 2
        buffer = buffer.copyOf(capacity)
    }

    private fun u8(index: Int): Int = buffer[index].toInt() and 0xFF

    private fun be16(index: Int): Int = (u8(index) shl 8) or u8(index + 1)

    private companion object {
        const val INCOMPLETE = -1
        const val MALFORMED = -2

        const val SOI = 0xD8
        const val EOI = 0xD9
        const val SOS = 0xDA
        const val TEM = 0x01
        const val RST_FIRST = 0xD0
        const val RST_LAST = 0xD7

        /** SOI + a minimal segment + EOI; below this there cannot be a whole frame. */
        const val MIN_FRAME_BYTES = 4

        /** Comfortably above a 1080p MJPEG frame (~100 KB); grows if a camera exceeds it. */
        const val DEFAULT_CAPACITY = 1 shl 20
    }
}
