package org.example.app.domain.audio

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MicNamesTest {

    private val model = "USB audio CODEC"

    // The same Burr-Brown USB device as each platform's Java Sound reports it.
    private val linux = AudioInputDevice(
        id = "l", name = "CODEC [plughw:3,0]", eligible = true,
        description = "Direct Audio Device: USB audio CODEC, USB Audio, USB Audio",
    )
    private val windows = AudioInputDevice(id = "w", name = "Microphone (USB audio CODEC)", eligible = true, description = "DirectSound Capture")
    private val macos = AudioInputDevice(id = "m", name = "USB audio CODEC", eligible = true, description = "Direct Audio Device: USB audio CODEC")

    @Test
    fun `one model string matches the same device on every platform`() {
        assertTrue(MicNames.matches(model, linux))
        assertTrue(MicNames.matches(model, windows))
        assertTrue(MicNames.matches(model, macos))
    }

    @Test
    fun `a different model does not match`() {
        assertFalse(MicNames.matches("Sennheiser USB headset", linux))
        assertFalse(MicNames.matches("Sennheiser USB headset", windows))
        assertFalse(MicNames.matches("Realtek ALC289", linux))
    }

    @Test
    fun `exact device names still match, ignoring case and surrounding whitespace`() {
        assertTrue(MicNames.matches("USBAudioDevice", "USBAudioDevice"))
        assertTrue(MicNames.matches("  usbaudiodevice ", "USBAudioDevice"))
        assertTrue(MicNames.matches("USBAudioDevice", " USBAudioDevice\t"))
    }

    @Test
    fun `a 31-char Windows name cut through the model still matches`() {
        val full = "Microphone (Sennheiser USB headset)" // 35 chars → Java reports the first 31
        assertTrue(MicNames.matches("Sennheiser USB headset", full.take(31)))
        assertTrue(MicNames.matches(full, full.take(31)))
    }

    @Test
    fun `a 31-char name whose cut lands on a space still matches`() {
        val full = "Microphone (Sennheiser USB hea dset)" // char 31 is a space
        assertTrue(MicNames.matches("Sennheiser USB hea dset", full.take(31)))
    }

    @Test
    fun `a truncated name must keep at least half of the model, and at least six characters`() {
        // "…(Sennheiser USB" keeps 14 of 22 chars → ok
        assertTrue(MicNames.matches("Sennheiser USB headset", "Microphone Arra (Sennheiser USB"))
        // "…Sennh" keeps 5 of 22 → no
        assertFalse(MicNames.matches("Sennheiser USB headset", "Microphone Array Front x (Sennh"))
        // a name merely ending in "USB" must not match a model starting with "USB …"
        assertFalse(MicNames.matches("USB audio CODEC", "Microphone (Logitech Camera USB"))
    }

    @Test
    fun `names shorter than 31 chars never match by prefix`() {
        assertFalse(MicNames.matches("Sennheiser USB headset", "Microphone (Sennheiser"))
    }

    @Test
    fun `a short generic model string does not match a device that merely contains it`() {
        assertFalse(MicNames.matches("Microphone", linux)) // no "Microphone" anywhere in the Linux strings
    }

    @Test
    fun `null or blank configured name never matches`() {
        assertFalse(MicNames.matches(null, windows))
        assertFalse(MicNames.matches("   ", windows))
    }
}
