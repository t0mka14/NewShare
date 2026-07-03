package org.example.app.ui.scenario

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.fakes.ConfigFixtures
import org.example.app.ui.TestTags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * §10.3 workflow scenarios 1 (happy path, trimmed to recording — processing/upload land in
 * Phase 3, so this suite stops at the post-protocol summary) and 2 (repeat flow), driven through
 * real Compose screens via [ScenarioApp] (see [ScenarioHarness] for why this isn't
 * `RootContent`/`DefaultRootComponent` directly).
 *
 * Both scenarios use [ConfigFixtures.fullProtocol]'s "Share" protocol: CALIBRATION, VOCAL
 * (`nrepetition: 2`, `canRepeat: true`), QUESTIONNAIRE, INFO — matching the §8.3 worked example.
 */
class HappyPathScenarioTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 1 - happy path produces the expected on-disk session`(@TempDir tempDir: Path) = runComposeUiTest {
        val harness = ScenarioHarness(tempDir)
        harness.loadConfig(ConfigFixtures.fullProtocol)

        setContent { ScenarioApp(harness) }

        onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).performClick()

        onNodeWithTag(TestTags.PatientInfo.field("code")).performTextInput("HC001")
        onNodeWithTag(TestTags.PatientInfo.field("visitNumber")).performTextInput("V1")
        onNodeWithTag(TestTags.PatientInfo.field("sex")).performTextInput("F")
        onNodeWithTag(TestTags.PatientInfo.CONTINUE_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        onNodeWithTag(TestTags.Calibration.CONFIRM_BUTTON).assertIsDisplayed().performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        // VOCAL rep 1 and rep 2 (nrepetition: 2, one take each — scenario 2 exercises repeats).
        repeat(2) {
            onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed().performClick()
            advanceBoth(harness.clock, 500)
            onNodeWithTag(TestTags.Task.STOP_BUTTON).performClick()
            onNodeWithTag(TestTags.Task.NEXT_BUTTON).performClick()
            harness.dispatchers.scheduler.advanceUntilIdle()
            waitForIdle()
        }

        // QUESTIONNAIRE: one required OPEN field + one required SINGLE_CHOICE.
        onNodeWithTag(TestTags.Questionnaire.answerField("smoking_status")).performTextInput("no")
        onNodeWithTag(TestTags.Questionnaire.answerOption("voice_fatigue", "option_no")).performClick()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        // INFO: display-only.
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).assertIsDisplayed().performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        onNodeWithTag(TestTags.SessionSummary.DONE_BUTTON).assertIsDisplayed()

        // ---- on-disk assertions (§8.2 layout, §8.3 timeline) ----
        val folderName = harness.sessionFolderName()
        val sessionDir = harness.container.directories.sessionDir(folderName)
        assertTrue(Files.exists(sessionDir.resolve("participant.json")), "participant.json")
        assertTrue(Files.exists(sessionDir.resolve("examination.json")), "examination.json")
        assertTrue(Files.exists(sessionDir.resolve("task_configuration_snapshot.json")), "config snapshot")
        assertTrue(Files.exists(sessionDir.resolve("timeline.events.jsonl")), "event log")
        assertTrue(Files.exists(sessionDir.resolve("timeline_original.json")), "compacted timeline")
        assertTrue(Files.isDirectory(sessionDir.resolve("master")), "master dir")

        val participant = harness.container.sessionRepository.readParticipant(folderName)
        assertEquals("HC001", participant?.fields?.get("code"))
        assertEquals("V1", participant?.fields?.get("visitNumber"))

        val examination = harness.container.sessionRepository.readExamination(folderName)
        assertNotNull(examination)
        assertEquals(4, examination!!.tasks.size) // VOCAL rep1, VOCAL rep2, QUESTIONNAIRE, INFO
        assertNotNull(examination.endedAt)
        assertNotNull(examination.captureFormat)

        val original = harness.container.timelineRepository.readOriginal(folderName)
        assertNotNull(original)
        assertTrue(original!!.events.any { it.type == TimelineEventType.SESSION_RECORDING_STARTED })
        assertTrue(original.events.any { it.type == TimelineEventType.SESSION_RECORDING_STOPPED })

        val offsets = original.events.mapNotNull { it.sampleOffset }
        assertTrue(offsets.isNotEmpty(), "master-bearing session should carry sample offsets")
        assertEquals(offsets, offsets.sorted(), "sampleOffsets must be monotonically increasing")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 2 - repeat flow produces 6 takes across 2 task instances, last take 3 each`(
        @TempDir tempDir: Path,
    ) = runComposeUiTest {
        val harness = ScenarioHarness(tempDir)
        harness.loadConfig(ConfigFixtures.fullProtocol)

        setContent { ScenarioApp(harness) }

        onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).performClick()
        onNodeWithTag(TestTags.PatientInfo.field("code")).performTextInput("HC002")
        onNodeWithTag(TestTags.PatientInfo.field("visitNumber")).performTextInput("V1")
        onNodeWithTag(TestTags.PatientInfo.CONTINUE_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        onNodeWithTag(TestTags.Calibration.CONFIRM_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        // Two VOCAL task instances (nrepetition: 2), 3 takes each via Repeat (§8.3 worked example).
        repeat(2) {
            onNodeWithTag(TestTags.Task.START_BUTTON).performClick() // take 1
            advanceBoth(harness.clock, 200)
            onNodeWithTag(TestTags.Task.STOP_BUTTON).performClick()
            onNodeWithTag(TestTags.Task.REPEAT_BUTTON).performClick() // rejects take 1, starts take 2
            advanceBoth(harness.clock, 200)
            onNodeWithTag(TestTags.Task.STOP_BUTTON).performClick()
            onNodeWithTag(TestTags.Task.REPEAT_BUTTON).performClick() // rejects take 2, starts take 3
            advanceBoth(harness.clock, 200)
            onNodeWithTag(TestTags.Task.STOP_BUTTON).performClick()
            onNodeWithTag(TestTags.Task.NEXT_BUTTON).performClick() // accepts take 3
            harness.dispatchers.scheduler.advanceUntilIdle()
            waitForIdle()
        }

        // Finish the remaining tasks to reach a clean session end (compaction needs it).
        onNodeWithTag(TestTags.Questionnaire.answerField("smoking_status")).performTextInput("no")
        onNodeWithTag(TestTags.Questionnaire.answerOption("voice_fatigue", "option_no")).performClick()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        val folderName = harness.sessionFolderName()
        val examination = harness.container.sessionRepository.readExamination(folderName)!!
        val vocalRecords = examination.tasks.filter { it.type == "VOCAL" }
        assertEquals(2, vocalRecords.size)
        assertTrue(vocalRecords.all { it.takes == 3 }, "both VOCAL instances should record 3 takes each")

        val original = harness.container.timelineRepository.readOriginal(folderName)!!
        val vocalTaskIndexes = vocalRecords.map { it.taskIndex }.toSet()
        val starts = original.events.filter {
            it.type == TimelineEventType.START_BUTTON_PRESSED && it.taskIndex in vocalTaskIndexes
        }
        assertEquals(6, starts.size, "2 instances x 3 takes = 6 START_BUTTON_PRESSED events")

        vocalTaskIndexes.forEach { taskIndex ->
            val takesForInstance = starts.filter { it.taskIndex == taskIndex }.map { it.take }.sortedBy { it }
            assertEquals(listOf(1, 2, 3), takesForInstance, "take numbering restarts at 1 per instance")
        }

        val rejections = original.events.filter { it.type == TimelineEventType.TAKE_REJECTED && it.taskIndex in vocalTaskIndexes }
        assertEquals(4, rejections.size, "2 rejections per instance (take 1 and take 2 each rejected by Repeat)")
        assertTrue(rejections.all { it.reason == "EXAMINER_REPEAT" })
    }
}
