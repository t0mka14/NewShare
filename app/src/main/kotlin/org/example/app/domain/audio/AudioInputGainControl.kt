package org.example.app.domain.audio

/**
 * System port for the OS-level input gain ("level") of a microphone (§13 decision 44).
 *
 * Distinct from [ContinuousSessionRecorder]: the capture line the recorder opens exposes no
 * volume control on Windows, so the level lives on the device itself, addressed by name.
 * Implementations are blocking (they touch the sound subsystem) — callers go through
 * [MicGainApplier], which runs them on the IO dispatcher.
 */
interface AudioInputGainControl {
    /** Set the input level of the device named [deviceName] to [percent] (0..100). */
    fun setInputGain(deviceName: String, percent: Int): GainApplyResult

    /** Current input level (0..100) of the device named [deviceName]; null when it cannot be read. */
    fun readInputGain(deviceName: String): Int?
}

sealed interface GainApplyResult {
    /** [controlsSet] volume controls were written on the mixer named [mixerName]. */
    data class Applied(val mixerName: String, val controlsSet: Int) : GainApplyResult

    /** No port mixer belongs to the device (`PortMixers.belongsTo`). */
    data object NoMatchingDevice : GainApplyResult

    /** A mixer matched but none of its input ports carries a volume control. */
    data class NoVolumeControl(val mixerName: String) : GainApplyResult

    data class Failed(val detail: String) : GainApplyResult
}
