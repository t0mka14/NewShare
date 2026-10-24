package org.example.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.ContinuousSessionRecorder
import org.example.app.domain.audio.RecorderState

interface CalibrationComponent {
    val state: Value<State>

    fun onDeviceSelected(device: AudioInputDevice)
    fun onConfirm()

    /** Re-opens the selected device after [State.micFailed]. */
    fun onRetry()

    data class State(
        val titleKey: String,
        val instructionKeys: List<String>,
        val level: Float = 0f,
        val minLoudness: Double,
        val maxLoudness: Double,
        val inRange: Boolean = false,
        val availableDevices: List<AudioInputDevice> = emptyList(),
        val selectedDevice: AudioInputDevice? = null,
        val deviceLost: Boolean = false,
        /** The recorder is [RecorderState.Failed]: the selected microphone could not be opened. */
        val micFailed: Boolean = false,
        /**
         * A Retry or device re-select failed again. Windows/JavaSound keeps the old microphone
         * access state for the JVM's lifetime, so the examiner is told to restart the app.
         */
        val micRetryFailed: Boolean = false,
        /** Only a [RecorderState.Monitoring] recorder can start writing, so Continue needs it. */
        val canConfirm: Boolean = true,
    )
}

/**
 * §6.2/§8.5 calibration screen. Drives [recorder]'s monitoring-only mode (no timeline
 * involvement — recording hasn't started yet, §8.1). Confirm is forwarded to the caller
 * ([SessionComponent]) which is the one that calls `startWriting` and logs
 * `SESSION_RECORDING_STARTED` (single-writer principle, §5.2).
 */
class DefaultCalibrationComponent(
    componentContext: ComponentContext,
    private val recorder: ContinuousSessionRecorder,
    private val dispatchers: CoroutineDispatchers,
    /** Target band, from `AppSettings.loudnessRange()`. */
    private val loudness: ClosedFloatingPointRange<Double>,
    initialDevice: AudioInputDevice,
    availableDevices: List<AudioInputDevice>,
    private val onConfirmed: () -> Unit,
) : CalibrationComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val _state = MutableValue(
        CalibrationComponent.State(
            // Built-in keys; a config's `strings` may override the wording like any other key.
            titleKey = "calibration.title",
            instructionKeys = listOf("calibration.instructions"),
            minLoudness = loudness.start,
            maxLoudness = loudness.endInclusive,
            availableDevices = availableDevices,
            selectedDevice = initialDevice,
            micFailed = recorder.state.value is RecorderState.Failed,
            canConfirm = recorder.state.value is RecorderState.Monitoring,
        ),
    )
    override val state: Value<CalibrationComponent.State> = _state

    /** Set when the examiner asked to reopen the mic; cleared once it opens (Monitoring). */
    private var reopenRequested = false

    init {
        lifecycle.doOnDestroy { scope.cancel() }

        scope.launch(dispatchers.main) {
            recorder.levels.collect { level ->
                val inRange = level.toDouble() in loudness
                _state.value = _state.value.copy(level = level, inRange = inRange)
            }
        }
        scope.launch(dispatchers.main) {
            recorder.state.collect { rs ->
                if (rs is RecorderState.Monitoring) reopenRequested = false
                _state.value = _state.value.copy(
                    deviceLost = if (rs is RecorderState.Interrupted) true
                    else if (rs is RecorderState.Monitoring) false
                    else _state.value.deviceLost,
                    // Failed is a failed open (§11 AudioError), distinct from losing a working device.
                    micFailed = rs is RecorderState.Failed,
                    micRetryFailed = rs is RecorderState.Failed && reopenRequested,
                    canConfirm = rs is RecorderState.Monitoring,
                )
            }
        }
    }

    override fun onDeviceSelected(device: AudioInputDevice) {
        _state.value = _state.value.copy(selectedDevice = device, deviceLost = false)
        reopen(device)
    }

    override fun onRetry() {
        val device = _state.value.selectedDevice ?: return
        reopen(device)
    }

    private fun reopen(device: AudioInputDevice) {
        reopenRequested = true
        scope.launch(dispatchers.main) {
            // Bootstrap already left the recorder Monitoring (or Interrupted after device loss, or
            // Failed after a failed open); startMonitoring only accepts Idle/Stopped/Failed, so
            // close the current line first.
            recorder.stop()
            recorder.startMonitoring(device)
            // A repeated Failed equals the previous one, so the state collector may not see it.
            if (recorder.state.value is RecorderState.Failed) {
                _state.value = _state.value.copy(micFailed = true, micRetryFailed = true)
            }
        }
    }

    override fun onConfirm() {
        // Writing can only start from Monitoring; a failed or lost mic is handled on this screen.
        if (!_state.value.canConfirm) return
        onConfirmed()
    }
}
