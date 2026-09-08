package org.example.app.domain.audio

import kotlinx.coroutines.runBlocking
import org.example.app.domain.config.RemoteConfig
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeAudioInputGainControl
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.ImmediateCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class MicGainReapplyingRecorderTest {

    private val mic = AudioInputDevice(id = "usb", name = "USBAudioDevice", eligible = true)
    private val config = RemoteConfig(
        schemaVersion = 1,
        configVersion = "v1",
        defaultLanguage = "en",
        defaultMicName = "USBAudioDevice",
        defaultMicGain = 63,
    )

    private class Harness(config: RemoteConfig?) {
        val control = FakeAudioInputGainControl()
        val delegate = FakeContinuousSessionRecorder(FakeClock())

        /** How many gain writes had happened when the delegate was asked to open the device. */
        var writesSeenAtOpen = -1

        private val orderProbe = object : ContinuousSessionRecorder by delegate {
            override suspend fun startMonitoring(device: AudioInputDevice) {
                writesSeenAtOpen = control.setCalls.size
                delegate.startMonitoring(device)
            }

            override suspend fun resume(device: AudioInputDevice, partFile: Path) {
                writesSeenAtOpen = control.setCalls.size
                delegate.resume(device, partFile)
            }
        }

        val recorder: ContinuousSessionRecorder = MicGainReapplyingRecorder(
            delegate = orderProbe,
            applier = MicGainApplier(control, FakeAppSettingsRepository(), ImmediateCoroutineDispatchers()),
            activeConfig = { config },
        )
    }

    @Test
    fun `startMonitoring applies the gain before the device is opened`() = runBlocking {
        val h = Harness(config)

        h.recorder.startMonitoring(mic)

        assertEquals(listOf("USBAudioDevice" to 63), h.control.setCalls)
        assertEquals(1, h.writesSeenAtOpen)
        assertEquals(listOf(mic), h.delegate.monitoringStarts)
        assertEquals(RecorderState.Monitoring, h.recorder.state.value)
    }

    @Test
    fun `resume applies the gain again for the device it reopens`() = runBlocking {
        val h = Harness(config)
        val part = Path.of("session_master.part2.wav")

        h.recorder.startMonitoring(mic)
        h.recorder.startWriting(Path.of("session_master.wav"))
        h.recorder.resume(mic, part)

        assertEquals(2, h.control.setCalls.size)
        assertEquals(2, h.writesSeenAtOpen)
        assertEquals(part, h.delegate.resumeCalls.single().partFile)
    }

    @Test
    fun `startWriting and stop never touch the gain`() = runBlocking {
        val h = Harness(config)

        h.recorder.startMonitoring(mic)
        h.recorder.startWriting(Path.of("session_master.wav"))
        h.recorder.stop()

        assertEquals(1, h.control.setCalls.size)
        assertEquals(1, h.delegate.stopCallCount)
    }

    @Test
    fun `without a config the device opens with its level untouched`() = runBlocking {
        val h = Harness(config = null)

        h.recorder.startMonitoring(mic)

        assertTrue(h.control.setCalls.isEmpty())
        assertEquals(listOf(mic), h.delegate.monitoringStarts)
    }
}
