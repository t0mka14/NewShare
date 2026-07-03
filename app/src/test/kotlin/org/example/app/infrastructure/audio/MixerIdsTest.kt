package org.example.app.infrastructure.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import javax.sound.sampled.Mixer

/**
 * `Mixer.Info`'s constructor is `protected`, so a real one can't be instantiated
 * without hardware enumeration — but it *can* be subclassed, which is enough to
 * unit-test id derivation without touching `AudioSystem`.
 */
private class FakeMixerInfo(name: String, vendor: String, description: String, version: String) :
    Mixer.Info(name, vendor, description, version)

class MixerIdsTest {

    @Test
    fun `stableId combines name, vendor and version`() {
        val info = FakeMixerInfo("Built-in Microphone", "Acme", "desc", "1.0")
        assertEquals("Built-in Microphone|Acme|1.0", MixerIds.stableId(info))
    }

    @Test
    fun `stableId is identical across two Info instances describing the same device`() {
        val a = FakeMixerInfo("USB Mic", "Vendor", "d1", "2.0")
        val b = FakeMixerInfo("USB Mic", "Vendor", "d2 (different description)", "2.0")
        assertEquals(MixerIds.stableId(a), MixerIds.stableId(b), "description must not affect the stable id")
    }

    @Test
    fun `stableId differs when name, vendor or version differ`() {
        val base = FakeMixerInfo("Mic", "Vendor", "d", "1.0")
        val differentName = FakeMixerInfo("Other Mic", "Vendor", "d", "1.0")
        val differentVendor = FakeMixerInfo("Mic", "Other Vendor", "d", "1.0")
        val differentVersion = FakeMixerInfo("Mic", "Vendor", "d", "2.0")

        assertNotEquals(MixerIds.stableId(base), MixerIds.stableId(differentName))
        assertNotEquals(MixerIds.stableId(base), MixerIds.stableId(differentVendor))
        assertNotEquals(MixerIds.stableId(base), MixerIds.stableId(differentVersion))
    }
}
