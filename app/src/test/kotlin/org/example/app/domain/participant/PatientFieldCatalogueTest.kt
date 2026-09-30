package org.example.app.domain.participant

import org.example.app.domain.participant.PatientFieldCatalogue.FieldKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PatientFieldCatalogueTest {

    @Test
    fun `catalogue names map to their agreed kinds, everything else is free text`() {
        assertEquals(FieldKind.AutoDate, PatientFieldCatalogue.kindOf("current_date"))
        assertEquals(FieldKind.Choice(listOf("male", "female")), PatientFieldCatalogue.kindOf("sex"))
        assertEquals(
            FieldKind.Choice(listOf("less than upper secondary", "upper secondary and vocational", "tertiary education")),
            PatientFieldCatalogue.kindOf("education"),
        )
        listOf("patient_code", "surname", "year_of_birth", "visit_number").forEach {
            assertEquals(FieldKind.Text, PatientFieldCatalogue.kindOf(it), it)
        }
    }

    @Test
    fun `option label keys replace spaces`() {
        assertEquals(
            "patientField.education.upper_secondary_and_vocational",
            PatientFieldCatalogue.optionLabelKey("education", "upper secondary and vocational"),
        )
    }

    @Test
    fun `sanitize keeps only filename-safe characters`() {
        assertEquals("HC-001_a", PatientFieldCatalogue.sanitize("HC-001_a"))
        assertEquals("HC001", PatientFieldCatalogue.sanitize("HC 0/0.1"))
        assertEquals("Novak", PatientFieldCatalogue.sanitize("Novák"))
        assertEquals("CizekRehor", PatientFieldCatalogue.sanitize("Čížek Řehoř"))
    }

    @Test
    fun `patientCode is the sanitized patient_code value, empty without one`() {
        assertEquals("HC001", PatientFieldCatalogue.patientCode(mapOf("patient_code" to "HC/001", "sex" to "female")))
        assertEquals("", PatientFieldCatalogue.patientCode(mapOf("sex" to "female")))
    }
}
