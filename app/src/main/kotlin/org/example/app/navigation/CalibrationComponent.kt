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
import org.example.app.domain.config.CalibrationTask

interface CalibrationComponent {
    val state: Value<State>

    fun onDeviceSelected(device: AudioInputDevice)
    fun onConfirm()

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
    private val calibrationTask: CalibrationTask,
    initialDevice: AudioInputDevice,
    availableDevices: List<AudioInputDevice>,
    private val onConfirmed: () -> Unit,
) : CalibrationComponent, ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val _state = MutableValue(
        CalibrationComponent.State(
            titleKey = calibrationTask.titleKey,
            instructionKeys = calibrationTask.instructionKeys,
            minLoudness = calibrationTask.minLoudness,
            maxLoudness = calibrationTask.maxLoudness,
            availableDevices = availableDevices,
            selectedDevice = initialDevice,
        ),
    )
    override val state: Value<CalibrationComponent.State> = _state

    init {
        lifecycle.doOnDestroy { scope.cancel() }

        scope.launch(dispatchers.main) {
            recorder.levels.collect { level ->
                val inRange = level >= calibrationTask.minLoudness && level <= calibrationTask.maxLoudness
                _state.value = _state.value.copy(level = level, inRange = inRange)
            }
        }
        scope.launch(dispatchers.main) {
            recorder.state.collect { rs ->
                if (rs is RecorderState.Interrupted) {
                    _state.value = _state.value.copy(deviceLost = true)
                } else if (rs is RecorderState.Monitoring) {
                    _state.value = _state.value.copy(deviceLost = false)
                }
            }
        }
    }

    override fun onDeviceSelected(device: AudioInputDevice) {
        _state.value = _state.value.copy(selectedDevice = device, deviceLost = false)
        scope.launch(dispatchers.main) {
            recorder.startMonitoring(device)
        }
    }

    override fun onConfirm() {
        onConfirmed()
    }
}
