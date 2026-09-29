package org.example.app.domain.config

import org.example.app.domain.participant.PatientFieldCatalogue

/**
 * Renders a protocol's `recordingsFileName` template (§3, §8.8) into a concrete clip base
 * name. Supported variables: `${installationId}`, `${taskIndex}`, `${task.subtype}`,
 * `${repetition}` and `${field.<name>}` — the value entered for the patient field `<name>`,
 * sanitized to `[A-Za-z0-9_-]` (config alignment row 8); a field with no value renders empty.
 *
 * `taskIndex` and `repetition` are passed in by the caller (the processor, §8.8) — this
 * function does no expansion or lookup itself, it is pure string substitution so it can be
 * unit-tested without a protocol/timeline in scope.
 */
object RecordingsFileNameRenderer {
    /** Matches `${field.<name>}`; group 1 is the field name. */
    val FIELD_VARIABLE = Regex("""\$\{field\.([^}]*)}""")

    fun render(
        template: String,
        installationId: String,
        fieldValues: Map<String, String>,
        taskIndex: Int,
        subtype: String,
        repetition: Int,
    ): String = template
        .replace(FIELD_VARIABLE) { match -> PatientFieldCatalogue.sanitize(fieldValues[match.groupValues[1]].orEmpty()) }
        .replace("\${installationId}", installationId)
        .replace("\${taskIndex}", taskIndex.toString())
        .replace("\${task.subtype}", subtype)
        .replace("\${repetition}", repetition.toString())
}
