package org.example.app.domain.config

import kotlinx.serialization.Serializable

/** Live recording feedback on VOCAL task screens (§6.2). Both consume `levels`. */
@Serializable
enum class IndicatorType { CIRCLE, WAVEFORM }

/**
 * A named, ordered list of configured tasks (§3). Task numbering ("task 3 of 10") is derived
 * from list position, not stored.
 *
 * `recordingsFileName` is the authoritative clip filename template; it must include
 * `${taskIndex}` (validated by [ConfigValidator], §3/§6.2) so that two tasks with the same
 * subtype cannot collide.
 *
 * `protocolInstructionsPdfUrl` is the URL of this protocol's instruction manual, opened by the
 * main menu's "Get protocol PDF" button — a URL, never a filesystem path, so the config stays
 * machine-independent. Blank or absent disables the button for this protocol.
 */
@Serializable
data class Protocol(
    val name: String,
    val protocolInstructionsPdfUrl: String? = null,
    val recordingsFileName: String,
    val tasks: List<Task> = emptyList(),
)

/**
 * Top-level remote configuration (§2). Decoded via [ConfigDecoder], checked by
 * [ConfigValidator]. This same model is persisted verbatim as a session's
 * `task_configuration_snapshot.json` (§8.10) — later config pushes never affect a running or
 * recorded session.
 */
@Serializable
data class RemoteConfig(
    val schemaVersion: Int,
    val configVersion: String,
    val defaultLanguage: String,
    val languages: List<String> = emptyList(),
    /**
     * The microphone *model* (its USB product string, e.g. `USB audio CODEC`) the session
     * records from when Settings has no saved device, and the target of [defaultMicGain]
     * (§6.2, §13 decision 44). A device saved in Settings wins. Matched with `MicNames.matches`
     * as a substring of the device's name + description, since each platform names the same
     * device differently, and tolerant of the 31-character truncation Java applies on Windows.
     */
    val defaultMicName: String? = null,
    /**
     * Input level 0..100 (the Windows sound panel's units) set on the device matching
     * [defaultMicName] right before it is opened, unless a level was set on the Settings slider
     * (§13 decision 44). Validated by [ConfigValidator]; null leaves the OS level alone.
     */
    val defaultMicGain: Int? = null,
    val enableEditor: Boolean = false,
    val indicatorType: IndicatorType = IndicatorType.CIRCLE,
    val patientFields: List<PatientField> = emptyList(),
    val protocols: List<Protocol> = emptyList(),
    /** `strings.<lang>.<key> -> value` (§6). */
    val strings: Map<String, Map<String, String>> = emptyMap(),
)
