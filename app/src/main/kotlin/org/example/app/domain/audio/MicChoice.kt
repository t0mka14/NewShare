package org.example.app.domain.audio

/**
 * The microphone a session opens, and why (§6.2): the device saved in Settings, else the one
 * matching the config's `defaultMicName` ([MicNames]), else any usable device, else the first
 * one listed. Settings preselects the same choice, so what it shows is what a session would use.
 */
data class MicChoice(val device: AudioInputDevice, val source: Source) {
    enum class Source { SAVED, CONFIG, SYSTEM_DEFAULT }

    companion object {
        fun choose(devices: List<AudioInputDevice>, savedDeviceId: String?, configuredMicName: String?): MicChoice? {
            devices.firstOrNull { it.id == savedDeviceId }?.let { return MicChoice(it, Source.SAVED) }
            devices.firstOrNull { it.eligible && MicNames.matches(configuredMicName, it) }
                ?.let { return MicChoice(it, Source.CONFIG) }
            val fallback = devices.firstOrNull { it.eligible } ?: devices.firstOrNull() ?: return null
            return MicChoice(fallback, Source.SYSTEM_DEFAULT)
        }
    }
}
