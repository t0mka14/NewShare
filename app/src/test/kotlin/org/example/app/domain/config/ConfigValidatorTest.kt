package org.example.app.domain.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigValidatorTest {

    private fun baseConfig(
        schemaVersion: Int = 1,
        protocols: List<Protocol> = listOf(validProtocol()),
        strings: Map<String, Map<String, String>> = emptyMap(),
    ) = RemoteConfig(
        schemaVersion = schemaVersion,
        configVersion = "2026-07-01.1",
        defaultLanguage = "cs",
        protocols = protocols,
        strings = strings,
    )

    private fun validProtocol(recordingsFileName: String = "\${installationId}_\${patientCode}_\${taskIndex}_\${task.subtype}") =
        Protocol(
            name = "Share",
            recordingsFileName = recordingsFileName,
            tasks = listOf(
                CalibrationTask(titleKey = "cal", optimalLoudness = listOf(0.2, 0.5)),
                VocalTask(titleKey = "vocal", subtype = VocalSubtype.PHONATION),
            ),
        )

    @Test
    fun `valid config passes with no errors`() {
        val result = ConfigValidator.validate(baseConfig())
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `rejects schemaVersion outside supported range`() {
        val result = ConfigValidator.validate(baseConfig(schemaVersion = 999))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it is ConfigValidationError.UnsupportedSchemaVersion })
    }

    @Test
    fun `supported schema range is exposed as a constant`() {
        assertEquals(1, ConfigValidator.SUPPORTED_SCHEMA_VERSIONS.first)
        assertTrue(ConfigValidator.SUPPORTED_SCHEMA_VERSIONS.contains(1))
    }

    @Test
    fun `rejects recordingsFileName template missing taskIndex placeholder`() {
        val protocol = validProtocol(recordingsFileName = "\${installationId}_\${patientCode}_\${task.subtype}")
        val result = ConfigValidator.validate(baseConfig(protocols = listOf(protocol)))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it is ConfigValidationError.MissingTaskIndexPlaceholder })
    }

    @Test
    fun `rejects VOCAL task with no CALIBRATION task at all`() {
        val protocol = Protocol(
            name = "NoCalibration",
            recordingsFileName = "\${taskIndex}",
            tasks = listOf(VocalTask(titleKey = "vocal", subtype = VocalSubtype.PHONATION)),
        )
        val result = ConfigValidator.validate(baseConfig(protocols = listOf(protocol)))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it is ConfigValidationError.MissingCalibrationBeforeVocal })
    }

    @Test
    fun `rejects VOCAL task before CALIBRATION`() {
        val protocol = Protocol(
            name = "WrongOrder",
            recordingsFileName = "\${taskIndex}",
            tasks = listOf(
                VocalTask(titleKey = "vocal", subtype = VocalSubtype.PHONATION),
                CalibrationTask(titleKey = "cal", optimalLoudness = listOf(0.2, 0.5)),
            ),
        )
        val result = ConfigValidator.validate(baseConfig(protocols = listOf(protocol)))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it is ConfigValidationError.MissingCalibrationBeforeVocal })
    }

    @Test
    fun `protocols with no VOCAL tasks do not require CALIBRATION`() {
        val protocol = Protocol(
            name = "QuestionnaireOnly",
            recordingsFileName = "\${taskIndex}",
            tasks = listOf(QuestionnaireTask(titleKey = "q")),
        )
        val result = ConfigValidator.validate(baseConfig(protocols = listOf(protocol)))
        assertTrue(result.isValid)
    }

    @Test
    fun `accumulates multiple errors across protocols`() {
        val badProtocol = Protocol(
            name = "Bad",
            recordingsFileName = "no-placeholder-here",
            tasks = listOf(VocalTask(titleKey = "vocal", subtype = VocalSubtype.PHONATION)),
        )
        val result = ConfigValidator.validate(baseConfig(protocols = listOf(badProtocol)))
        assertEquals(2, result.errors.size)
    }
}
