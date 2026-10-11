package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.config.PatientField
import org.example.app.domain.participant.FieldValidationError
import org.example.app.domain.participant.ValidateParticipantInfoUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PatientInfoComponentTest {

    private val fields = listOf(
        PatientField(name = "code", labelKey = "field.code", regex = "[A-Z]{2}\\d{3}", required = true),
        PatientField(name = "notes", labelKey = "field.notes"),
    )

    private class Harness(fields: List<PatientField>, examinationDate: LocalDate = LocalDate.of(2026, 9, 28)) {
        var validatedValues: Map<String, String>? = null

        val component: PatientInfoComponent = DefaultPatientInfoComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            fields = fields,
            examinationDate = examinationDate,
            validateParticipantInfoUseCase = ValidateParticipantInfoUseCase(),
            onValidated = { values -> validatedValues = values },
        )
    }

    @Test
    fun `initial state carries the configured fields with no errors shown yet`() {
        val h = Harness(fields)
        assertEquals(fields, h.component.state.value.fields)
        // No validation pass has run yet (§8.6: gated on Continue) — no stale errors are shown.
        assertTrue(h.component.state.value.errors.isEmpty())
        assertEquals(null, h.validatedValues)
    }

    @Test
    fun `continue with a missing required field reports an error and does not proceed`() {
        val h = Harness(fields)
        h.component.onFieldChanged("notes", "free text")

        h.component.onContinue()

        assertFalse(h.component.state.value.isValid)
        val codeErrors = h.component.state.value.errors["code"]!!
        assertTrue(codeErrors.any { it is FieldValidationError.Required })
        assertEquals(null, h.validatedValues)
    }

    @Test
    fun `continue with a value that fails the regex reports a pattern mismatch`() {
        val h = Harness(fields)
        h.component.onFieldChanged("code", "not-valid")

        h.component.onContinue()

        val codeErrors = h.component.state.value.errors["code"]!!
        assertTrue(codeErrors.any { it is FieldValidationError.PatternMismatch })
        assertEquals(null, h.validatedValues)
    }

    @Test
    fun `continue with valid values clears errors and forwards to onValidated`() {
        val h = Harness(fields)
        h.component.onFieldChanged("code", "HC001")
        h.component.onFieldChanged("notes", "")

        h.component.onContinue()

        assertTrue(h.component.state.value.isValid)
        assertEquals(mapOf("code" to "HC001", "notes" to ""), h.validatedValues)
    }

    @Test
    fun `editing a field after a failed continue clears just that field's error`() {
        val h = Harness(fields)
        h.component.onContinue()
        assertTrue(h.component.state.value.errors.containsKey("code"))

        h.component.onFieldChanged("code", "still-typing")
        assertFalse(h.component.state.value.errors.containsKey("code"))
    }

    @Test
    fun `current_date is pre-filled with the examination date and cannot be edited`() {
        val h = Harness(listOf(PatientField(name = "current_date", labelKey = "field.date", required = true)))

        assertEquals("2026-09-28", h.component.state.value.values["current_date"])
        h.component.onFieldChanged("current_date", "1999-01-01")
        assertEquals("2026-09-28", h.component.state.value.values["current_date"])

        h.component.onContinue()
        assertEquals(mapOf("current_date" to "2026-09-28"), h.validatedValues)
    }

    @Test
    fun `a choice field only accepts one of its options`() {
        val h = Harness(listOf(PatientField(name = "sex", labelKey = "field.sex", required = true)))

        h.component.onFieldChanged("sex", "F")
        h.component.onContinue()
        assertEquals(
            listOf(FieldValidationError.NotAnOption("sex")),
            h.component.state.value.errors["sex"],
        )

        h.component.onFieldChanged("sex", "female")
        h.component.onContinue()
        assertEquals(mapOf("sex" to "female"), h.validatedValues)
    }
}
