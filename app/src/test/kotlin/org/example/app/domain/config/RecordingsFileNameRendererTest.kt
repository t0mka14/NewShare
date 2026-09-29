package org.example.app.domain.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecordingsFileNameRendererTest {

    private fun render(template: String, fields: Map<String, String> = mapOf("patient_code" to "HC001")) =
        RecordingsFileNameRenderer.render(
            template = template,
            installationId = "clinic-01",
            fieldValues = fields,
            taskIndex = 3,
            subtype = "PHONATION",
            repetition = 2,
        )

    @Test
    fun `renders every supported template variable`() {
        assertEquals(
            "HC001_clinic-01_3_PHONATION_Rep2",
            render("\${field.patient_code}_\${installationId}_\${taskIndex}_\${task.subtype}_Rep\${repetition}"),
        )
    }

    @Test
    fun `renders template with a subset of variables and literal text`() {
        assertEquals("clip-3", render("clip-\${taskIndex}"))
    }

    @Test
    fun `repeated placeholders are all substituted`() {
        assertEquals("3-3-HC001-HC001", render("\${taskIndex}-\${taskIndex}-\${field.patient_code}-\${field.patient_code}"))
    }

    @Test
    fun `field values are sanitized to filename-safe characters`() {
        val fields = mapOf("education" to "upper secondary and vocational", "surname" to "Novák/Č.")
        assertEquals("uppersecondaryandvocational_Novk", render("\${field.education}_\${field.surname}", fields))
    }

    @Test
    fun `a field without a value renders empty`() {
        assertEquals("_3", render("\${field.surname}_\${taskIndex}"))
    }

    @Test
    fun `patientCode is no longer a variable`() {
        assertEquals("\${patientCode}_3", render("\${patientCode}_\${taskIndex}"))
    }
}
