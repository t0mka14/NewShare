package org.example.app.ui.scenario

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.example.app.domain.upload.UploadResult
import org.example.app.domain.upload.UploadStatus
import org.example.app.domain.upload.UploadStatusValue
import org.example.app.fakes.ConfigFixtures
import org.example.app.ui.TestTags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * §10.3 workflow scenario 8 (batch upload, §13 decisions 34/35): two processed sessions built
 * through the real UI (the cheapest path that also exercises `ProcessSessionUseCase` producing a
 * real archive each session's upload eligibility depends on), then a manual-only batch upload
 * where [org.example.app.fakes.FakeUploadApi] fails the first session and accepts the second.
 *
 * [ConfigFixtures.questionnaireOnly] is used to build the two sessions quickly — no calibration,
 * no editor, no master recording — batch upload itself is protocol-agnostic (§8.9: the payload is
 * always just the archive ZIP).
 */
class BatchUploadScenarioTest {

    /** Runs one questionnaire-only protocol from the main menu to its summary screen and back to
     * the menu, producing one processed, archived, eligible-for-upload session. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.runQuestionnaireOnlySession(harness: ScenarioHarness, patientCode: String) {
        onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).performClick()
        onNodeWithTag(TestTags.PatientInfo.field("code")).performTextInput(patientCode)
        onNodeWithTag(TestTags.PatientInfo.field("visitNumber")).performTextInput("V1")
        onNodeWithTag(TestTags.PatientInfo.CONTINUE_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        onNodeWithTag(TestTags.Questionnaire.answerField("intake_notes")).performTextInput("no complaints")
        onNodeWithTag(TestTags.Questionnaire.answerOption("symptoms", "symptom_hoarseness")).performClick()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        // INFO, then straight to processing (enableEditor: false for this fixture) and summary.
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).assertIsDisplayed().performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        onNodeWithTag(TestTags.SessionSummary.DONE_BUTTON).assertIsDisplayed().performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 8 - one failed session stays listed with a reason, the other uploads, retry targets only the failure`(
        @TempDir tempDir: Path,
    ) = runComposeUiTest {
        val harness = ScenarioHarness(tempDir)
        harness.loadConfig(ConfigFixtures.questionnaireOnly)

        setContent { ScenarioApp(harness) }

        runQuestionnaireOnlySession(harness, "HC010")
        runQuestionnaireOnlySession(harness, "HC011")

        val folderNames = harness.sessionFolderNames()
        assertEquals(2, folderNames.size)
        val examinations = folderNames.map { harness.container.sessionRepository.readExamination(it)!! }
        val sessionIds = examinations.map { it.sessionId }
        // FakeIdGenerator is sequential (§10.3 fixture inventory) — the folder created first
        // sorts first (date_patientCode_sessionId, ascending patient codes here) and is the one
        // scripted to fail below.
        val (failingSessionId, succeedingSessionId) = sessionIds
        harness.uploadApi.respondTo(failingSessionId, UploadResult.NetworkFailure)

        // ---- reach the upload screen from the main menu ----
        onNodeWithTag(TestTags.MainMenu.UPLOAD_BUTTON).performClick()
        onNodeWithTag(TestTags.Upload.READY_COUNT_TEXT).assertTextEquals("2 sessions ready to upload")
        onNodeWithTag(TestTags.Upload.sessionRow(failingSessionId)).assertIsDisplayed()
        onNodeWithTag(TestTags.Upload.sessionRow(succeedingSessionId)).assertIsDisplayed()

        onNodeWithTag(TestTags.Upload.UPLOAD_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        // Sequential batch: both sessions were attempted despite the first one failing.
        assertEquals(listOf(failingSessionId, succeedingSessionId), harness.uploadApi.calls.map { it.sessionId })

        onNodeWithTag(TestTags.Upload.PROGRESS_BAR).assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))

        // The failed session remains listed with a localized error reason; the uploaded one is gone.
        onNodeWithTag(TestTags.Upload.sessionRow(failingSessionId)).assertIsDisplayed()
        onNodeWithTag(TestTags.Upload.sessionErrorText(failingSessionId))
            .assertIsDisplayed()
            .assertTextEquals("Upload failed: network failure")
        onNodeWithTag(TestTags.Upload.sessionRow(succeedingSessionId)).assertDoesNotExist()
        onNodeWithTag(TestTags.Upload.READY_COUNT_TEXT).assertTextEquals("1 sessions ready to upload")

        // ---- both upload_status.json files are correct (§8.10) ----
        val failingFolder = folderNames[sessionIds.indexOf(failingSessionId)]
        val succeedingFolder = folderNames[sessionIds.indexOf(succeedingSessionId)]

        val failingStatus = harness.container.uploadStatusRepository.read(failingFolder)!!
        assertEquals(UploadStatusValue.Failed, failingStatus.status)
        assertEquals(1, failingStatus.attemptCount)
        assertEquals(1, failingStatus.attempts.size)

        val succeedingStatus = harness.container.uploadStatusRepository.read(succeedingFolder)!!
        assertEquals(UploadStatusValue.Uploaded, succeedingStatus.status)
        assertEquals(1, succeedingStatus.attemptCount)
        assertTrue(succeedingStatus.uploadedAt != null, "a terminal Uploaded status must record uploadedAt")

        // ---- pressing Upload again retries only the still-failed session ----
        harness.uploadApi.calls.clear()
        harness.uploadApi.respondTo(failingSessionId, UploadResult.Success(serverResponse = null))
        onNodeWithTag(TestTags.Upload.UPLOAD_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        assertEquals(listOf(failingSessionId), harness.uploadApi.calls.map { it.sessionId })
        onNodeWithTag(TestTags.Upload.sessionRow(failingSessionId)).assertDoesNotExist()
        onNodeWithTag(TestTags.Upload.SUCCESS_MESSAGE).assertIsDisplayed()
        assertEquals(UploadStatusValue.Uploaded, harness.container.uploadStatusRepository.read(failingFolder)!!.status)
    }

    /** Bonus assertion, §13 decision 35: an `Uploading` status left over from a crash/kill mid
     * upload (never actually rewritten to `Failed`/`Uploaded`) must show up as an interrupted,
     * retryable failure — otherwise the session would be stuck forever with no worker to
     * reconcile it. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 8b - a leftover Uploading status is listed as an interrupted failure and is retryable`(
        @TempDir tempDir: Path,
    ) = runComposeUiTest {
        val harness = ScenarioHarness(tempDir)
        harness.loadConfig(ConfigFixtures.questionnaireOnly)

        setContent { ScenarioApp(harness) }

        runQuestionnaireOnlySession(harness, "HC012")
        val folderName = harness.sessionFolderNames().single()
        val sessionId = harness.container.sessionRepository.readExamination(folderName)!!.sessionId

        // Simulate a crash mid-upload: `Uploading` was written but the attempt never resolved.
        harness.container.uploadStatusRepository.write(folderName, UploadStatus(status = UploadStatusValue.Uploading))

        onNodeWithTag(TestTags.MainMenu.UPLOAD_BUTTON).performClick()

        onNodeWithTag(TestTags.Upload.sessionRow(sessionId)).assertIsDisplayed()
        onNodeWithTag(TestTags.Upload.sessionErrorText(sessionId))
            .assertIsDisplayed()
            .assertTextEquals("Upload failed: the previous attempt was interrupted")

        onNodeWithTag(TestTags.Upload.UPLOAD_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        assertEquals(listOf(sessionId), harness.uploadApi.calls.map { it.sessionId })
        onNodeWithTag(TestTags.Upload.sessionRow(sessionId)).assertDoesNotExist()
        assertEquals(UploadStatusValue.Uploaded, harness.container.uploadStatusRepository.read(folderName)!!.status)
    }
}
