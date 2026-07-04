package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.session.Examination
import org.example.app.domain.session.ProcessSessionUseCase
import org.example.app.domain.timeline.TimelineOriginal
import org.example.app.fakes.FakeAudioClipService
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeSessionArchiveService
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeTimelineRepository
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.fakes.TestAppDirectories
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** §8.8 processing progress component tests (fakes only, no Compose). */
class ProcessingComponentTest {

    private val folderName = "2026-07-03_HC001_session-1"

    private inner class Harness(tempDir: Path, seedSession: Boolean) {
        val dispatchers = TestCoroutineDispatchers()
        val directories = TestAppDirectories(tempDir)
        val sessionRepository = FakeSessionRepository()
        val timelineRepository = FakeTimelineRepository()
        val uploadStatusRepository = FakeUploadStatusRepository()
        var doneCalledWith: String? = null
        var backCalled = 0

        val processSessionUseCase = ProcessSessionUseCase(
            directories = directories,
            sessionRepository = sessionRepository,
            timelineRepository = timelineRepository,
            uploadStatusRepository = uploadStatusRepository,
            audioClipService = FakeAudioClipService(),
            archiveService = FakeSessionArchiveService(),
            clock = FakeClock(),
            dispatchers = dispatchers,
        )

        init {
            if (seedSession) {
                // No-master (questionnaire-only) session — the simplest path through
                // ProcessSessionUseCase (§8.8: "steps 2-3 are skipped").
                sessionRepository.writeExamination(
                    "session-folder".let { folderName },
                    Examination(
                        sessionId = "session-1",
                        installationId = "install-1",
                        protocolName = "QOnly",
                        configVersion = "v1",
                        startedAt = "2026-07-03T09:00:00Z",
                    ),
                )
                timelineRepository.writeOriginal(folderName, TimelineOriginal(sessionId = "session-1", sampleRate = 0, events = emptyList()))
            }
        }

        fun build(): ProcessingComponent = DefaultProcessingComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            folderName = folderName,
            processSessionUseCase = processSessionUseCase,
            dispatchers = dispatchers,
            onDone = { folder -> doneCalledWith = folder },
            onBackClicked = { backCalled++ },
        )
    }

    @Test
    fun `reports step progress and completes to Done, notifying onDone`(@TempDir tempDir: Path) {
        val h = Harness(tempDir, seedSession = true)
        val component = h.build()
        h.dispatchers.scheduler.advanceUntilIdle()

        val state = component.state.value
        assertFalse(state.running)
        assertFalse(state.failed)
        assertEquals(1f, state.fraction)
        assertEquals(ProcessSessionUseCase.Step.UPDATING_METADATA, state.step)
        assertEquals(folderName, h.doneCalledWith)
    }

    @Test
    fun `a missing examination surfaces as failed and does not call onDone`(@TempDir tempDir: Path) {
        val h = Harness(tempDir, seedSession = false) // no examination.json ever written
        val component = h.build()
        h.dispatchers.scheduler.advanceUntilIdle()

        val state = component.state.value
        assertTrue(state.failed)
        assertFalse(state.running)
        assertEquals(null, h.doneCalledWith)
    }

    @Test
    fun `retry after a failure re-runs processing`(@TempDir tempDir: Path) {
        val h = Harness(tempDir, seedSession = false)
        val component = h.build()
        h.dispatchers.scheduler.advanceUntilIdle()
        assertTrue(component.state.value.failed)

        // Fix the underlying cause between attempts, then retry — mirrors the examiner retrying
        // once whatever made processing fail (e.g. disk issue) is resolved out of band.
        h.sessionRepository.writeExamination(
            folderName,
            Examination(
                sessionId = "session-1",
                installationId = "install-1",
                protocolName = "QOnly",
                configVersion = "v1",
                startedAt = "2026-07-03T09:00:00Z",
            ),
        )
        h.timelineRepository.writeOriginal(folderName, TimelineOriginal(sessionId = "session-1", sampleRate = 0, events = emptyList()))

        component.onRetry()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertFalse(component.state.value.failed)
        assertEquals(folderName, h.doneCalledWith)
    }

    @Test
    fun `retry is a no-op while a run is already in progress`(@TempDir tempDir: Path) {
        val h = Harness(tempDir, seedSession = true)
        val component = h.build()
        // Before advancing the dispatcher the first run is still "running" (default State()).
        assertTrue(component.state.value.running)

        component.onRetry() // must not restart mid-flight

        h.dispatchers.scheduler.advanceUntilIdle()
        assertEquals(folderName, h.doneCalledWith)
    }

    @Test
    fun `onBack forwards to the caller`(@TempDir tempDir: Path) {
        val h = Harness(tempDir, seedSession = true)
        val component = h.build()

        component.onBack()

        assertEquals(1, h.backCalled)
    }
}
