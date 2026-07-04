package org.example.app.ui.scenario

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.example.app.domain.session.TimelineSource
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.fakes.ConfigFixtures
import org.example.app.ui.TestTags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * §10.3 workflow scenario 4: the waveform editor, using [ConfigFixtures.fullProtocol]'s "Share"
 * protocol (`enableEditor: true`, two VOCAL instances via `nrepetition: 2`). Both halves of the
 * scenario run the identical protocol; only what happens on the editor screen differs.
 *
 * `performMouseInput` press-drag-release on a boundary handle is exactly the pattern already
 * proven to move a boundary in `NewScreensSmokeTest` ("editor screen renders a segment and a
 * real mouse drag moves the start boundary"), here driven through the real `DefaultRootComponent`
 * end to end instead of a bare `DefaultEditorComponent`, with assertions on the actual written
 * `timeline_edited.json` rather than just in-memory component state.
 */
class EditorScenarioTest {

    /** Drives Menu -> ProtocolPicker -> PatientInfo -> Calibration -> two one-take VOCAL
     * instances -> Questionnaire -> Info, landing on the editor screen. Returns the completed
     * session's folder name. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.runProtocolIntoEditor(harness: ScenarioHarness, patientCode: String) {
        onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).performClick()
        // fullProtocol defines two protocols ("Share"/"QuestionnaireOnly", §3 follow-up).
        onNodeWithTag(TestTags.ProtocolPicker.protocolButton("Share")).performClick()
        onNodeWithTag(TestTags.PatientInfo.field("code")).performTextInput(patientCode)
        onNodeWithTag(TestTags.PatientInfo.field("visitNumber")).performTextInput("V1")
        onNodeWithTag(TestTags.PatientInfo.CONTINUE_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        onNodeWithTag(TestTags.Calibration.CONFIRM_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        // Two VOCAL task instances (nrepetition: 2), one take each — plain accept, no repeats.
        repeat(2) {
            onNodeWithTag(TestTags.Task.START_BUTTON).assertIsDisplayed().performClick()
            advanceBoth(harness.clock, 500)
            onNodeWithTag(TestTags.Task.STOP_BUTTON).performClick()
            onNodeWithTag(TestTags.Task.NEXT_BUTTON).performClick()
            harness.dispatchers.scheduler.advanceUntilIdle()
            waitForIdle()
        }

        onNodeWithTag(TestTags.Questionnaire.answerField("smoking_status")).performTextInput("no")
        onNodeWithTag(TestTags.Questionnaire.answerOption("voice_fatigue", "option_no")).performClick()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        // INFO: display-only; its Next lands on the editor (enableEditor: true).
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).assertIsDisplayed().performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 4a - dragging a boundary writes the complete edited timeline and processing consumes it`(
        @TempDir tempDir: Path,
    ) = runComposeUiTest {
        val harness = ScenarioHarness(tempDir)
        harness.loadConfig(ConfigFixtures.fullProtocol)

        setContent { ScenarioApp(harness) }

        runProtocolIntoEditor(harness, "HC006")

        // ---- ground truth: the original timeline's actual START/STOP offsets, before any edit ----
        val folderName = harness.sessionFolderName()
        val examinationBeforeEdit = harness.container.sessionRepository.readExamination(folderName)!!
        val vocalTaskIndexes = examinationBeforeEdit.tasks.filter { it.type == "VOCAL" }.map { it.taskIndex }.sorted()
        assertEquals(2, vocalTaskIndexes.size, "nrepetition: 2 must produce two VOCAL task records")

        val originalTimeline = harness.container.timelineRepository.readOriginal(folderName)!!
        fun takeBounds(taskIndex: Int): Pair<Long, Long> {
            val start = originalTimeline.events.single {
                it.type == TimelineEventType.START_BUTTON_PRESSED && it.taskIndex == taskIndex
            }.sampleOffset!!
            val stop = originalTimeline.events.single {
                it.type == TimelineEventType.STOP_BUTTON_PRESSED && it.taskIndex == taskIndex
            }.sampleOffset!!
            return start to stop
        }
        val (seg0OriginalStart, seg0OriginalStop) = takeBounds(vocalTaskIndexes[0])
        val (seg1OriginalStart, seg1OriginalStop) = takeBounds(vocalTaskIndexes[1])

        // ---- editor: drag the *current* (first) segment's start boundary; never touch segment 2 ----
        onNodeWithTag(TestTags.Editor.WAVEFORM_CANVAS).assertIsDisplayed()
        onNodeWithTag(TestTags.Editor.SEGMENT_LABEL).assertIsDisplayed()

        onNodeWithTag(TestTags.Editor.START_BOUNDARY_HANDLE).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(20f, 0f))
            release()
        }
        waitForIdle()

        onNodeWithTag(TestTags.Editor.ACCEPT_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        onNodeWithTag(TestTags.SessionSummary.DONE_BUTTON).assertIsDisplayed()

        // ---- timeline_edited.json: complete segment list (§13 decision 33) ----
        val edited = harness.container.timelineRepository.readEdited(folderName)
        assertNotNull(edited, "a boundary was moved — timeline_edited.json must exist")
        assertEquals(1, edited!!.version)
        assertEquals(2, edited.segments.size, "decision 33: the complete segment list, not just the touched one")

        val seg0 = edited.segments.single { it.taskIndex == vocalTaskIndexes[0] }
        val seg1 = edited.segments.single { it.taskIndex == vocalTaskIndexes[1] }

        assertNotEquals(seg0OriginalStart, seg0.startSample, "the dragged boundary must differ from the original START offset")
        assertEquals(seg0OriginalStop, seg0.stopSample, "segment 1's untouched stop boundary must equal the original STOP offset")
        assertEquals(seg1OriginalStart, seg1.startSample, "the untouched segment must equal the original START offset")
        assertEquals(seg1OriginalStop, seg1.stopSample, "the untouched segment must equal the original STOP offset")

        // ---- processing consumed the edited ranges (FakeAudioClipService.cutClipCalls) ----
        val examinationAfter = harness.container.sessionRepository.readExamination(folderName)!!
        assertEquals(TimelineSource.EDITED, examinationAfter.processing?.timelineUsed)

        val cutForSeg0 = harness.audioClipService.cutClipCalls.single {
            it.startSample == seg0.startSample && it.stopSample == seg0.stopSample
        }
        assertNotEquals(seg0OriginalStart, cutForSeg0.startSample, "processing must cut the moved range, not the original one")
        val cutForSeg1 = harness.audioClipService.cutClipCalls.single {
            it.startSample == seg1.startSample && it.stopSample == seg1.stopSample
        }
        assertEquals(seg1OriginalStart, cutForSeg1.startSample)
        assertEquals(seg1OriginalStop, cutForSeg1.stopSample)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 4b - an untouched editor pass writes no timeline_edited json and exports the original ranges`(
        @TempDir tempDir: Path,
    ) = runComposeUiTest {
        val harness = ScenarioHarness(tempDir)
        harness.loadConfig(ConfigFixtures.fullProtocol)

        setContent { ScenarioApp(harness) }

        runProtocolIntoEditor(harness, "HC007")

        val folderName = harness.sessionFolderName()
        val originalTimeline = harness.container.timelineRepository.readOriginal(folderName)!!
        val originalStartStops = originalTimeline.events
            .filter { it.type == TimelineEventType.START_BUTTON_PRESSED || it.type == TimelineEventType.STOP_BUTTON_PRESSED }
            .mapNotNull { it.sampleOffset }
            .toSet()

        onNodeWithTag(TestTags.Editor.WAVEFORM_CANVAS).assertIsDisplayed()

        // No drag at all — accept immediately (§8.7 decision 13).
        onNodeWithTag(TestTags.Editor.ACCEPT_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        onNodeWithTag(TestTags.SessionSummary.DONE_BUTTON).assertIsDisplayed()

        assertNull(
            harness.container.timelineRepository.readEdited(folderName),
            "an untouched editor pass must not write timeline_edited.json",
        )
        assertEquals(false, harness.container.timelineRepository.editedExists(folderName))

        val examinationAfter = harness.container.sessionRepository.readExamination(folderName)!!
        assertEquals(TimelineSource.ORIGINAL, examinationAfter.processing?.timelineUsed)

        // Every cut range processing actually used is one of the original take offset pairs.
        val vocalTaskIndexes = examinationAfter.tasks.filter { it.type == "VOCAL" }.map { it.taskIndex }
        assertEquals(2, harness.audioClipService.cutClipCalls.size)
        harness.audioClipService.cutClipCalls.forEach { call ->
            assertEquals(true, call.startSample in originalStartStops, "cut start must be an original take offset, unchanged")
            assertEquals(true, call.stopSample in originalStartStops, "cut stop must be an original take offset, unchanged")
        }
        assertEquals(2, vocalTaskIndexes.size)
    }
}
