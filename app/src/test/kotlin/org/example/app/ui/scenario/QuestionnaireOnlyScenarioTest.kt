package org.example.app.ui.scenario

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.example.app.fakes.ConfigFixtures
import org.example.app.ui.TestTags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * §10.3 workflow scenario 7: a questionnaire-only protocol (no CALIBRATION/VOCAL task anywhere,
 * [ConfigFixtures.questionnaireOnly]) skips calibration entirely, never creates a recorder, and
 * produces a JSON-only session (§8.8 "no-master sessions").
 */
class QuestionnaireOnlyScenarioTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 7 - questionnaire-only protocol never starts the recorder and archives JSON only`(
        @TempDir tempDir: Path,
    ) = runComposeUiTest {
        val harness = ScenarioHarness(tempDir)
        harness.loadConfig(ConfigFixtures.questionnaireOnly)

        setContent { ScenarioApp(harness) }

        onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).performClick()
        onNodeWithTag(TestTags.PatientInfo.field("code")).performTextInput("HC004")
        onNodeWithTag(TestTags.PatientInfo.field("visitNumber")).performTextInput("V1")
        onNodeWithTag(TestTags.PatientInfo.CONTINUE_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        // No calibration screen: straight to the first (QUESTIONNAIRE) task instance.
        onNodeWithTag(TestTags.Calibration.CONFIRM_BUTTON).assertDoesNotExist()
        onNodeWithTag(TestTags.Questionnaire.answerField("intake_notes")).assertIsDisplayed()

        onNodeWithTag(TestTags.Questionnaire.answerField("intake_notes")).performTextInput("no complaints")
        onNodeWithTag(TestTags.Questionnaire.answerOption("symptoms", "symptom_hoarseness")).performClick()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        // INFO task.
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).assertIsDisplayed().performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        onNodeWithTag(TestTags.SessionSummary.DONE_BUTTON).assertIsDisplayed()

        // The recorder is never constructed at all for a no-master protocol (§6.2) — stronger
        // than "saw no startMonitoring/startWriting calls", and matches the assertion pattern
        // `SessionComponentTest` already uses for this case.
        assertNull(harness.recorder, "no-master protocol must never create a recorder")

        val folderName = harness.sessionFolderName()
        val sessionDir = harness.container.directories.sessionDir(folderName)
        assertTrue(
            Files.list(sessionDir.resolve("master")).use { it.toList() }.isEmpty(),
            "no master WAV should be written for a no-master session",
        )

        val examination = harness.container.sessionRepository.readExamination(folderName)!!
        assertNull(examination.captureFormat)
        assertEquals(2, examination.tasks.size) // QUESTIONNAIRE + INFO
        val questionnaireRecord = examination.tasks.first { it.type == "QUESTIONNAIRE" }
        assertEquals(listOf("no complaints"), questionnaireRecord.questionnaireAnswers?.get("intake_notes"))
        assertEquals(listOf("symptom_hoarseness"), questionnaireRecord.questionnaireAnswers?.get("symptoms"))

        val original = harness.container.timelineRepository.readOriginal(folderName)!!
        assertTrue(original.events.isNotEmpty())
        assertTrue(original.events.all { it.sampleOffset == null }, "no-master sessions carry null sampleOffsets (§8.3/§8.8)")
    }
}
