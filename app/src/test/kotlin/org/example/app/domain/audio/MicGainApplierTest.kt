package org.example.app.domain.audio

import kotlinx.coroutines.runBlocking
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.settings.AppSettings
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeAudioInputGainControl
import org.example.app.fakes.ImmediateCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MicGainApplierTest {

    private val configuredMic = AudioInputDevice(id = "usb", name = "USBAudioDevice", eligible = true)
    private val otherMic = AudioInputDevice(id = "other", name = "Built-in Microphone", eligible = true)

    private fun config(micName: String? = "USBAudioDevice", gain: Int? = 63) = RemoteConfig(
        schemaVersion = 1,
        configVersion = "v1",
        defaultLanguage = "en",
        defaultMicName = micName,
        defaultMicGain = gain,
    )

    private class Harness(
        val control: FakeAudioInputGainControl = FakeAudioInputGainControl(),
        val settings: FakeAppSettingsRepository = FakeAppSettingsRepository(),
    ) {
        val applier = MicGainApplier(control, settings, ImmediateCoroutineDispatchers())
    }

    @Test
    fun `config gain applies to the device matching defaultMicName`() = runBlocking {
        val h = Harness()

        h.applier.applyBeforeOpen(configuredMic, config())

        assertEquals(listOf("USBAudioDevice" to 63), h.control.setCalls)
    }

    @Test
    fun `config gain is not applied to a device with another name`() = runBlocking {
        val h = Harness()

        h.applier.applyBeforeOpen(otherMic, config())

        assertTrue(h.control.setCalls.isEmpty())
    }

    @Test
    fun `config gain matches a Windows-truncated device name`() = runBlocking {
        val h = Harness()
        val truncated = AudioInputDevice(id = "t", name = "Microphone (Sennheiser USB head", eligible = true)

        h.applier.applyBeforeOpen(truncated, config(micName = "Microphone (Sennheiser USB headset)"))

        assertEquals(listOf(truncated.name to 63), h.control.setCalls)
    }

    @Test
    fun `config gain finds the configured model however the platform names the device`() = runBlocking {
        val h = Harness()
        val linux = AudioInputDevice(
            id = "l", name = "CODEC [plughw:3,0]", eligible = true,
            description = "Direct Audio Device: USB audio CODEC, USB Audio, USB Audio",
        )

        h.applier.applyBeforeOpen(linux, config(micName = "USB audio CODEC"))

        assertEquals(listOf("CODEC [plughw:3,0]" to 63), h.control.setCalls)
    }

    @Test
    fun `a local override wins over the config and applies to any device`() = runBlocking {
        val h = Harness()
        h.settings.write(AppSettings(micGain = 40))

        h.applier.applyBeforeOpen(otherMic, config())

        assertEquals(listOf("Built-in Microphone" to 40), h.control.setCalls)
        assertEquals(40, h.applier.effectiveGain(configuredMic, config()))
    }

    @Test
    fun `nothing is applied without a config gain or an override`() = runBlocking {
        val h = Harness()

        h.applier.applyBeforeOpen(configuredMic, config(gain = null))
        h.applier.applyBeforeOpen(configuredMic, null)

        assertTrue(h.control.setCalls.isEmpty())
        assertNull(h.applier.effectiveGain(configuredMic, null))
    }

    @Test
    fun `values are clamped to 0-100`() = runBlocking {
        val h = Harness()
        h.settings.write(AppSettings(micGain = 250))

        h.applier.applyBeforeOpen(configuredMic, null)

        assertEquals(listOf("USBAudioDevice" to 100), h.control.setCalls)
    }

    @Test
    fun `a failing sound subsystem is reported, never thrown`() = runBlocking {
        val h = Harness(control = FakeAudioInputGainControl(throwOnSet = true))

        val result = h.applier.set(configuredMic, 50)

        assertTrue(result is GainApplyResult.Failed)
    }

    @Test
    fun `read returns the device level or null when unknown`() = runBlocking {
        val h = Harness(control = FakeAudioInputGainControl(levels = mutableMapOf("USBAudioDevice" to 63)))

        assertEquals(63, h.applier.read(configuredMic))
        assertNull(h.applier.read(otherMic))
    }
}
