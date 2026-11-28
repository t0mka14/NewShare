package org.example.app.domain.audio

/**
 * Maps between "played frames" (frames rendered by an output line that plays [ranges]
 * back-to-back, with the cuts skipped) and source-file frames.
 *
 * [ranges] are inclusive, file-local frame ranges, non-empty, ascending and non-overlapping.
 * Touching ranges (`0..9`, `10..19`) are allowed.
 */
class RangeTimeMap(val ranges: List<LongRange>) {
    /** Cumulative played frames at the start of each range. */
    private val cumulative: LongArray

    /** Total frames played when all ranges have been rendered. */
    val totalPlayedFrames: Long

    init {
        require(ranges.isNotEmpty()) { "ranges must not be empty" }
        var sum = 0L
        var previousEnd = -1L
        cumulative = LongArray(ranges.size)
        ranges.forEachIndexed { i, r ->
            require(r.first >= 0) { "range $r starts before 0" }
            require(!r.isEmpty()) { "range $r is empty" }
            require(r.first > previousEnd) { "range $r overlaps or precedes the previous range" }
            cumulative[i] = sum
            sum += r.last - r.first + 1
            previousEnd = r.last
        }
        totalPlayedFrames = sum
    }

    /**
     * Source frame that is sounding after [playedFrames] frames were rendered. Never lies inside
     * a cut: at a range boundary it reports the start of the next kept range. At or past the end
     * it is the exclusive end of the last range (matching `playRange`'s stopSample).
     */
    fun sourceFrameAt(playedFrames: Long): Long {
        if (playedFrames <= 0) return ranges.first().first
        if (playedFrames >= totalPlayedFrames) return ranges.last().last + 1
        val i = indexOfRangeContaining(playedFrames)
        return ranges[i].first + (playedFrames - cumulative[i])
    }

    /**
     * Played-frame offset of [sourceFrame]. A source frame inside a cut (or before the first
     * range) maps to the start of the next kept range; past the last range, to the total.
     */
    fun playedFramesAt(sourceFrame: Long): Long {
        for (i in ranges.indices) {
            val r = ranges[i]
            if (sourceFrame < r.first) return cumulative[i]
            if (sourceFrame <= r.last) return cumulative[i] + (sourceFrame - r.first)
        }
        return totalPlayedFrames
    }

    private fun indexOfRangeContaining(playedFrames: Long): Int {
        var lo = 0
        var hi = ranges.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (cumulative[mid] <= playedFrames) lo = mid else hi = mid - 1
        }
        return lo
    }
}
