package org.example.app.domain.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Golden-shape check against real web output: `fixtures/config/web_*.json` are copies of
 * task_protocoller_web_app's `docs/ext_app_samples/` JSONs, with `defaultMicGain` converted to the
 * agreed 0..100 integer (the web still emitted a 0..1 fraction when these were copied).
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
        assertEquals(80, config.defaultMicGain)
        assertTrue(config.protocols.single().tasks.any { it is VocalTask })
    }

    @Test
    fun `multi-project sample carries a project on every protocol`() {
        val config = load("web_multi_project.json")

        assertTrue(config.protocols.size > 1)
        assertTrue(config.protocols.all { !it.project.isNullOrBlank() })
    }

    @Test
    fun `merged language variants sample has calibration off`() {
        val config = load("web_variants_en_cs.json")

        assertFalse(config.useCalibration)
        assertEquals(listOf("cs", "en"), config.languages)
    }
}
