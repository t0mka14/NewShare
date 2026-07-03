package org.example.app.domain.participant

import org.example.app.domain.config.PatientField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParticipantValidatorTest {

    private val visitNumberField = PatientField(
        name = "visitNumber",
        labelKey = "patient_visit_number_label",
        regex = "V\\d+",
        required = true,
        useInFilename = true,
    )

    private val optionalNotesField = PatientField(
        name = "notes",
        labelKey = "patient_notes_label",
        regex = "",
        required = false,
        useInFilename = false,
    )

    @Test
    fun `valid value passes with no errors`() {
        assertTrue(ParticipantValidator.validateField(visitNumberField, "V123").isEmpty())
    }

    @Test
    fun `blank required field yields a Required error`() {
        val errors = ParticipantValidator.validateField(visitNumberField, "")
        assertEquals(listOf(FieldValidationError.Required("visitNumber")), errors)
    }

    @Test
    fun `non-matching value on a required field yields a PatternMismatch error`() {
        val errors = ParticipantValidator.validateField(visitNumberField, "not-a-visit")
        assertEquals(listOf(FieldValidationError.PatternMismatch("visitNumber", "V\\d+")), errors)
    }

    @Test
    fun `blank optional field is valid - regex not free text is not enforced on empty`() {
        assertTrue(ParticipantValidator.validateField(optionalNotesField, "").isEmpty())
    }

    @Test
    fun `empty regex means free text - any non-blank value is valid`() {
        assertTrue(ParticipantValidator.validateField(optionalNotesField, "anything at all!").isEmpty())
    }

    @Test
    fun `validateAll only reports fields with errors`() {
        val fields = listOf(visitNumberField, optionalNotesField)
        val result = ParticipantValidator.validateAll(fields, mapOf("visitNumber" to "bad", "notes" to "fine"))

        assertEquals(setOf("visitNumber"), result.keys)
        assertTrue(result.getValue("visitNumber").isNotEmpty())
    }

    @Test
    fun `validateAll treats a missing field value as blank`() {
        val result = ParticipantValidator.validateAll(listOf(visitNumberField), emptyMap())
        assertEquals(listOf(FieldValidationError.Required("visitNumber")), result.getValue("visitNumber"))
    }

    @Test
    fun `validateAll returns no entries when everything is valid`() {
        val result = ParticipantValidator.validateAll(
            listOf(visitNumberField, optionalNotesField),
            mapOf("visitNumber" to "V1", "notes" to ""),
        )
        assertTrue(result.isEmpty())
    }
}
