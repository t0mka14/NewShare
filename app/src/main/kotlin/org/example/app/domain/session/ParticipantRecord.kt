package org.example.app.domain.session

import kotlinx.serialization.Serializable

/**
 * `participant.json` (§8.10): written once at session start, never updated afterward.
 * `fields` is keyed by [org.example.app.domain.config.PatientField.name]; values are the raw
 * (validated, unsanitized) participant entry — sanitization to `[A-Za-z0-9_-]` only happens
 * when composing `${patientCode}` for filenames (see
 * [org.example.app.domain.participant.PatientCodeComposer]), never for the persisted value
 * itself.
 */
@Serializable
data class ParticipantRecord(
    val version: Int = 1,
    val fields: Map<String, String>,
    val createdAt: String,
)
