package org.example.app.domain.participant

import org.example.app.domain.config.PatientField

/**
 * Thin wrapper over [ParticipantValidator] (§5.5) — the uniform use-case entry point the
 * participant-info screen calls, kept separate from the pure validator so persistence
 * (writing `participant.json`, owned by `StartSessionUseCase`) can be layered in later
 * without touching call sites. Deliberately has no logic of its own beyond delegation.
 */
class ValidateParticipantInfoUseCase {
    fun execute(
        fields: List<PatientField>,
        values: Map<String, String>,
    ): Map<String, List<FieldValidationError>> = ParticipantValidator.validateAll(fields, values)
}
