package org.example.app.domain.upload

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.example.app.domain.session.Examination
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeFileHashService
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeUploadApi
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.fakes.ImmediateCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UploadSessionUseCaseTest {

    private val folderName = "2026-07-01_HC001_s1"

    private class Fixture {
        val sessionRepository = FakeSessionRepository()
        val uploadStatusRepository = FakeUploadStatusRepository()
        val uploadApi = FakeUploadApi()
        val fileHashService = FakeFileHashService(default = "abc123")
        val clock = FakeClock()
        val useCase = UploadSessionUseCase(
            sessionRepository = sessionRepository,
            uploadStatusRepository = uploadStatusRepository,
            uploadApi = uploadApi,
            fileHashService = fileHashService,
            clock = clock,
            dispatchers = ImmediateCoroutineDispatchers(),
        )
    }

    private fun Fixture.setUpProcessedSession(folderName: String) {
        sessionRepository.createSessionDirectory(folderName)
        sessionRepository.writeExamination(
            folderName,
            Examination(sessionId = "s1", installationId = "install1", protocolName = "Share", configVersion = "1", startedAt = "t"),
        )
        sessionRepository.seedArchiveExists(folderName)
    }

    @Test
    fun `refuses to upload a session with no processed archive`() {
        val f = Fixture()
        f.sessionRepository.createSessionDirectory(folderName)
        f.sessionRepository.writeExamination(
            folderName,
            Examination(sessionId = "s1", installationId = "install1", protocolName = "Share", configVersion = "1", startedAt = "t"),
        )
        // No seedArchiveExists() — session was never processed.

        val outcome = runBlocking { f.useCase.upload(folderName) }

        assertTrue(outcome is UploadSessionUseCase.Outcome.Refused)
        assertTrue(f.uploadApi.calls.isEmpty())
        assertNull(f.uploadStatusRepository.read(folderName)) // never touched
    }

    @Test
    fun `refuses to upload an already-Uploaded session without calling the network`() {
        val f = Fixture()
        f.setUpProcessedSession(folderName)
        val alreadyUploaded = UploadStatus(status = UploadStatusValue.Uploaded, uploadedAt = "2026-07-01T09:00:00Z")
        f.uploadStatusRepository.write(folderName, alreadyUploaded)

        val outcome = runBlocking { f.useCase.upload(folderName) }

        assertTrue(outcome is UploadSessionUseCase.Outcome.Refused)
        assertTrue(f.uploadApi.calls.isEmpty())
        assertEquals(alreadyUploaded, f.uploadStatusRepository.read(folderName)) // untouched
    }

    @Test
    fun `NotUploaded to Uploading to Uploaded on success, with an attempt recorded`() {
        val f = Fixture()
        f.setUpProcessedSession(folderName)
        f.uploadApi.nextResult = UploadResult.Success(serverResponse = JsonPrimitive("ok"))
        f.clock.set(java.time.Instant.parse("2026-07-01T10:00:00Z"))

        val outcome = runBlocking { f.useCase.upload(folderName) }

        assertTrue(outcome is UploadSessionUseCase.Outcome.Success)
        val status = f.uploadStatusRepository.read(folderName)!!
        assertEquals(UploadStatusValue.Uploaded, status.status)
        assertEquals("2026-07-01T10:00:00Z", status.uploadedAt)
        assertEquals("abc123", status.zipSha256)
        assertEquals(1, status.attemptCount)
        assertEquals(1, status.attempts.size)
        assertEquals(JsonPrimitive("ok"), status.serverResponse)

        val call = f.uploadApi.calls.single()
        assertEquals("install1", call.installationId)
        assertEquals("s1", call.sessionId)
        assertEquals("abc123", call.zipSha256)
    }

    @Test
    fun `server rejection transitions to Failed with the rejection reason`() {
        val f = Fixture()
        f.setUpProcessedSession(folderName)
        f.uploadApi.nextResult = UploadResult.Rejected("unknown installation id")

        val outcome = runBlocking { f.useCase.upload(folderName) }

        assertTrue(outcome is UploadSessionUseCase.Outcome.Failed)
        assertEquals("unknown installation id", (outcome as UploadSessionUseCase.Outcome.Failed).reason)
        assertEquals(UploadStatusValue.Failed, f.uploadStatusRepository.read(folderName)!!.status)
    }

    @Test
    fun `network failure transitions to Failed and increments attemptCount across retries`() {
        val f = Fixture()
        f.setUpProcessedSession(folderName)
        f.uploadApi.nextResult = UploadResult.NetworkFailure

        runBlocking { f.useCase.upload(folderName) }
        var status = f.uploadStatusRepository.read(folderName)!!
        assertEquals(UploadStatusValue.Failed, status.status)
        assertEquals(1, status.attemptCount)

        runBlocking { f.useCase.upload(folderName) } // manual retry
        status = f.uploadStatusRepository.read(folderName)!!
        assertEquals(UploadStatusValue.Failed, status.status)
        assertEquals(2, status.attemptCount)
        assertEquals(2, status.attempts.size)
    }

    @Test
    fun `an exception thrown by UploadApi is treated as a network failure, not propagated`() {
        val f = Fixture()
        f.setUpProcessedSession(folderName)
        f.uploadApi.nextResult = UploadResult.Success(null) // ignored — override via a throwing fake below
        val throwingApi = object : org.example.app.domain.upload.UploadApi {
            override suspend fun uploadSession(
                zip: java.nio.file.Path,
                installationId: String,
                sessionId: String,
                zipSha256: String,
                onProgress: (Float) -> Unit,
            ): UploadResult = throw java.io.IOException("connection reset")
        }
        val useCase = UploadSessionUseCase(
            sessionRepository = f.sessionRepository,
            uploadStatusRepository = f.uploadStatusRepository,
            uploadApi = throwingApi,
            fileHashService = f.fileHashService,
            clock = f.clock,
            dispatchers = ImmediateCoroutineDispatchers(),
        )

        val outcome = runBlocking { useCase.upload(folderName) }

        assertTrue(outcome is UploadSessionUseCase.Outcome.Failed)
        assertEquals(UploadStatusValue.Failed, f.uploadStatusRepository.read(folderName)!!.status)
    }

    @Test
    fun `server error transitions to Failed with the status code in the reason`() {
        val f = Fixture()
        f.setUpProcessedSession(folderName)
        f.uploadApi.nextResult = UploadResult.ServerError(503)

        val outcome = runBlocking { f.useCase.upload(folderName) }

        assertTrue(outcome is UploadSessionUseCase.Outcome.Failed)
        assertTrue((outcome as UploadSessionUseCase.Outcome.Failed).reason.contains("503"))
    }

    @Test
    fun `zip SHA-256 is recomputed fresh on every upload attempt`() {
        val f = Fixture()
        f.setUpProcessedSession(folderName)
        f.uploadApi.nextResult = UploadResult.NetworkFailure // keep status retry-eligible across both calls

        runBlocking { f.useCase.upload(folderName) }
        f.fileHashService.respondWith(f.uploadApi.calls[0].zip, "new-hash-after-reprocess")
        runBlocking { f.useCase.upload(folderName) }

        assertEquals(2, f.fileHashService.hashedFiles.size)
        assertEquals("new-hash-after-reprocess", f.uploadApi.calls[1].zipSha256)
    }
}
