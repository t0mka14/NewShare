package org.example.app.domain.audio

/**
 * Matches the configured microphone *model* (`RemoteConfig.defaultMicName`) against a device
 * as Java Sound reports it (§6.2, §13 decision 44).
 *
 * The same USB microphone is named differently on every platform — Linux `CODEC [plughw:3,0]`
 * (ALSA card id + slot), Windows `Microphone (USB audio CODEC)` (endpoint role + product), macOS
 * `USB audio CODEC` (product) — so the name alone is no key. What every platform carries is the
 * device's USB product string, in the name (Windows, macOS) or in the description (Linux:
 * `Direct Audio Device: USB audio CODEC, USB Audio, USB Audio`). The configured value is therefore
 * treated as a model string and matched as a case-insensitive substring of name + description.
 * It identifies a model, not a unit: two plugged-in devices of the same model both match.
 *
 * Windows tolerance: the JDK cuts capture-device and port-mixer names to 31 characters there
 * (JDK-7116070, closed "External", never fixed — `DirectSoundCaptureEnumerateW` descriptions
 * and `MIXERCAPS.szPname` are bounded by `MAXPNAMELEN`), which can cut through the model string
 * itself (`Microphone (Sennheiser USB head`). A 31-character name therefore also matches when it
 * *ends with a prefix* of the model string, provided that prefix is at least half of the model
 * and at least [MIN_TRUNCATED_PREFIX] characters — enough to rule out coincidences like a name
 * ending in "USB".
 */
object MicNames {
    /** Length Windows truncates capture/port mixer names to (JDK-7116070). */
    const val WINDOWS_TRUNCATED_NAME_LENGTH = 31

    /** Shortest model-string prefix a truncated Windows name may match on. */
    const val MIN_TRUNCATED_PREFIX = 6

    fun matches(configured: String?, device: AudioInputDevice): Boolean =
        matches(configured, device.name, device.description)

    fun matches(configured: String?, name: String, description: String = ""): Boolean {
        val model = configured?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val haystack = "$name $description"
        if (haystack.contains(model, ignoreCase = true)) return true

        if (name.length != WINDOWS_TRUNCATED_NAME_LENGTH) return false
        val minPrefix = minOf(model.length, maxOf(MIN_TRUNCATED_PREFIX, (model.length + 1) / 2))
        for (k in model.length downTo minPrefix) {
            if (name.endsWith(model.take(k), ignoreCase = true)) return true
        }
        return false
    }
}
