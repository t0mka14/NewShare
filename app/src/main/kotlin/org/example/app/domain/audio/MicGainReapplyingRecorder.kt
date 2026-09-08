package org.example.app.domain.audio

import org.example.app.domain.config.RemoteConfig
import java.nio.file.Path

/**
 * Sets the microphone level right before the wrapped recorder opens a device (§13 decision 44):
 * session bootstrap, a device switch on the calibration screen and a device-loss resume all go
 * through [startMonitoring]/[resume], so this is the single trigger for the level. Wired once,
 * in `RootComponent.buildSessionChild`, around whatever `AppContainer.sessionRecorderFactory`
 * produces; the session components never see it.
 */
class MicGainReapplyingRecorder(
    private val delegate: ContinuousSessionRecorder,
    private val applier: MicGainApplier,
    private val activeConfig: () -> RemoteConfig?,
) : ContinuousSessionRecorder by delegate {

    override suspend fun startMonitoring(device: AudioInputDevice) {
        applier.applyBeforeOpen(device, activeConfig())
        delegate.startMonitoring(device)
    }

    override suspend fun resume(device: AudioInputDevice, partFile: Path) {
        applier.applyBeforeOpen(device, activeConfig())
        delegate.resume(device, partFile)
    }
}
