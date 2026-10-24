package org.example.app.infrastructure.logging

import ch.qos.logback.classic.Level
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import org.example.app.domain.config.ConfigApplyResult
import org.example.app.domain.config.RefreshConfigurationUseCase
import org.example.app.domain.config.RemoteConfig
import org.example.app.domain.session.Examination
import org.example.app.domain.settings.AppSettings
import org.example.app.domain.settings.InstallationIdProvider
import org.example.app.domain.upload.DeviceCheckin
import org.example.app.domain.upload.DeviceCheckinResult
import org.example.app.domain.upload.DeviceCheckinUseCase
import org.example.app.domain.upload.EligibleUploadsQuery
import org.example.app.domain.upload.UploadResult
import org.example.app.domain.upload.UploadSessionUseCase
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeClock
import org.example.app.fakes.FakeConfigApi
import org.example.app.fakes.FakeConfigurationRepository
import org.example.app.fakes.FakeFileHashService
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeUploadApi
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.fakes.ImmediateCoroutineDispatchers
import org.example.app.infrastructure.network.KtorDeviceCheckinApi
import org.example.app.infrastructure.network.KtorUploadApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** F-038: config fetch, device check-in and upload leave log lines, and none carries a secret. */
class NetworkLoggingTest {

    private val siteToken = "SECRETsiteTOKEN000000"
    private val installationId = "SECRET-INSTALLATION-ID-42"

    @Test
    fun `a config refresh logs one INFO line with the configVersion and site, never the token`() {
        val config = RemoteConfig(schemaVersion = 1, configVersion = "2026-10-24.3", defaultLanguage = "en", siteName = "Paris")
        val repo = FakeConfigurationRepository().also { it.enqueueApplyResult(ConfigApplyResult.Applied(config)) }
        val api = FakeConfigApi().also { it.enqueueSuccess("{}") }
        val settings = FakeAppSettingsRepository().also { it.write(AppSettings(siteToken = siteToken)) }

        val logs = captureLogs { runBlocking { RefreshConfigurationUseCase(settings, api, repo).refresh() } }

        val lines = logs.filter { it.formattedMessage.startsWith("config refresh") }
        assertEquals(1, lines.size)
        assertEquals(Level.INFO, lines.single().level)
        assertTrue(lines.single().formattedMessage.contains("configVersion=2026-10-24.3"))
        assertTrue(lines.single().formattedMessage.contains("site=Paris"))
        assertFalse(logs.any { it.fullText.contains(siteToken) })
    }

    @Test
    fun `a failed config refresh logs a WARN with the HTTP status`() {
        val api = FakeConfigApi().also { it.enqueueServerError(503) }
        val settings = FakeAppSettingsRepository().also { it.write(AppSettings(siteToken = siteToken)) }

        val logs = captureLogs {
            runBlocking { RefreshConfigurationUseCase(settings, api, FakeConfigurationRepository()).refresh() }
        }

        val line = logs.single { it.formattedMessage.startsWith("config refresh") }
        assertEquals(Level.WARN, line.level)
        assertTrue(line.formattedMessage.contains("HTTP 503"))
        assertFalse(logs.any { it.fullText.contains(siteToken) })
    }

    @Test
    fun `a device check-in logs its HTTP status and outcome, never the token or the installation id`() {
        val engine = MockEngine { respond(content = """{"status":"ok"}""", status = HttpStatusCode.OK) }
        val settings = FakeAppSettingsRepository().also { it.write(AppSettings(siteToken = siteToken, installationId = installationId)) }
        val useCase = DeviceCheckinUseCase(
            settingsRepository = settings,
            installationIdProvider = InstallationIdProvider(settings),
            eligibleUploadsQuery = EligibleUploadsQuery(FakeSessionRepository(), FakeUploadStatusRepository()),
            api = KtorDeviceCheckinApi(engine = engine),
            appVersion = "1.4.0",
            dispatchers = ImmediateCoroutineDispatchers(),
        )

        val logs = captureLogs { runBlocking { useCase.checkIn() } }

        assertTrue(logs.any { it.level == Level.INFO && it.formattedMessage == "device check-in: HTTP 200" })
        assertTrue(logs.any { it.level == Level.INFO && it.formattedMessage == "device check-in: ok" })
        assertFalse(logs.any { it.fullText.contains(siteToken) || it.fullText.contains(installationId) })
    }

    @Test
    fun `a conflicting device check-in logs a WARN`() {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.Conflict) }
        val checkin = DeviceCheckin(siteToken, installationId, "1.4.0", pending = 1, failed = 0)

        val logs = captureLogs {
            runBlocking {
                assertEquals(DeviceCheckinResult.RegisteredToAnotherSite, KtorDeviceCheckinApi(engine = engine).checkIn(checkin))
            }
        }

        assertTrue(logs.any { it.level == Level.WARN && it.formattedMessage.contains("HTTP 409") })
        assertFalse(logs.any { it.fullText.contains(siteToken) || it.fullText.contains(installationId) })
    }

    @Test
    fun `an upload call logs the sessionId and the HTTP status 200, never the token`(@TempDir dir: Path) {
        val zip = dir.resolve("session.zip").also { Files.write(it, ByteArray(1024) { b -> b.toByte() }) }
        val engine = MockEngine { request ->
            (request.body as OutgoingContent.ReadChannelContent).readFrom().readRemaining().readBytes()
            respond(content = "", status = HttpStatusCode.OK)
        }

        val logs = captureLogs {
            runBlocking { KtorUploadApi(engine = engine).uploadSession(zip, installationId, "s-77", "abc", siteToken, "1.4.0") {} }
        }

        val line = logs.single { it.formattedMessage.contains("s-77") }
        assertEquals(Level.INFO, line.level)
        assertTrue(line.formattedMessage.contains("HTTP 200"))
        assertFalse(logs.any { it.fullText.contains(siteToken) || it.fullText.contains(installationId) })
    }

    @Test
    fun `an upload attempt logs sessionId, attempt number, outcome and the retry decision`() {
        val sessions = FakeSessionRepository()
        val folder = "2026-07-01_HC001_s1"
        sessions.createSessionDirectory(folder)
        sessions.writeExamination(
            folder,
            Examination(sessionId = "s1", installationId = installationId, protocolName = "Share", configVersion = "1", startedAt = "t"),
        )
        sessions.seedArchiveExists(folder)
        val api = FakeUploadApi().also { it.nextResult = UploadResult.ServerError(503) }
        val settings = FakeAppSettingsRepository().also { it.write(AppSettings(siteToken = siteToken)) }
        val useCase = UploadSessionUseCase(
            sessions, FakeUploadStatusRepository(), api, settings, "1.2.3",
            FakeFileHashService(default = "abc"), FakeClock(), ImmediateCoroutineDispatchers(),
        )

        val logs = captureLogs { runBlocking { useCase.upload(folder) } }

        val line = logs.single { it.formattedMessage.startsWith("upload attempt") }
        assertEquals(Level.WARN, line.level)
        assertTrue(line.formattedMessage.contains("session=s1"))
        assertTrue(line.formattedMessage.contains("attempt=1"))
        assertTrue(line.formattedMessage.contains("outcome=ServerError(503)"))
        assertTrue(line.formattedMessage.contains("retry=manual"))
        assertFalse(logs.any { it.fullText.contains(siteToken) || it.fullText.contains(installationId) || it.fullText.contains("HC001") })
    }
}
