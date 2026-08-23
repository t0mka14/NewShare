package org.example.app.fakes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.app.domain.video.PtzAction
import org.example.app.domain.video.PtzController

/**
 * Records the PTZ actions a screen sends. [isAvailable] defaults to false, matching every
 * platform without a PTZ backend — a test that wants controls has to ask for them.
 */
class FakePtzController(
    override val isAvailable: Boolean = false,
) : PtzController {

    private val _zoom = MutableStateFlow<Int?>(null)
    override val zoom: StateFlow<Int?> = _zoom

    val actions = mutableListOf<PtzAction>()
    var closeCallCount = 0
        private set

    override fun move(action: PtzAction) {
        actions += action
    }

    override fun close() {
        closeCallCount++
    }

    fun setZoom(value: Int?) {
        _zoom.value = value
    }
}
