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
 */
@Serializable
data class Protocol(
    val name: String,
    val manualFilePath: String? = null,
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
    /** Hint only — the locally selected device in Settings wins (§6.2). */
    val defaultMicName: String? = null,
    val enableEditor: Boolean = false,
    val indicatorType: IndicatorType = IndicatorType.CIRCLE,
    val patientFields: List<PatientField> = emptyList(),
    val protocols: List<Protocol> = emptyList(),
    /** `strings.<lang>.<key> -> value` (§6). */
    val strings: Map<String, Map<String, String>> = emptyMap(),
)
