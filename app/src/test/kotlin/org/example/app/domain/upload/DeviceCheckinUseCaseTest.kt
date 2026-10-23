package org.example.app.domain.upload

import kotlinx.coroutines.test.runTest
import org.example.app.domain.session.Examination
import org.example.app.domain.settings.AppSettings
import org.example.app.domain.settings.InstallationIdProvider
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeDeviceCheckinApi
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.fakes.ImmediateCoroutineDispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceCheckinUseCaseTest {

    private class Fixture(siteToken: String? = "paris000paris000") {
        val settingsRepository = FakeAppSettingsRepository().apply {
            write(AppSettings(siteToken = siteToken, installationId = "install-1"))
        }
        val sessionRepository = FakeSessionRepository()
        val uploadStatusRepository = FakeUploadStatusRepository()
        val api = FakeDeviceCheckinApi()
        val useCase = DeviceCheckinUseCase(
            settingsRepository = settingsRepository,
            installationIdProvider = InstallationIdProvider(settingsRepository),
            eligibleUploadsQuery = EligibleUploadsQuery(sessionRepository, uploadStatusRepository),
            api = api,
            appVersion = "1.4.0",
            dispatchers = ImmediateCoroutineDispatchers(),
        )

        fun seed(folderName: String, status: UploadStatusValue?) {
            sessionRepository.createSessionDirectory(folderName)
            sessionRepository.writeExamination(
                folderName,
                Examination(sessionId = folderName, installationId = "install-1", protocolName = "Share", configVersion = "1", startedAt = "t"),
            )
            sessionRepository.seedArchiveExists(folderName)
            if (status != null) uploadStatusRepository.write(folderName, UploadStatus(status = status))
        }
    }

    @Test
    fun `reports pending and failed counts with the token, installation id and version`() = runTest {
        val f = Fixture()
        f.seed("a", null)
        f.seed("b", UploadStatusValue.NotUploaded)
        f.seed("c", UploadStatusValue.Failed)
        f.seed("d", UploadStatusValue.Uploading) // crash leftover, reported as failed (decision 35)
        f.seed("e", UploadStatusValue.Uploaded)

        f.useCase.checkIn()

        assertEquals(listOf(DeviceCheckin("paris000paris000", "install-1", "1.4.0", pending = 2, failed = 2)), f.api.calls)
    }

    @Test
    fun `no check-in without a site token`() = runTest {
        val f = Fixture(siteToken = " ")

        f.useCase.checkIn()

        assertTrue(f.api.calls.isEmpty())
    }

    @Test
    fun `a 409 raises the other-site flag and the next recorded check-in clears it`() = runTest {
        val f = Fixture()
        f.api.nextResult = DeviceCheckinResult.RegisteredToAnotherSite

        f.useCase.checkIn()
        assertTrue(f.useCase.registeredToAnotherSite.value)

        f.api.nextResult = DeviceCheckinResult.Failed
        f.useCase.checkIn()
        assertTrue(f.useCase.registeredToAnotherSite.value, "a failed check-in says nothing new")

        f.api.nextResult = DeviceCheckinResult.Recorded
        f.useCase.checkIn()
        assertFalse(f.useCase.registeredToAnotherSite.value)
    }

    @Test
    fun `an exception never escapes`() = runTest {
        val f = Fixture()
        val throwing = DeviceCheckinUseCase(
            settingsRepository = f.settingsRepository,
            installationIdProvider = InstallationIdProvider(f.settingsRepository),
            eligibleUploadsQuery = EligibleUploadsQuery(f.sessionRepository, f.uploadStatusRepository),
            api = object : DeviceCheckinApi {
                override suspend fun checkIn(checkin: DeviceCheckin): DeviceCheckinResult = throw IllegalStateException("boom")
            },
            appVersion = "1.4.0",
            dispatchers = ImmediateCoroutineDispatchers(),
        )

        throwing.checkIn()
    }
}
