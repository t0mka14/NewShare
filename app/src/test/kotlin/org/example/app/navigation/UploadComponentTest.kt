package org.example.app.navigation

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import org.example.app.domain.localization.BuiltinStrings
import org.example.app.domain.session.Examination
import org.example.app.domain.settings.AppSettings
import org.example.app.domain.settings.InstallationIdProvider
import org.example.app.domain.upload.DeviceCheckin
import org.example.app.domain.upload.DeviceCheckinUseCase
import org.example.app.domain.upload.EligibleUploadsQuery
import org.example.app.domain.upload.RejectionReason
import org.example.app.domain.upload.UploadApi
import org.example.app.domain.upload.UploadResult
import org.example.app.domain.upload.UploadSessionUseCase
import org.example.app.domain.upload.UploadStatus
import org.example.app.domain.upload.UploadStatusValue
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeDeviceCheckinApi
import org.example.app.fakes.FakeFileHashService
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.fakes.TestCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** §8.9/§13 decision 34 upload screen component tests (fakes only, no Compose). */
class UploadComponentTest {

    /** Per-sessionId scripted outcome, defaulting to [UploadResult.Success] — [org.example.app.fakes.FakeUploadApi]'s
     * single shared `nextResult` can't express "first session fails, second succeeds" within one
     * batch, which decision 34's "a failed session does not stop the batch" requires exercising. */
    private class ScriptedUploadApi(private val outcomes: Map<String, UploadResult> = emptyMap()) : UploadApi {
        val calls = mutableListOf<String>()

        override suspend fun uploadSession(
            zip: Path,
            installationId: String,
            sessionId: String,
            zipSha256: String,
            siteToken: String?,
            appVersion: String,
            onProgress: (Float) -> Unit,
        ): UploadResult {
            calls += sessionId
            onProgress(1.0f)
            return outcomes[sessionId] ?: UploadResult.Success(serverResponse = null)
        }
    }

    private class Harness(uploadApi: UploadApi = ScriptedUploadApi()) {
        val dispatchers = TestCoroutineDispatchers()
        val sessionRepository = FakeSessionRepository()
        val uploadStatusRepository = FakeUploadStatusRepository()
        val eligibleUploadsQuery = EligibleUploadsQuery(sessionRepository, uploadStatusRepository)
        val settingsRepository = FakeAppSettingsRepository().apply {
            write(AppSettings(siteToken = "site-token-0000000", installationId = "install-1"))
        }
        val checkinApi = FakeDeviceCheckinApi()
        val uploadSessionUseCase = UploadSessionUseCase(
            sessionRepository = sessionRepository,
            uploadStatusRepository = uploadStatusRepository,
            uploadApi = uploadApi,
            settingsRepository = settingsRepository,
            appVersion = "1.2.3",
            fileHashService = FakeFileHashService(),
            clock = FakeClock(),
            dispatchers = dispatchers,
        )
        var backCalled = 0

        fun seedSession(folderName: String, sessionId: String, patientCode: String) {
            sessionRepository.createSessionDirectory(folderName)
            sessionRepository.writeExamination(
                folderName,
                Examination(
                    sessionId = sessionId,
                    installationId = "install-1",
                    protocolName = "Share",
                    configVersion = "v1",
                    startedAt = "2026-07-03T09:00:00Z",
                ),
            )
            sessionRepository.seedArchiveExists(folderName)
        }

