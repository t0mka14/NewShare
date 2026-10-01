package org.example.app.domain.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
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
          "defaultMicGain": 63,
          "enableEditor": false,
          "indicatorType": "CIRCLE",
          "useCalibration": true,
          "protocols": [
            {
              "name": "Share",
              "recordingsFileName": "${'$'}{installationId}_${'$'}{field.patient_code}_${'$'}{taskIndex}_${'$'}{task.subtype}_Rep${'$'}{repetition}",
              "patientFields": [
                { "name": "patient_code", "labelKey": "patient_code_label", "helpKey": "patient_code_help", "placeholder": "HC001", "regex": "[a-zA-Z0-9_-]+", "required": true }
              ],
              "tasks": [
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
        assertEquals(2, result.config.protocols[0].tasks.size)
        assertTrue(result.config.useCalibration)
        assertEquals("USBAudioDevice", result.config.defaultMicName)
        assertEquals(63, result.config.defaultMicGain)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `protocol project decodes, and is null when absent`() {
        assertEquals(null, ConfigDecoder.decode(minimalConfigJson).config.protocols[0].project)

        val json = minimalConfigJson.replace("\"name\": \"Share\",", "\"name\": \"Share\", \"project\": \"PD study\",")
        assertEquals("PD study", ConfigDecoder.decode(json).config.protocols[0].project)
    }

    @Test
    fun `findProtocol matches name and project, or name alone without a project`() {
        val config = RemoteConfig(
            schemaVersion = 1, configVersion = "v", defaultLanguage = "en",
            protocols = listOf(
                Protocol(name = "Standard", project = "A", recordingsFileName = "a"),
                Protocol(name = "Standard", project = "B", recordingsFileName = "b"),
            ),
        )

        assertEquals("b", config.findProtocol("Standard", "B")?.recordingsFileName)
        assertEquals("a", config.findProtocol("Standard", null)?.recordingsFileName)
        assertEquals(null, config.findProtocol("Standard", "C"))
    }

    @Test
    fun `patientFields decode inside the protocol`() {
        val field = ConfigDecoder.decode(minimalConfigJson).config.protocols.single().patientFields.single()
        assertEquals(
            PatientField("patient_code", "patient_code_label", "patient_code_help", "HC001", "[a-zA-Z0-9_-]+", required = true),
            field,
        )
    }

    @Test
    fun `a field name listed twice in one protocol keeps the first and warns`() {
        val json = minimalConfigJson.replace(
            "\"required\": true }",
            "\"required\": true },\n{ \"name\": \"patient_code\", \"labelKey\": \"dup\", \"required\": false }",
        )
        val result = ConfigDecoder.decode(json)

        val fields = result.config.protocols.single().patientFields
        assertEquals(listOf("patient_code_label"), fields.map { it.labelKey })
        assertTrue(result.warnings.any { it.contains("patient_code") })
    }

    @Test
    fun `useCalibration defaults to false when absent`() {
        val json = minimalConfigJson.replace("\"useCalibration\": true,", "")
        assertFalse(ConfigDecoder.decode(json).config.useCalibration)
    }

    @Test
    fun `CALIBRATION is no longer a task type`() {
        val json = minimalConfigJson.replace(
            "\"tasks\": [",
            "\"tasks\": [ { \"type\": \"CALIBRATION\", \"titleKey\": \"c\", \"optimalLoudness\": [0.2, 0.5] },",
        )
        assertThrows(Exception::class.java) { ConfigDecoder.decode(json) }
    }

    @Test
    fun `defaultMicGain is optional and decodes to null when absent`() {
        val json = minimalConfigJson.replace("\"defaultMicGain\": 63,", "")
        assertEquals(null, ConfigDecoder.decode(json).config.defaultMicGain)
    }

    @Test
    fun `decodes real JSON booleans strictly, not 0-1 ints`() {
        val result = ConfigDecoder.decode(minimalConfigJson)
        val vocal = result.config.protocols[0].tasks[0] as VocalTask
        assertEquals(true, vocal.canRepeat)
        assertEquals(false, vocal.canSkip)
        assertEquals(true, vocal.showIndicator)
    }

    @Test
    fun `VOCAL recordVideo decodes, and defaults to false when absent`() {
        val plain = ConfigDecoder.decode(minimalConfigJson).config.protocols[0].tasks[0] as VocalTask
        assertFalse(plain.recordVideo)

        val json = minimalConfigJson.replace("\"showIndicator\": true,", "\"showIndicator\": true, \"recordVideo\": true,")
        val filming = ConfigDecoder.decode(json).config.protocols[0].tasks[0] as VocalTask
        assertTrue(filming.recordVideo)
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
        assertInstanceOf(VocalTask::class.java, tasks[0])
        assertInstanceOf(InfoTask::class.java, tasks[1])
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
