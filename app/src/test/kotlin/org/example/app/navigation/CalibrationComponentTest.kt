package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.audio.AudioError
import org.example.app.domain.audio.AudioInputDevice
import org.example.app.domain.audio.InterruptionReason
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
        val clock = FakeClock()
        val dispatchers = TestCoroutineDispatchers()
        val recorder = FakeContinuousSessionRecorder(clock)
        var confirmed = 0
        var refreshes = 0
        val devices = kotlinx.coroutines.flow.MutableStateFlow(listOf(FakeAudioInputDeviceProvider.DEFAULT_DEVICE, FakeAudioInputDeviceProvider.SECONDARY_DEVICE))
        val picked = mutableListOf<AudioInputDevice>()
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
            loudness = 0.2..0.6,
            initialDevice = device,
            availableDevices = devices,
            refreshDevices = { refreshes++ },
            onConfirmed = { confirmed++ },
            onDevicePicked = { picked += it },
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

    @Test
    fun `a failed mic open shows the mic error, not the device-lost state, and blocks Continue`() {
        val h = Harness()

        h.recorder.simulateFailure(AudioError.NoSupportedPcmFormat(h.device.id))
        h.dispatchers.scheduler.advanceUntilIdle()

        val state = h.component.state.value
        assertTrue(state.micFailed)
        assertFalse(state.deviceLost)
        assertFalse(state.canConfirm)

        h.component.onConfirm()
        assertEquals(0, h.confirmed)
    }

    @Test
    fun `retry reopens the selected device and clears the mic error`() {
        val h = Harness()
        h.recorder.simulateFailure(AudioError.NoSupportedPcmFormat(h.device.id))
        h.dispatchers.scheduler.advanceUntilIdle()

        h.component.onRetry()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertFalse(h.component.state.value.micFailed)
        assertTrue(h.component.state.value.canConfirm)
        assertEquals(listOf(h.device, h.device), h.recorder.monitoringStarts)

        h.component.onConfirm()
        assertEquals(1, h.confirmed)
    }

    @Test
    fun `selecting another device after a failed open clears the mic error`() {
        val h = Harness()
        h.recorder.simulateFailure(AudioError.DeviceUnavailable(h.device.id))
        h.dispatchers.scheduler.advanceUntilIdle()
        assertTrue(h.component.state.value.micFailed)

        h.component.onDeviceSelected(h.secondary)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertFalse(h.component.state.value.micFailed)
        assertEquals(h.secondary, h.recorder.monitoringStarts.last())
    }

    @Test
    fun `first mic failure shows no restart hint`() {
        val h = Harness()
        h.recorder.simulateFailure(AudioError.DeviceUnavailable(h.device.id))
        h.dispatchers.scheduler.advanceUntilIdle()

        assertTrue(h.component.state.value.micFailed)
        assertFalse(h.component.state.value.micRetryFailed)
    }

    @Test
    fun `retry that fails again shows the restart hint`() {
        val h = Harness()
        h.recorder.simulateFailure(AudioError.DeviceUnavailable(h.device.id))
        h.dispatchers.scheduler.advanceUntilIdle()

        h.recorder.failNextMonitoring = true
        h.component.onRetry()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertTrue(h.component.state.value.micFailed)
        assertTrue(h.component.state.value.micRetryFailed)
    }

    @Test
    fun `retry that succeeds clears the error and the restart hint`() {
        val h = Harness()
        h.recorder.simulateFailure(AudioError.DeviceUnavailable(h.device.id))
        h.dispatchers.scheduler.advanceUntilIdle()
        h.recorder.failNextMonitoring = true
        h.component.onRetry()
        h.dispatchers.scheduler.advanceUntilIdle()
        assertTrue(h.component.state.value.micRetryFailed)

        h.recorder.failNextMonitoring = false
        h.component.onRetry()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertFalse(h.component.state.value.micFailed)
        assertFalse(h.component.state.value.micRetryFailed)
    }

    @Test
    fun `F-062 an explicit device pick is reported for saving, the initial selection is not`() {
        val h = Harness()
        h.dispatchers.scheduler.advanceUntilIdle()
        assertTrue(h.picked.isEmpty())

        h.component.onDeviceSelected(h.secondary)
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(listOf(h.secondary), h.picked)
    }

    @Test
    fun `device list in state follows the live flow and the selection survives its removal`() {
        val h = Harness()
        val plugged = AudioInputDevice(id = "usb2", name = "New USB Mic", eligible = true)

        h.devices.value = listOf(h.device, h.secondary, plugged)
        h.dispatchers.scheduler.advanceUntilIdle()
        assertEquals(listOf(h.device, h.secondary, plugged), h.component.state.value.availableDevices)

        h.devices.value = listOf(h.secondary)
        h.dispatchers.scheduler.advanceUntilIdle()
        assertEquals(listOf(h.secondary), h.component.state.value.availableDevices)
        assertEquals(h.device, h.component.state.value.selectedDevice, "a vanished selection stays shown as selected")
    }

    @Test
    fun `opening the device menu triggers a device refresh`() {
        val h = Harness()
        assertEquals(0, h.refreshes)
        h.component.onDeviceMenuOpened()
        h.dispatchers.scheduler.advanceUntilIdle()
        assertEquals(1, h.refreshes)
    }
}
