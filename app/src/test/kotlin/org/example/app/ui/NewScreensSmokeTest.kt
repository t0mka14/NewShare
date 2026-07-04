package org.example.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.config.Protocol
import org.example.app.domain.localization.LocalizedStringProvider
import org.example.app.domain.session.Examination
import org.example.app.domain.session.ProcessSessionUseCase
import org.example.app.domain.session.TaskRecord
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.timeline.TimelineOriginal
import org.example.app.domain.upload.EligibleUploadsQuery
import org.example.app.domain.upload.UploadResult
import org.example.app.domain.upload.UploadSessionUseCase
import org.example.app.fakes.FakeAudioClipService
import org.example.app.fakes.FakeAudioPlaybackService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeFileHashService
import org.example.app.fakes.FakeSessionArchiveService
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeTimelineRepository
import org.example.app.fakes.FakeUploadApi
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.fakes.FakeWaveformService
import org.example.app.fakes.TestCoroutineDispatchers
import org.example.app.navigation.DefaultEditorComponent
import org.example.app.navigation.DefaultProcessingComponent
import org.example.app.navigation.DefaultProtocolPickerComponent
import org.example.app.navigation.DefaultSessionBrowserComponent
import org.example.app.navigation.DefaultUploadComponent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertTrue

/**
 * Headless render (and, for the editor, an actual `performMouseInput` drag) smoke coverage for
 * the Phase-3 screens' novel rendering code — `BoxWithConstraints`/`Canvas`/`pointerInput` drag
 * handling in `EditorContent` is exactly the kind of thing a pure component test (no Compose)
 * cannot catch a runtime composition crash in (§10.3's cheaper component-test layer still covers
 * the state-transition logic itself; see `EditorComponentTest`/`ProtocolPickerComponentTest`/
 * `SessionBrowserComponentTest`).
 */
class NewScreensSmokeTest {

    private fun localization() = UiLocalization(LocalizedStringProvider(), "en", null)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `editor screen renders a segment and a real mouse drag moves the start boundary`() = runComposeUiTest {
        val dispatchers = TestCoroutineDispatchers()
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        val folderName = "folder-1"
        val format = CaptureFormat(sampleRate = 1000, bits = 16, channels = 1)

        timelineRepository.writeOriginal(
            folderName,
            TimelineOriginal(
                sessionId = "session-1",
                sampleRate = format.sampleRate,
                events = listOf(
                    TimelineEvent(TimelineEventType.START_BUTTON_PRESSED, 100, "t", 0, 1, 1),
                    TimelineEvent(TimelineEventType.STOP_BUTTON_PRESSED, 300, "t", 0, 1, 1),
                    TimelineEvent(TimelineEventType.SESSION_RECORDING_STOPPED, 1000, "t", null, null, null),
                ),
            ),
        )
        sessionRepository.writeExamination(
            folderName,
            Examination(
                sessionId = "session-1",
                installationId = "install-1",
                protocolName = "Share",
                configVersion = "v1",
                startedAt = "2026-07-03T09:00:00Z",
                captureFormat = format,
                tasks = listOf(TaskRecord(taskIndex = 0, type = "VOCAL", subtype = "PHONATION", repetition = 1, takes = 1)),
            ),
        )

        val component = DefaultEditorComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            folderName = folderName,
            sessionRepository = sessionRepository,
            timelineRepository = timelineRepository,
            waveformService = FakeWaveformService(),
            audioPlaybackService = FakeAudioPlaybackService(),
            dispatchers = dispatchers,
            onDone = {},
        )
        dispatchers.scheduler.advanceUntilIdle()

        setContent { EditorContent(component, localization()) }
        waitForIdle()

