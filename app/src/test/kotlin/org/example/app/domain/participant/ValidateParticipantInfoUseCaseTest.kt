package org.example.app.domain.participant

import org.example.app.domain.config.PatientField
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidateParticipantInfoUseCaseTest {

    private val fields = listOf(
        PatientField(name = "code", labelKey = "field.code", regex = "^HC\\d{3}$", required = true, useInFilename = true),
        PatientField(name = "notes", labelKey = "field.notes"),
    )

    @Test
    fun `delegates to ParticipantValidator and reports the same errors`() {
        val values = mapOf("code" to "bad-code", "notes" to "")
        val useCaseResult = ValidateParticipantInfoUseCase().execute(fields, values)
        val directResult = ParticipantValidator.validateAll(fields, values)
        assertTrue(useCaseResult == directResult)
    }

    @Test
    fun `valid input produces no errors`() {
        val values = mapOf("code" to "HC001", "notes" to "")
        val result = ValidateParticipantInfoUseCase().execute(fields, values)
        assertTrue(result.isEmpty())
    }
}
