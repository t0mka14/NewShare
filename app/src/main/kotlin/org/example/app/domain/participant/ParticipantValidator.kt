package org.example.app.domain.participant

import org.example.app.domain.config.PatientField

/** Per-field validation failures (§5.5 `ValidateParticipantInfoUseCase` will wrap these). */
sealed interface FieldValidationError {
    data class Required(val fieldName: String) : FieldValidationError
    data class PatternMismatch(val fieldName: String, val regex: String) : FieldValidationError
}

/**
 * Regex/required validation for participant-entered values against server-configured
 * [PatientField] definitions (§5, §6.2). Pure — no I/O; `ValidateParticipantInfoUseCase`
 * (Phase 2) is a thin wrapper that also persists `participant.json`.
 */
object ParticipantValidator {
    /** Empty `regex` means free text (no pattern check); blank values on a non-required
     * field are valid and skip the regex check entirely. */
    fun validateField(field: PatientField, value: String): List<FieldValidationError> {
        val errors = mutableListOf<FieldValidationError>()

        if (field.required && value.isBlank()) {
            errors += FieldValidationError.Required(field.name)
        }

        if (value.isNotEmpty() && field.regex.isNotEmpty() && !Regex(field.regex).matches(value)) {
            errors += FieldValidationError.PatternMismatch(field.name, field.regex)
        }

        return errors
    }

    /** Validates every configured field against [values] (keyed by [PatientField.name]).
     * Only fields with at least one error are present in the result map. */
    fun validateAll(fields: List<PatientField>, values: Map<String, String>): Map<String, List<FieldValidationError>> =
        fields
            .associate { field -> field.name to validateField(field, values[field.name].orEmpty()) }
            .filterValues { it.isNotEmpty() }
}
