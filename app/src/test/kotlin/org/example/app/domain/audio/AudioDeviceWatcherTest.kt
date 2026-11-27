package org.example.app.domain.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.fakes.FakeAudioInputDeviceProvider.Companion.DEFAULT_DEVICE
import org.example.app.fakes.FakeAudioInputDeviceProvider.Companion.INELIGIBLE_DEVICE
import org.example.app.fakes.FakeAudioInputDeviceProvider.Companion.SECONDARY_DEVICE
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Mic hot-plug merge rule and polling watcher tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioDeviceWatcherTest {

    private fun dev(id: String, eligible: Boolean = true) = AudioInputDevice(id, id, eligible)

    @Test
    fun `merge keeps order of existing devices regardless of provider order`() {
        val merged = mergeDevices(listOf(dev("a"), dev("b")), listOf(dev("b"), dev("a")))
        assertEquals(listOf("a", "b"), merged.map { it.id })
    }

    @Test
    fun `merge appends new devices at the end in provider order`() {
        val merged = mergeDevices(listOf(dev("a")), listOf(dev("z"), dev("a"), dev("y")))
        assertEquals(listOf("a", "z", "y"), merged.map { it.id })
    }

    @Test
    fun `merge removes vanished devices`() {
        val merged = mergeDevices(listOf(dev("a"), dev("b"), dev("c")), listOf(dev("c"), dev("a")))
        assertEquals(listOf("a", "c"), merged.map { it.id })
    }

    @Test
    fun `merge takes the fresh object for existing ids`() {
        val merged = mergeDevices(listOf(dev("a", eligible = true)), listOf(dev("a", eligible = false)))
        assertEquals(listOf(dev("a", eligible = false)), merged)
    }

    @Test
    fun `new device appears after one interval`() = runTest {
        val d = TestCoroutineDispatchers(testScheduler)
        val provider = FakeAudioInputDeviceProvider(listOf(DEFAULT_DEVICE))
        val watcher = AudioDeviceWatcher(provider, provider.availableDevices(), d, 2.seconds)
        watcher.start(backgroundScope)
        runCurrent()
        provider.setDevices(listOf(DEFAULT_DEVICE, SECONDARY_DEVICE))

        advanceTimeBy(1.seconds); runCurrent()
        assertEquals(listOf(DEFAULT_DEVICE), watcher.devices.value)
        advanceTimeBy(1001.milliseconds); runCurrent()
        assertEquals(listOf(DEFAULT_DEVICE, SECONDARY_DEVICE), watcher.devices.value)
    }

    @Test
    fun `full scan is skipped while the fingerprint is unchanged`() = runTest {
        val d = TestCoroutineDispatchers(testScheduler)
        val provider = FakeAudioInputDeviceProvider(listOf(DEFAULT_DEVICE))
        val watcher = AudioDeviceWatcher(provider, listOf(DEFAULT_DEVICE), d, 2.seconds)
        watcher.start(backgroundScope)

        advanceTimeBy(2001.milliseconds); runCurrent()
        assertEquals(1, provider.availableDevicesCalls, "without an initial fingerprint the first poll scans once")
        advanceTimeBy(10.seconds); runCurrent()
        assertEquals(1, provider.availableDevicesCalls)
        assertEquals(6, provider.fingerprintCalls)

        provider.setDevices(listOf(DEFAULT_DEVICE, INELIGIBLE_DEVICE))
        advanceTimeBy(2.seconds); runCurrent()
        assertEquals(2, provider.availableDevicesCalls)
    }

    @Test
    fun `a matching initial fingerprint means no full scan on the first polls`() = runTest {
        val d = TestCoroutineDispatchers(testScheduler)
        val provider = FakeAudioInputDeviceProvider(listOf(DEFAULT_DEVICE))
        val baseline = provider.deviceFingerprint()
        val watcher = AudioDeviceWatcher(provider, listOf(DEFAULT_DEVICE), d, 2.seconds, initialFingerprint = baseline)
        watcher.start(backgroundScope)

        advanceTimeBy(6001.milliseconds); runCurrent()
        assertEquals(0, provider.availableDevicesCalls)

        provider.setDevices(listOf(DEFAULT_DEVICE, SECONDARY_DEVICE))
        advanceTimeBy(2.seconds); runCurrent()
        assertEquals(1, provider.availableDevicesCalls)
        assertEquals(listOf(DEFAULT_DEVICE, SECONDARY_DEVICE), watcher.devices.value)
    }

    @Test
    fun `provider exception does not kill the loop`() = runTest {
        val d = TestCoroutineDispatchers(testScheduler)
        val provider = FakeAudioInputDeviceProvider(listOf(DEFAULT_DEVICE))
        val watcher = AudioDeviceWatcher(provider, listOf(DEFAULT_DEVICE), d, 2.seconds)
        val job = watcher.start(backgroundScope)
        provider.failure = IllegalStateException("boom")

        advanceTimeBy(4001.milliseconds); runCurrent()
        assertEquals(true, job.isActive)

        provider.failure = null
        provider.setDevices(listOf(DEFAULT_DEVICE, SECONDARY_DEVICE))
        advanceTimeBy(2.seconds); runCurrent()
        assertEquals(listOf(DEFAULT_DEVICE, SECONDARY_DEVICE), watcher.devices.value)
    }

    @Test
    fun `refreshNow scans immediately without waiting for the interval`() = runTest {
        val d = TestCoroutineDispatchers(testScheduler)
        val provider = FakeAudioInputDeviceProvider(listOf(DEFAULT_DEVICE))
        val watcher = AudioDeviceWatcher(provider, listOf(DEFAULT_DEVICE), d, 2.seconds)
        provider.setDevices(listOf(DEFAULT_DEVICE, SECONDARY_DEVICE))

        watcher.refreshNow()
        assertEquals(listOf(DEFAULT_DEVICE, SECONDARY_DEVICE), watcher.devices.value)
    }

    @Test
    fun `refreshNow swallows provider failures`() = runTest {
        val d = TestCoroutineDispatchers(testScheduler)
        val provider = FakeAudioInputDeviceProvider(listOf(DEFAULT_DEVICE))
        val watcher = AudioDeviceWatcher(provider, listOf(DEFAULT_DEVICE), d, 2.seconds)
        provider.failure = IllegalStateException("boom")

        watcher.refreshNow()
        assertEquals(listOf(DEFAULT_DEVICE), watcher.devices.value)
    }

    @Test
    fun `loop stops when its scope is cancelled`() = runTest {
        val d = TestCoroutineDispatchers(testScheduler)
        val provider = FakeAudioInputDeviceProvider(listOf(DEFAULT_DEVICE))
        val watcher = AudioDeviceWatcher(provider, listOf(DEFAULT_DEVICE), d, 2.seconds)
        val job = watcher.start(backgroundScope)
        advanceTimeBy(2001.milliseconds); runCurrent()
        val calls = provider.fingerprintCalls
        job.cancel()
        advanceTimeBy(20.seconds); runCurrent()
        assertEquals(calls, provider.fingerprintCalls)
    }
}


