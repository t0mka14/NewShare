package org.example.app.domain.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecordingsFileNameRendererTest {

    @Test
    fun `renders every supported template variable`() {
        val result = RecordingsFileNameRenderer.render(
            template = "\${installationId}_\${patientCode}_\${taskIndex}_\${task.subtype}_Rep\${repetition}",
            installationId = "clinic-01",
            patientCode = "HC001",
            taskIndex = 3,
            subtype = "PHONATION",
            repetition = 2,
        )
        assertEquals("clinic-01_HC001_3_PHONATION_Rep2", result)
    }

    @Test
    fun `renders template with a subset of variables and literal text`() {
        val result = RecordingsFileNameRenderer.render(
            template = "clip-\${taskIndex}",
            installationId = "clinic-01",
            patientCode = "HC001",
            taskIndex = 7,
            subtype = "PATAKA",
            repetition = 1,
        )
        assertEquals("clip-7", result)
    }

    @Test
    fun `repeated placeholders are all substituted`() {
        val result = RecordingsFileNameRenderer.render(
            template = "\${taskIndex}-\${taskIndex}",
            installationId = "i",
            patientCode = "p",
            taskIndex = 5,
            subtype = "READING",
            repetition = 1,
        )
        assertEquals("5-5", result)
    }
}
