package org.example.app.domain.participant

import java.text.Normalizer

/**
 * The identifier catalogue agreed with the web (config alignment row 7a): well-known
 * `PatientField.name`s whose rendering the app owns — the web sends no options. Every other name
 * is a free-text field validated by its `regex`.
 *
 * Stored values (`participant.json`, `${field.<name>}`) are the raw option strings and ISO dates;
 * option *labels* are the localization keys from [optionLabelKey] (built in, overridable by a
 * config's `strings`).
 */
object PatientFieldCatalogue {
    const val CURRENT_DATE = "current_date"
    const val SEX = "sex"
    const val EDUCATION = "education"
    const val PATIENT_CODE = "patient_code"

    sealed interface FieldKind {
        /** Auto-filled with the examination date (`yyyy-MM-dd`), not editable. */
        data object AutoDate : FieldKind

        /** One of a fixed list of stored values. */
        data class Choice(val options: List<String>) : FieldKind

        /** Free text validated by the field's `regex`. */
        data object Text : FieldKind
    }

    private val choices = mapOf(
        SEX to listOf("male", "female"),
        EDUCATION to listOf("less than upper secondary", "upper secondary and vocational", "tertiary education"),
    )

    fun kindOf(fieldName: String): FieldKind = when (fieldName) {
        CURRENT_DATE -> FieldKind.AutoDate
        in choices -> FieldKind.Choice(choices.getValue(fieldName))
        else -> FieldKind.Text
    }

    /** `patientField.<field>.<option with spaces as _>`, e.g. `patientField.education.tertiary_education`. */
    fun optionLabelKey(fieldName: String, option: String): String =
        "patientField.$fieldName.${option.replace(' ', '_')}"

    private val unsafeCharacters = Regex("[^A-Za-z0-9_-]")
    private val combiningMarks = Regex("\\p{M}+")

    /**
     * Filename-safe (Windows/macOS): accents are stripped but the letter kept (`Novák` → `Novak`,
     * `Čížek` → `Cizek`), then everything still outside `[A-Za-z0-9_-]` is dropped.
     */
    fun sanitize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(unsafeCharacters, "")

    /**
     * The session's label in its folder name, ZIP name and the session lists: the sanitized
     * `patient_code` value, empty when the protocol has no such field.
     */
    fun patientCode(values: Map<String, String>): String = sanitize(values[PATIENT_CODE].orEmpty())
}
