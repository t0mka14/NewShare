package org.example.app.domain.config

import kotlinx.serialization.Serializable

/**
 * Participant-input field definition pushed from the server config (§5, §6.2). `name` is the
 * stable identifier used as the key in `participant.json`. `regex` validates the entered
 * value (empty means free text). `useInFilename` marks fields that participate in
 * `${patientCode}` composition (see [org.example.app.domain.participant.PatientCodeComposer]).
 */
@Serializable
data class PatientField(
    val name: String,
    val labelKey: String,
    val helpKey: String? = null,
    val placeholder: String? = null,
    val regex: String = "",
    val required: Boolean = false,
    val useInFilename: Boolean = false,
)