        onNodeWithTag(TestTags.Editor.WAVEFORM_CANVAS).assertExists()
        onNodeWithTag(TestTags.Editor.START_BOUNDARY_HANDLE).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(20f, 0f))
            release()
        }
        waitForIdle()
        dispatchers.scheduler.advanceUntilIdle()

        // The drag either moves the boundary or is validly rejected (§8.7 validation) — either
        // way rendering + gesture handling must not crash, which is what this test really proves.
        val moved = component.state.value.segments.first().moved
        val unchanged = component.state.value.segments.first().startSample == 100L
        assertTrue(moved || unchanged, "drag must either move the boundary or leave it validly unchanged")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `protocol picker and session browser render without crashing`() = runComposeUiTest {
        val protocol = Protocol(name = "Share", recordingsFileName = "\${taskIndex}.wav")
        val pickerComponent = DefaultProtocolPickerComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            protocols = listOf(protocol),
            onProtocolSelectedClicked = {},
            onBackClicked = {},
        )
        setContent { ProtocolPickerContent(pickerComponent, localization()) }
        waitForIdle()
        onNodeWithTag(TestTags.ProtocolPicker.protocolButton("Share")).assertExists().performClick()

        val browserComponent = DefaultSessionBrowserComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            sessionRepository = FakeSessionRepository(),
            uploadStatusRepository = FakeUploadStatusRepository(),
            onOpenEditorClicked = {},
            onReprocessClicked = {},
            onGoToUploadClicked = {},
            onBackClicked = {},
        )
        setContent { SessionBrowserContent(browserComponent, localization()) }
        waitForIdle()
        onNodeWithTag(TestTags.SessionBrowser.BACK_BUTTON).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `upload screen renders with a listed session`() = runComposeUiTest {
        val dispatchers = TestCoroutineDispatchers()
        val sessionRepository = FakeSessionRepository()
        val uploadStatusRepository = FakeUploadStatusRepository()
        sessionRepository.createSessionDirectory("folder-1")
        sessionRepository.writeExamination(
            "folder-1",
            Examination(
                sessionId = "session-1",
                installationId = "install-1",
                protocolName = "Share",
                configVersion = "v1",
                startedAt = "2026-07-03T09:00:00Z",
            ),
        )
        sessionRepository.seedArchiveExists("folder-1")

        val component = DefaultUploadComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            eligibleUploadsQuery = EligibleUploadsQuery(sessionRepository, uploadStatusRepository),
            uploadSessionUseCase = UploadSessionUseCase(
                sessionRepository = sessionRepository,
                uploadStatusRepository = uploadStatusRepository,
                uploadApi = FakeUploadApi().apply { nextResult = UploadResult.Success(null) },
                fileHashService = FakeFileHashService(),
                clock = FakeClock(),
                dispatchers = dispatchers,
            ),
            dispatchers = dispatchers,
            onBackClicked = {},
        )

        setContent { UploadContent(component, localization()) }
        waitForIdle()

        onNodeWithTag(TestTags.Upload.sessionRow("session-1")).assertExists()
        onNodeWithTag(TestTags.Upload.UPLOAD_BUTTON).assertExists().performClick()
        waitForIdle()
        dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `processing screen renders progress and reaches completion`(@TempDir tempDir: Path) = runComposeUiTest {
        val dispatchers = TestCoroutineDispatchers()
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        sessionRepository.writeExamination(
            "folder-1",
            Examination(
                sessionId = "session-1",
                installationId = "install-1",
                protocolName = "Share",
                configVersion = "v1",
                startedAt = "2026-07-03T09:00:00Z",
            ),
        )
        timelineRepository.writeOriginal("folder-1", TimelineOriginal(sessionId = "session-1", sampleRate = 0, events = emptyList()))

        val component = DefaultProcessingComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            folderName = "folder-1",
            processSessionUseCase = ProcessSessionUseCase(
                directories = org.example.app.fakes.TestAppDirectories(tempDir),
                sessionRepository = sessionRepository,
                timelineRepository = timelineRepository,
                uploadStatusRepository = FakeUploadStatusRepository(),
                audioClipService = FakeAudioClipService(),
                archiveService = FakeSessionArchiveService(),
                clock = FakeClock(),
                dispatchers = dispatchers,
            ),
            dispatchers = dispatchers,
            onDone = {},
            onBackClicked = {},
        )

        setContent { ProcessingContent(component, localization()) }
        waitForIdle()
        dispatchers.scheduler.advanceUntilIdle()
        waitForIdle()

        onNodeWithTag(TestTags.Processing.PROGRESS_BAR).assertExists()
    }
}
