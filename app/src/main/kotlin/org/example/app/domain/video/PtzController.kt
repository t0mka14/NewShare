package org.example.app.domain.video

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pan/tilt/zoom control for a camera that supports it.
 *
 * The only implementation that does anything is Windows-only (DirectShow `IAMCameraControl`
 * over COM). Everywhere else [NoOpPtzController] is bound, and [isAvailable] is false so the
 * UI renders no PTZ controls at all rather than dead buttons — a `havePTZ: true` task
 * configuration must not produce controls on a platform that cannot honour them.
 */
interface PtzController {
    /** False on platforms with no PTZ backend, and on Windows when the camera has no PTZ. */
    val isAvailable: Boolean

    /** Current zoom as reported by the camera, or null when unknown/unavailable. */
    val zoom: StateFlow<Int?>

    /**
     * Apply [action]. Movement actions start continuous motion and run until
     * [PtzAction.Stop] arrives — the UI drives these from a press-and-hold button.
     */
    fun move(action: PtzAction)

    /** Release the underlying device handles. Safe to call more than once. */
    fun close()
}

enum class PtzAction { PanLeft, PanRight, TiltUp, TiltDown, ZoomIn, ZoomOut, Stop }

/** The binding on every platform without a PTZ backend. */
object NoOpPtzController : PtzController {
    override val isAvailable: Boolean = false
    override val zoom: StateFlow<Int?> = MutableStateFlow(null)
    override fun move(action: PtzAction) = Unit
    override fun close() = Unit
}
