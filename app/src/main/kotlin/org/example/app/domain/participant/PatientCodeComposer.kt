package org.example.app.domain.participant

import org.example.app.domain.config.PatientField

/**
 * Composes the `${patientCode}` filename-template variable (§6.2): values of the
 * `useInFilename` fields, sanitized to `[A-Za-z0-9_-]` (Windows/macOS-safe), joined with `_`
 * in config (declaration) order.
 */
object PatientCodeComposer {
    private val unsafeCharacters = Regex("[^A-Za-z0-9_-]")

    /** [values] keyed by [PatientField.name]; missing values are treated as empty strings. */
    fun compose(fields: List<PatientField>, values: Map<String, String>): String =
        fields
            .filter { it.useInFilename }
            .joinToString("_") { field -> sanitize(values[field.name].orEmpty()) }

    fun sanitize(value: String): String = value.replace(unsafeCharacters, "")
}
