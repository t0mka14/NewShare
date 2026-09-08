package org.example.app.infrastructure.audio

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PortMixersTest {

    @Test
    fun `Windows and macOS port mixers carry the capture mixer's name`() {
        assertTrue(PortMixers.belongsTo("Port Microphone (USB audio CODEC)", "Microphone (USB audio CODEC)"))
        assertTrue(PortMixers.belongsTo("Port USB audio CODEC", "USB audio CODEC"))
        assertTrue(PortMixers.belongsTo("Port microphone (usb audio codec)", "Microphone (USB audio CODEC) "))
    }

    @Test
    fun `ALSA port mixers are paired by card id and number`() {
        assertTrue(PortMixers.belongsTo("Port CODEC [hw:3]", "CODEC [plughw:3,0]"))
        assertTrue(PortMixers.belongsTo("Port Generic_1 [hw:2]", "Generic_1 [plughw:2,0]"))
        assertFalse(PortMixers.belongsTo("Port Generic [hw:1]", "Generic_1 [plughw:2,0]"))
        assertFalse(PortMixers.belongsTo("Port CODEC [hw:4]", "CODEC [plughw:3,0]"))
    }

    @Test
    fun `other mixers never qualify`() {
        assertFalse(PortMixers.belongsTo("Microphone (USB audio CODEC)", "Microphone (USB audio CODEC)"))
        assertFalse(PortMixers.belongsTo("Port Speakers (USB audio CODEC)", "Microphone (USB audio CODEC)"))
    }
}
