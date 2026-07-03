package org.example.app.infrastructure.audio

/**
 * Tracks `writtenSamples` (§5.3.1) as a single monotonically increasing
 * counter across master *parts* (§8.5 device-loss continuation): each part's
 * writer restarts its own local byte/frame count at zero, but timeline
 * offsets must keep counting from the running session total so that
 * `RECORDING_INTERRUPTED`/`RESUMED` events correctly delimit parts within one
 * continuous offset space.
 *
 * Pure arithmetic, no I/O — unit-testable in isolation.
 */
class GlobalSampleCounter {
    @Volatile
    var total: Long = 0
        private set

    private var basePartSamples: Long = 0

    /** Call when a new part starts writing (first `startWriting`, or each `resume`):
     * snapshots the current total as the base the next part's local count is added to. */
    fun startNewPart() {
        basePartSamples = total
    }

    /** Call with the current *local* frame count written in the active part.
     * Returns the updated global total. */
    fun update(localFramesInCurrentPart: Long): Long {
        total = basePartSamples + localFramesInCurrentPart
        return total
    }

    /** Clears all state (new session / recorder restart, no leaks between sessions). */
    fun reset() {
        basePartSamples = 0
        total = 0
    }
}
