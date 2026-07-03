package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.InterruptionReason
import org.example.app.domain.config.CalibrationTask
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeContinuousSessionRecorder
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalibrationComponentTest {

    private class Harness {
        val calibrationTask = CalibrationTask(
            titleKey = "calibration.title",
            optimalLoudness = listOf(0.2, 0.6),
        )
        val clock = FakeClock()
        val dispatchers = TestCoroutineDispatchers()
        val recorder = FakeContinuousSessionRecorder(clock)
        var confirmed = 0
        val device = FakeAudioInputDeviceProvider.DEFAULT_DEVICE
        val secondary = FakeAudioInputDeviceProvider.SECONDARY_DEVICE

        init {
            // Mirrors real usage: SessionComponent's bootstrap already called startMonitoring
            // on the initial device (to negotiate the capture format) before constructing
            // CalibrationComponent — this component only reacts to re-selection (§8.5).
            kotlinx.coroutines.runBlocking { recorder.startMonitoring(device) }
        }

        val component: CalibrationComponent = DefaultCalibrationComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            recorder = recorder,
            dispatchers = dispatchers,
            calibrationTask = calibrationTask,
            initialDevice = device,
            availableDevices = listOf(device, secondary),
            onConfirmed = { confirmed++ },
        )
    }

    @Test
    fun `level updates and in-range flag follow the optimalLoudness band`() {
        val h = Harness()

        h.recorder.emitLevel(0.05f)
        h.dispatchers.scheduler.advanceUntilIdle()
        assertFalse(h.component.state.value.inRange)
        assertEquals(0.05f, h.component.state.value.level)

        h.recorder.emitLevel(0.4f)
        h.dispatchers.scheduler.advanceUntilIdle()
        assertTrue(h.component.state.value.inRange)

        h.recorder.emitLevel(0.9f)
        h.dispatchers.scheduler.advanceUntilIdle()
        assertFalse(h.component.state.value.inRange)
    }

    @Test
    fun `confirm forwards to the caller without touching the recorder itself`() {
        val h = Harness()
        h.component.onConfirm()
        assertEquals(1, h.confirmed)
        assertEquals(0, h.recorder.writingStarts.size) // startWriting is SessionComponent's job, not calibration's
    }

    @Test
    fun `device loss while monitoring flags the error and re-selection restarts monitoring`() {
        val h = Harness()

        h.recorder.simulateInterruption(InterruptionReason.DEVICE_LOST)
        h.dispatchers.scheduler.advanceUntilIdle()
        assertTrue(h.component.state.value.deviceLost)

        h.component.onDeviceSelected(h.secondary)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertFalse(h.component.state.value.deviceLost)
        assertEquals(h.secondary, h.component.state.value.selectedDevice)
        assertEquals(listOf(h.device, h.secondary), h.recorder.monitoringStarts)
    }
}
