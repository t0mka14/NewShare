package org.example.app.domain.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigDecoderTest {

    private val minimalConfigJson = """
        {
          "schemaVersion": 1,
          "configVersion": "2026-07-01.1",
          "defaultLanguage": "cs",
          "languages": ["cs"],
          "defaultMicName": "USBAudioDevice",
          "enableEditor": false,
          "indicatorType": "CIRCLE",
          "patientFields": [
            { "name": "code", "labelKey": "patient_code_label", "regex": "[a-zA-Z0-9_-]+", "required": true, "useInFilename": true }
          ],
          "protocols": [
            {
              "name": "Share",
              "recordingsFileName": "${'$'}{installationId}_${'$'}{patientCode}_${'$'}{taskIndex}_${'$'}{task.subtype}_Rep${'$'}{repetition}",
              "tasks": [
                { "type": "CALIBRATION", "titleKey": "calibration_title", "instructionKeys": ["c1"], "optimalLoudness": [0.2, 0.5] },
                { "type": "VOCAL", "subtype": "PHONATION", "titleKey": "phonation_title", "instructionKeys": ["p1"], "length": 10,
                  "showIndicator": true, "canRepeat": true, "canSkip": false, "nrepetition": 1 },
                { "type": "INFO", "titleKey": "info_title", "instructionKeys": ["info_done"] }
              ]
            }
          ],
          "strings": { "cs": { "calibration_title": "Kalibrace" } }
        }
    """.trimIndent()

    @Test
    fun `decodes minimal end-to-end example`() {
        val result = ConfigDecoder.decode(minimalConfigJson)

        assertEquals(1, result.config.schemaVersion)
        assertEquals("2026-07-01.1", result.config.configVersion)
        assertEquals(1, result.config.protocols.size)
        assertEquals(3, result.config.protocols[0].tasks.size)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `decodes real JSON booleans strictly, not 0-1 ints`() {
        val result = ConfigDecoder.decode(minimalConfigJson)
        val vocal = result.config.protocols[0].tasks[1] as VocalTask
        assertEquals(true, vocal.canRepeat)
        assertEquals(false, vocal.canSkip)
        assertEquals(true, vocal.showIndicator)
    }

    @Test
    fun `rejects 0-1 in place of real booleans`() {
        val badJson = minimalConfigJson.replace("\"canRepeat\": true", "\"canRepeat\": 1")
        org.junit.jupiter.api.assertThrows<Exception> {
            ConfigDecoder.decode(badJson)
        }
    }

    @Test
    fun `decodes each task type discriminator to the right subtype`() {
        val result = ConfigDecoder.decode(minimalConfigJson)
        val tasks = result.config.protocols[0].tasks
        assertInstanceOf(CalibrationTask::class.java, tasks[0])
        assertInstanceOf(VocalTask::class.java, tasks[1])
        assertInstanceOf(InfoTask::class.java, tasks[2])
    }

    @Test
    fun `VIDEO task type decodes without crashing`() {
        val json = minimalConfigJson.replace(
            "\"tasks\": [",
            """"tasks": [
                { "type": "VIDEO", "subtype": "EMOTIONS", "titleKey": "emotions_title",
                  "instructionKeys": ["e1"], "length": 30, "canRepeat": true, "canSkip": false,
                  "nrepetition": 1, "havePTZ": false },""",
        )
        val result = ConfigDecoder.decode(json)
        val videoTask = result.config.protocols[0].tasks.first()
        assertInstanceOf(VideoTask::class.java, videoTask)
        assertEquals("EMOTIONS", (videoTask as VideoTask).subtype)
    }

    @Test
    fun `lenient QUESTIONAIRE alias decodes as QUESTIONNAIRE with a warning`() {
        val json = minimalConfigJson.replace(
            "\"tasks\": [",
            """"tasks": [
                { "type": "QUESTIONAIRE", "titleKey": "q_title", "questions": [], "nrepetition": 1 },""",
        )
        val result = ConfigDecoder.decode(json)
        val questionnaireTask = result.config.protocols[0].tasks.first()
        assertInstanceOf(QuestionnaireTask::class.java, questionnaireTask)
        assertTrue(result.warnings.any { it.contains("QUESTIONAIRE") })
    }

    @Test
    fun `canonical QUESTIONNAIRE spelling produces no warning`() {
        val json = minimalConfigJson.replace(
            "\"tasks\": [",
            """"tasks": [
                { "type": "QUESTIONNAIRE", "titleKey": "q_title", "questions": [], "nrepetition": 1 },""",
        )
        val result = ConfigDecoder.decode(json)
        assertFalse(result.warnings.any { it.contains("QUESTIONAIRE") })
    }

    @Test
    fun `ignores unknown keys for forward compatibility`() {
        val json = minimalConfigJson.replace(
            "\"schemaVersion\": 1,",
            "\"schemaVersion\": 1, \"someFutureField\": \"whatever\",",
        )
        val result = ConfigDecoder.decode(json)
        assertEquals(1, result.config.schemaVersion)
    }
}
