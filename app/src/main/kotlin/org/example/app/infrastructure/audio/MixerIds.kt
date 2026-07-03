package org.example.app.infrastructure.audio

import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Mixer

/**
 * Derives a stable [org.example.app.domain.audio.AudioInputDevice.id] from a
 * `Mixer.Info` (§5.3.1) and resolves it back. Kept as a standalone pure-ish
 * object (the lookup call is the only part that touches `AudioSystem`) so id
 * derivation is unit-testable via a fake `Mixer.Info` subclass.
 */
object MixerIds {
    fun stableId(info: Mixer.Info): String =
        listOf(info.name, info.vendor, info.version).joinToString("|")

    fun findByStableId(id: String): Mixer.Info? =
        AudioSystem.getMixerInfo().firstOrNull { stableId(it) == id }
}
