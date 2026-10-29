package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.pause
import com.arkivanov.essenty.lifecycle.resume
import org.example.app.domain.session.Examination
import org.example.app.domain.session.ProcessingInfo
import org.example.app.domain.session.ProcessingStatus
import org.example.app.domain.upload.UploadStatus
import org.example.app.domain.upload.UploadStatusValue
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeUploadStatusRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** §8.11 session browser component tests (fakes only, no Compose). */
class SessionBrowserComponentTest {

    private class Harness {
        val sessionRepository = FakeSessionRepository()
        val uploadStatusRepository = FakeUploadStatusRepository()
        var openedEditorFor: String? = null
        var reprocessedFolder: String? = null
        var goToUploadCalled = 0
        var backCalled = 0

        fun seed(folderName: String, sessionId: String, recovered: Boolean = false, processing: ProcessingStatus? = null) {
            sessionRepository.createSessionDirectory(folderName)
            sessionRepository.writeExamination(
                folderName,
                Examination(
                    sessionId = sessionId,
                    installationId = "install-1",
                    protocolName = "Share",
                    configVersion = "v1",
                    startedAt = "2026-07-03T09:00:00Z",
                    recovered = recovered,
                    processing = processing?.let { ProcessingInfo(status = it) },
                ),
            )
        }

        fun build(lifecycle: LifecycleRegistry = LifecycleRegistry()): SessionBrowserComponent = DefaultSessionBrowserComponent(
            componentContext = DefaultComponentContext(lifecycle),
            sessionRepository = sessionRepository,
            uploadStatusRepository = uploadStatusRepository,
            onOpenEditorClicked = { folderName -> openedEditorFor = folderName },
            onReprocessClicked = { folderName -> reprocessedFolder = folderName },
            onGoToUploadClicked = { goToUploadCalled++ },
            onBackClicked = { backCalled++ },
        )
    }

    @Test
    fun `lists every session with its processing, upload, and recovered status`() {
        val h = Harness()
        h.seed("folder-a", "session-a", recovered = true, processing = ProcessingStatus.Done)
        h.uploadStatusRepository.write("folder-a", UploadStatus(status = UploadStatusValue.Uploaded))

        val component = h.build()

        val row = component.state.value.rows.single()
        assertEquals("session-a", row.sessionId)
        assertTrue(row.recovered)
        assertEquals(ProcessingStatus.Done, row.processingStatus)
        assertEquals(UploadStatusValue.Uploaded, row.uploadStatus)
    }

    @Test
    fun `a session with no upload attempt yet reports a null upload status`() {
        val h = Harness()
        h.seed("folder-a", "session-a")

        val component = h.build()

        assertNull(component.state.value.rows.single().uploadStatus)
    }

    @Test
    fun `open editor, reprocess, go-to-upload, and back all forward to the caller`() {
        val h = Harness()
        h.seed("folder-a", "session-a")
        val component = h.build()

        component.onOpenEditor("folder-a")
        component.onReprocess("folder-a")
        component.onGoToUpload()
        component.onBack()

        assertEquals("folder-a", h.openedEditorFor)
        assertEquals("folder-a", h.reprocessedFolder)
        assertEquals(1, h.goToUploadCalled)
        assertEquals(1, h.backCalled)
    }

    @Test
    fun `rows refresh by themselves when the screen is resumed after processing or an upload`() {
        val h = Harness()
        h.seed("folder-a", "session-a", processing = ProcessingStatus.NotProcessed)
        h.uploadStatusRepository.write("folder-a", UploadStatus(status = UploadStatusValue.Failed))
        val lifecycle = LifecycleRegistry()
        val component = h.build(lifecycle)
        lifecycle.resume()
        assertEquals(UploadStatusValue.Failed, component.state.value.rows.single().uploadStatus)

        // Another screen is pushed on top; the browser pauses while processing / upload run.
        lifecycle.pause()
        h.uploadStatusRepository.write("folder-a", UploadStatus(status = UploadStatusValue.Uploaded))
        h.seed("folder-a", "session-a", processing = ProcessingStatus.Done)
        lifecycle.resume() // Back pops to the browser

        val row = component.state.value.rows.single()
        assertEquals(UploadStatusValue.Uploaded, row.uploadStatus)
        assertEquals(ProcessingStatus.Done, row.processingStatus)
    }

    @Test
    fun `refresh reloads the list from the repositories`() {
        val h = Harness()
        val component = h.build()
        assertEquals(0, component.state.value.rows.size)

        h.seed("folder-a", "session-a")
        component.refresh()

        assertEquals(1, component.state.value.rows.size)
    }

    @Test
    fun `open editor is refused for an Uploaded session but allowed for others (F-066)`() {
        val h = Harness()
        h.seed("folder-up", "session-up")
        h.seed("folder-failed", "session-failed")
        h.uploadStatusRepository.write("folder-up", UploadStatus(status = UploadStatusValue.Uploaded))
        h.uploadStatusRepository.write("folder-failed", UploadStatus(status = UploadStatusValue.Failed))
        val component = h.build()

        component.onOpenEditor("folder-up")
        assertNull(h.openedEditorFor)

        component.onOpenEditor("folder-failed")
        assertEquals("folder-failed", h.openedEditorFor)
    }

    @Test
    fun `reprocess is offered only for failed, not-processed and stuck sessions, never for processed or uploaded ones (F-104)`() {
        val h = Harness()
        h.seed("f-failed", "s-failed", processing = ProcessingStatus.Failed)
        h.seed("f-new", "s-new", processing = ProcessingStatus.NotProcessed)
        h.seed("f-none", "s-none", processing = null)
        h.seed("f-stuck", "s-stuck", processing = ProcessingStatus.Processing)
        h.seed("f-done", "s-done", processing = ProcessingStatus.Done)
        h.seed("f-up", "s-up", processing = ProcessingStatus.Failed)
        h.uploadStatusRepository.write("f-up", UploadStatus(status = UploadStatusValue.Uploaded))

        val can = h.build().state.value.rows.associate { it.sessionId to it.canReprocess }

        assertEquals(
            mapOf("s-failed" to true, "s-new" to true, "s-none" to true, "s-stuck" to true, "s-done" to false, "s-up" to false),
            can,
        )
    }

    @Test
    fun `a result message is shown, survives the resume refresh and is gone after leaving and returning (F-104)`() {
        val h = Harness()
        h.seed("folder-a", "session-a", processing = ProcessingStatus.Done)
        val lifecycle = LifecycleRegistry()
        val component = h.build(lifecycle)
        lifecycle.resume()
        assertNull(component.state.value.message)

        // Processing ran on top of the browser (paused); the root reports the result, then pops.
        lifecycle.pause()
        component.showResult(SessionBrowserComponent.Message.Processed("HC001", 3))
        lifecycle.resume()
        assertEquals(SessionBrowserComponent.Message.Processed("HC001", 3), component.state.value.message)
        component.refresh()
        assertEquals(SessionBrowserComponent.Message.Processed("HC001", 3), component.state.value.message)

        // Leaving Sessions (upload screen, editor, ...) and coming back shows no stale message.
        lifecycle.pause()
        lifecycle.resume()
        assertNull(component.state.value.message)
    }
}
