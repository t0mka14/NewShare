package org.example.app.domain.config

/**
 * Renders a protocol's `recordingsFileName` template (§3, §8.8) into a concrete clip base
 * name. Supported variables: `${installationId}`, `${patientCode}`, `${taskIndex}`,
 * `${task.subtype}`, `${repetition}`.
 *
 * `taskIndex` and `repetition` are passed in by the caller (the processor, §8.8) — this
 * function does no expansion or lookup itself, it is pure string substitution so it can be
 * unit-tested without a protocol/timeline in scope.
 */
object RecordingsFileNameRenderer {
    fun render(
        template: String,
        installationId: String,
        patientCode: String,
        taskIndex: Int,
        subtype: String,
        repetition: Int,
    ): String = template
        .replace("\${installationId}", installationId)
        .replace("\${patientCode}", patientCode)
        .replace("\${taskIndex}", taskIndex.toString())
        .replace("\${task.subtype}", subtype)
        .replace("\${repetition}", repetition.toString())
}
