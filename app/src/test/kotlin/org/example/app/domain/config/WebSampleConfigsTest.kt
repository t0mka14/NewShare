package org.example.app.domain.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Golden-shape check against real web output: `fixtures/config/web_*.json` are verbatim copies of
 * task_protocoller_web_app's `docs/ext_app_samples/` JSONs (web commit 7170959 or later, where
 * `defaultMicGain` is the agreed 0..100 integer).
 */
class WebSampleConfigsTest {

    private fun load(name: String): RemoteConfig {
        val json = requireNotNull(javaClass.getResource("/fixtures/config/$name")) { "missing fixture $name" }.readText()
        val config = ConfigDecoder.decode(json).config
        val validation = ConfigValidator.validate(config)
        assertTrue(validation.isValid, "$name: ${validation.errors}")
        return config
    }

    @Test
    fun `single protocol sample decodes and validates, with calibration on`() {
        val config = load("web_single_en.json")

        assertTrue(config.useCalibration)
        assertTrue(config.enableEditor)
        assertEquals(63, config.defaultMicGain)
        val protocol = config.protocols.single()
        assertTrue(protocol.tasks.any { it is VocalTask })
        assertEquals(listOf("patient_code"), protocol.patientFields.map { it.name })
        assertTrue(protocol.recordingsFileName.contains("\${field.patient_code}"))
    }

    @Test
    fun `multi-project sample carries a project on every protocol`() {
        val config = load("web_multi_project.json")

        assertTrue(config.protocols.size > 1)
        assertTrue(config.protocols.all { !it.project.isNullOrBlank() })
        assertTrue(config.protocols.all { it.patientFields.isNotEmpty() })
    }

    @Test
    fun `merged language variants sample has calibration off`() {
        val config = load("web_variants_en_cs.json")

        assertFalse(config.useCalibration)
        assertEquals(listOf("cs", "en"), config.languages)
    }

    @Test
    fun `full identifier sample decodes the catalogue and custom fields, with the duplicate dropped`() {
        val json = requireNotNull(javaClass.getResource("/fixtures/config/web_identifiers_full.json")).readText()
        val decoded = ConfigDecoder.decode(json)
        assertTrue(decoded.warnings.any { it.contains("patient_code") }, "${decoded.warnings}")
        val config = load("web_identifiers_full.json")

        val fields = config.protocols.single().patientFields
        assertEquals(
            listOf("current_date", "sex", "education", "patient_code", "surname", "year_of_birth", "visit_number", "medication_state"),
            fields.map { it.name },
        )
        // The first patient_code wins (the web's own, not the one mapped from a legacy id).
        assertEquals("PD001", fields.single { it.name == "patient_code" }.placeholder)
        assertTrue(fields.all { it.helpKey.isNotEmpty() })
    }
}