        /** Builds the component now, snapshotting whatever has been seeded so far — mirrors
         * `DefaultUploadComponent`'s own eager row snapshot at construction (§8.9: the list is
         * computed on demand, not live-observed). */
        fun build(): UploadComponent = DefaultUploadComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            eligibleUploadsQuery = eligibleUploadsQuery,
            uploadSessionUseCase = uploadSessionUseCase,
            dispatchers = dispatchers,
            onBackClicked = { backCalled++ },
            deviceCheckin = DeviceCheckinUseCase(
                settingsRepository = settingsRepository,
                installationIdProvider = InstallationIdProvider(settingsRepository),
                eligibleUploadsQuery = eligibleUploadsQuery,
                api = checkinApi,
                appVersion = "1.2.3",
                dispatchers = dispatchers,
            ),
        )
    }

    @Test
    fun `initial state lists every eligible session`() {
        val h = Harness()
        h.seedSession("folder-a", "session-a", "HC001")
        h.seedSession("folder-b", "session-b", "HC002")

        val component = h.build()

        assertEquals(2, component.state.value.rows.size)
        assertFalse(component.state.value.uploading)
    }

    @Test
    fun `a successful batch uploads every session and leaves the list empty`() {
        val h = Harness()
        h.seedSession("folder-a", "session-a", "HC001")
        h.seedSession("folder-b", "session-b", "HC002")
        val component = h.build()

        component.onUploadClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        val state = component.state.value
        assertTrue(state.rows.isEmpty())
        assertFalse(state.uploading)
        assertEquals(1f, state.overallProgress)
        assertEquals("upload.successMessage", state.batchResultKey)
    }

    @Test
    fun `a failed session does not stop the batch — it stays listed with a reason, the other still uploads`() {
        val uploadApi = ScriptedUploadApi(mapOf("session-a" to UploadResult.NetworkFailure))
        val h = Harness(uploadApi)
        h.seedSession("folder-a", "session-a", "HC001")
        h.seedSession("folder-b", "session-b", "HC002")
        val component = h.build()

        component.onUploadClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        // Both sessions were attempted despite the first one failing.
        assertEquals(listOf("session-a", "session-b"), uploadApi.calls)

        val state = component.state.value
        assertFalse(state.uploading)
        assertNull(state.batchResultKey) // not everything succeeded
        assertEquals(1, state.rows.size)
        val remaining = state.rows.single()
        assertEquals("session-a", remaining.sessionId)
        assertEquals(UploadStatusValue.Failed, remaining.status)
        assertEquals("upload.error.unreachable", remaining.failureReasonKey)

        assertEquals(UploadStatusValue.Uploaded, h.uploadStatusRepository.read("folder-b")!!.status)
    }

    @Test
    fun `the unreachable-server reason says the session is kept and can be retried later`() {
        assertTrue("kept" in BuiltinStrings.en.getValue("upload.error.unreachable"))
        assertTrue("later" in BuiltinStrings.en.getValue("upload.error.unreachable"))
        assertTrue("zůstává uloženo" in BuiltinStrings.cs.getValue("upload.error.unreachable"))
    }

    @Test
    fun `server answers that pressing Upload again cannot fix get their own messages`() {
        val uploadApi = ScriptedUploadApi(
            mapOf(
                "session-a" to UploadResult.Conflict,
                "session-b" to UploadResult.Rejected(RejectionReason.DeviceNotRegistered),
                "session-c" to UploadResult.ServerError(503),
                "session-d" to UploadResult.Rejected(RejectionReason.TooLarge),
                "session-e" to UploadResult.ServerError(500),
            ),
        )
        val h = Harness(uploadApi)
        for (id in listOf("a", "b", "c", "d", "e")) h.seedSession("folder-$id", "session-$id", "HC00$id")
        val component = h.build()

        component.onUploadClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        val keys = component.state.value.rows.associate { it.sessionId to it.failureReasonKey }
        assertEquals(
            mapOf(
                "session-a" to "upload.error.conflict",
                "session-b" to "upload.error.notRegistered",
                "session-c" to "upload.error.busy",
                "session-d" to "upload.error.tooLarge",
                "session-e" to "upload.error.server",
            ),
            keys,
        )
    }

    @Test
    fun `after a batch the computer checks in with what is left to upload`() {
        val h = Harness(ScriptedUploadApi(mapOf("session-a" to UploadResult.NetworkFailure)))
        h.seedSession("folder-a", "session-a", "HC001")
        h.seedSession("folder-b", "session-b", "HC002")
        val component = h.build()

        component.onUploadClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(DeviceCheckin("site-token-0000000", "install-1", "1.2.3", pending = 0, failed = 1)),
            h.checkinApi.calls,
        )
    }

    @Test
    fun `a leftover Uploading status is shown as an interrupted failure, per decision 35`() {
        val h = Harness()
        h.seedSession("folder-a", "session-a", "HC001")
        h.uploadStatusRepository.write("folder-a", UploadStatus(status = UploadStatusValue.Uploading))

        val component = h.build()

        val row = component.state.value.rows.single()
        assertEquals(UploadStatusValue.Failed, row.status)
        assertEquals("upload.error.interrupted", row.failureReasonKey)
    }

    @Test
    fun `uploading with an empty list is a no-op`() {
        val h = Harness()
        val component = h.build()

        component.onUploadClicked()
        h.dispatchers.scheduler.advanceUntilIdle()

        assertFalse(component.state.value.uploading)
        assertTrue(component.state.value.rows.isEmpty())
    }

    @Test
    fun `onBack forwards to the caller`() {
        val h = Harness()
        val component = h.build()

        component.onBack()

        assertEquals(1, h.backCalled)
    }
}
