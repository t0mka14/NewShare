package org.example.app.domain.audio

import java.nio.file.Path

/**
 * Reports every device the wrapped recorder successfully opens — session bootstrap, a calibration
 * switch, a device-loss resume all pass through [startMonitoring]/[resume] — so the session can log
 * which microphone was *actually* used without the screens that trigger the open knowing about it.
 * Same trick as [MicGainReapplyingRecorder]; wrap this one outside it so the gain it sets is already
 * in force when [onOpened] reads it back.
 */
class DeviceOpenReportingRecorder(
    private val delegate: ContinuousSessionRecorder,
    private val onOpened: suspend (AudioInputDevice, CaptureFormat?) -> Unit,
) : ContinuousSessionRecorder by delegate {

    override suspend fun startMonitoring(device: AudioInputDevice) {
        delegate.startMonitoring(device)
        if (delegate.state.value == RecorderState.Monitoring) onOpened(device, delegate.captureFormat.value)
    }

    override suspend fun resume(device: AudioInputDevice, partFile: Path) {
        delegate.resume(device, partFile)
        if (delegate.state.value == RecorderState.Writing) onOpened(device, delegate.captureFormat.value)
    }
}