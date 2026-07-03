package org.example.app.ui.scenario

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.example.app.domain.audio.InterruptionReason
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.fakes.ConfigFixtures
import org.example.app.fakes.FakeAudioInputDeviceProvider
import org.example.app.ui.TestTags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * §10.3 workflow scenario 6: fake recorder signals device loss mid-take -> blocking dialog ->
 * select a new fake device -> resume -> redo the take -> gap events + auto-rejected take in the
 * timeline. Driven at the Compose UI level (via [ScenarioApp]) rather than component-only,
 * because the assertion "device-lost dialog appears (testTag)" requires actually rendering it —
 * the existing component-level coverage in `SessionComponentTest` (`device-loss resume during a
 * task instance...`) does not render Compose at all.
 */
class DeviceLossScenarioTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `scenario 6 - device loss mid-take shows the reconnect dialog and resumes on a new device`(
        @TempDir tempDir: Path,
    ) = runComposeUiTest {
        val harness = ScenarioHarness(tempDir)
        harness.loadConfig(ConfigFixtures.fullProtocol)

        setContent { ScenarioApp(harness) }

        onNodeWithTag(TestTags.MainMenu.START_PROTOCOL_BUTTON).performClick()
        onNodeWithTag(TestTags.PatientInfo.field("code")).performTextInput("HC003")
        onNodeWithTag(TestTags.PatientInfo.field("visitNumber")).performTextInput("V1")
        onNodeWithTag(TestTags.PatientInfo.CONTINUE_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        onNodeWithTag(TestTags.Calibration.CONFIRM_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        // Open take 1 on the first VOCAL instance, then simulate device loss mid-take.
        onNodeWithTag(TestTags.Task.START_BUTTON).performClick()
        advanceBoth(harness.clock, 300)

        val recorder = requireNotNull(harness.recorder) { "VOCAL protocol should have created a recorder" }
        recorder.simulateInterruption(InterruptionReason.DEVICE_LOST)
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        // Dialog appears (blocking, task-screen variant requires an explicit Resume).
        val deviceLostMessageTag = "${TestTags.Task.ERROR_DIALOG}.deviceLost"
        onNodeWithTag(deviceLostMessageTag).assertIsDisplayed()
        onNodeWithTag(TestTags.DeviceLostDialog.RESUME_BUTTON).assertIsDisplayed()

        val folderName = harness.sessionFolderName()
        val eventsAfterInterrupt = harness.container.timelineRepository.readEventLog(folderName).events
        assertTrue(eventsAfterInterrupt.any { it.type == TimelineEventType.RECORDING_INTERRUPTED })
        val rejection = eventsAfterInterrupt.single { it.type == TimelineEventType.TAKE_REJECTED }
        assertEquals("DEVICE_LOST", rejection.reason)
        assertEquals(1, rejection.take)

        // Select the secondary fake device, then resume.
        onNodeWithTag(TestTags.DeviceLostDialog.DEVICE_SELECT).performClick()
        onNodeWithTag("${TestTags.DeviceLostDialog.DEVICE_SELECT}.${FakeAudioInputDeviceProvider.SECONDARY_DEVICE.id}")
            .performClick()
        onNodeWithTag(TestTags.DeviceLostDialog.RESUME_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        // Dialog is gone; the examiner is back on Idle and can redo the take.
        onNodeWithTag(deviceLostMessageTag).assertDoesNotExist()

        val eventsAfterResume = harness.container.timelineRepository.readEventLog(folderName).events
        assertTrue(eventsAfterResume.any { it.type == TimelineEventType.RECORDING_RESUMED })

        val examination = harness.container.sessionRepository.readExamination(folderName)!!
        assertEquals(1, examination.interruptions.size)
        val interruption = examination.interruptions.single()
        assertEquals(FakeAudioInputDeviceProvider.DEFAULT_DEVICE.id, interruption.oldDevice)
        assertEquals(FakeAudioInputDeviceProvider.SECONDARY_DEVICE.id, interruption.newDevice)
        assertEquals("session_master.part2.wav", interruption.partFile)
        assertEquals(1, recorder.resumeCalls.size)

        // Redo the rejected take (take 2) and complete the task instance.
        onNodeWithTag(TestTags.Task.START_BUTTON).performClick()
        advanceBoth(harness.clock, 300)
        onNodeWithTag(TestTags.Task.STOP_BUTTON).performClick()
        onNodeWithTag(TestTags.Task.NEXT_BUTTON).performClick()
        harness.dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        val finalEvents = harness.container.timelineRepository.readEventLog(folderName).events
        val vocalTaskIndex = rejection.taskIndex
        val startsForInstance = finalEvents.filter {
            it.type == TimelineEventType.START_BUTTON_PRESSED && it.taskIndex == vocalTaskIndex
        }.map { it.take }
        assertEquals(listOf(1, 2), startsForInstance, "take 1 (auto-rejected) + take 2 (redone, accepted)")
    }
}
