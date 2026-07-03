package org.example.app.domain.participant

import org.example.app.domain.config.PatientField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PatientCodeComposerTest {

    private fun field(name: String, useInFilename: Boolean) = PatientField(
        name = name,
        labelKey = "${name}_label",
        useInFilename = useInFilename,
    )

    @Test
    fun `composes from useInFilename fields only, in config order`() {
        val fields = listOf(field("code", true), field("sex", false), field("visitNumber", true))
        val values = mapOf("code" to "HC001", "sex" to "F", "visitNumber" to "V1")

        assertEquals("HC001_V1", PatientCodeComposer.compose(fields, values))
    }

    @Test
    fun `sanitizes unsafe characters out of each component`() {
        val fields = listOf(field("code", true))
        val values = mapOf("code" to "HC 001/ä!ő")

        assertEquals("HC001", PatientCodeComposer.compose(fields, values))
    }

    @Test
    fun `keeps letters digits underscore and hyphen`() {
        assertEquals("Abc-123_xyz", PatientCodeComposer.sanitize("Abc-123_xyz"))
    }

    @Test
    fun `missing value for a useInFilename field is treated as empty, not a crash`() {
        val fields = listOf(field("code", true), field("visitNumber", true))
        val values = mapOf("code" to "HC001")

        assertEquals("HC001_", PatientCodeComposer.compose(fields, values))
    }

    @Test
    fun `no useInFilename fields composes to empty string`() {
        val fields = listOf(field("sex", false))
        assertEquals("", PatientCodeComposer.compose(fields, mapOf("sex" to "F")))
    }
}
