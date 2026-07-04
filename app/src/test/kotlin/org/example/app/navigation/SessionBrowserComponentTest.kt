package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
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

        fun build(): SessionBrowserComponent = DefaultSessionBrowserComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
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
    fun `refresh reloads the list from the repositories`() {
        val h = Harness()
        val component = h.build()
        assertEquals(0, component.state.value.rows.size)

        h.seed("folder-a", "session-a")
        component.refresh()

        assertEquals(1, component.state.value.rows.size)
    }
}
