package org.example.app.domain.config

import kotlinx.serialization.Serializable

/**
 * Participant-input field definition, one of a protocol's `patientFields` (config alignment rows
 * 6/7). `name` is the stable identifier used as the key in `participant.json` and in the
 * `${field.<name>}` filename variable; catalogue names get app-side rendering (see
 * [org.example.app.domain.participant.PatientFieldCatalogue]). `regex` validates free-text
 * values (empty means no pattern check). `helpKey` is always sent; its string may be empty.
 */
@Serializable
data class PatientField(
    val name: String,
    val labelKey: String,
    val helpKey: String = "",
    val placeholder: String = "",
    val regex: String = "",
    val required: Boolean = false,
)
