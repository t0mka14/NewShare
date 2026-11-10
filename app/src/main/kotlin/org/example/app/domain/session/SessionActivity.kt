package org.example.app.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-wide "a recording session is running" signal. Background work that must stay out of a
 * session's way (sending logs) reads [active].
 *
 * Every [begin] returns its own idempotent end function, and [active] is true while at least
 * one begun session has not ended. The component that calls [begin] ties the end function to
 * its lifecycle destruction, so the signal cannot stay true after the session screen is gone,
 * whether it ended, was left or failed.
 */
class SessionActivity {
    private val lock = Any()
    private var running = 0
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    /** Marks a session as running; call the result once it is over (further calls do nothing). */
    fun begin(): () -> Unit {
        synchronized(lock) {
            running++
            _active.value = true
        }
        val ended = AtomicBoolean(false)
        return {
            if (ended.compareAndSet(false, true)) {
                synchronized(lock) {
                    running--
                    _active.value = running > 0
                }
            }
        }
    }
}
