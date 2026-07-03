package org.example.app.fakes

import org.example.app.domain.IdGenerator

/**
 * Predictable, sequential session IDs (no UUID randomness) so assertions on
 * session-folder names / paths are stable across test runs.
 */
class FakeIdGenerator(
    private val prefix: String = "session",
) : IdGenerator {
    private var counter = 0

    override fun newSessionId(): String {
        counter += 1
        return "$prefix-${counter.toString().padStart(4, '0')}"
    }

    /** Test-only: reset the sequence, e.g. between scenario phases. */
    fun reset() {
        counter = 0
    }
}
