package org.example.app.fakes

import org.example.app.domain.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * Deterministic [Clock] for tests. No real time is ever read.
 *
 * Other fakes that need to move in lockstep with domain time (notably
 * [FakeContinuousSessionRecorder]'s `writtenSamples` counter, §10.3) register
 * an [onAdvance] listener so a single `clock.advance(...)` call is the one
 * source of truth for "time passed" in a test — this is also how UI-test
 * scenario helpers coordinate the Compose `mainClock` and this clock (§10.3):
 * advance the Compose clock, then advance this clock by the same amount, and
 * every subscribed fake updates consistently.
 */
class FakeClock(
    initial: Instant = Instant.parse("2026-07-01T09:00:00Z"),
) : Clock {
    private var instant: Instant = initial
    private val listeners = mutableListOf<(Instant) -> Unit>()

    override fun now(): Instant = instant

    /** Jump directly to [newInstant] (e.g. to fix a session's `startedAt`). */
    fun set(newInstant: Instant) {
        instant = newInstant
        notifyListeners()
    }

    /** Move time forward by [duration]. Negative durations are rejected. */
    fun advance(duration: Duration) {
        require(!duration.isNegative()) { "FakeClock cannot move backwards: $duration" }
        instant = instant.plus(duration.toJavaDuration())
        notifyListeners()
    }

    /** Called after every [set]/[advance] with the new instant. */
    fun onAdvance(listener: (Instant) -> Unit) {
        listeners += listener
    }

    private fun notifyListeners() {
        // Copy to avoid ConcurrentModificationException if a listener registers another.
        listeners.toList().forEach { it(instant) }
    }
}
