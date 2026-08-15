package org.example.app.domain

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val logger = KotlinLogging.logger {}

/**
 * Last-resort sink for exceptions that escaped every feature-level failure flow (recorder
 * `AudioError`s, session-start rejection, config Blocking screen, …). `Main.kt` routes the
 * process-wide uncaught-exception handlers here; the UI renders [current] as a dialog on top
 * of whatever screen is showing.
 *
 * Classification: JVM [Error]s mean the process state is no longer trustworthy → fatal (the
 * dialog only offers exit). Any [Exception] reaching this point is outside the guarded
 * recording/session paths, so the app keeps running after the user dismisses the dialog —
 * for a recording tool, killing a live examination is worse than a degraded feature.
 */
class ErrorReporter {

    data class ReportedError(val throwable: Throwable, val fatal: Boolean)

    private val _current = MutableStateFlow<ReportedError?>(null)
    val current: StateFlow<ReportedError?> = _current.asStateFlow()

    fun report(throwable: Throwable) {
        val fatal = throwable is Error
        logger.error(throwable) { "unhandled ${if (fatal) "fatal" else "recoverable"} error" }
        // A fatal error stays on screen until the user exits — never downgraded by a later one.
        if (_current.value?.fatal == true) return
        _current.value = ReportedError(throwable, fatal)
    }

    fun dismiss() {
        if (_current.value?.fatal == true) return
        _current.value = null
    }
}
