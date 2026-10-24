package org.example.app.domain.config

import kotlinx.coroutines.test.runTest
import org.example.app.domain.settings.AppSettings
import org.example.app.domain.settings.InstallationIdProvider
import org.example.app.domain.upload.DeviceCheckinResult
import org.example.app.domain.upload.DeviceCheckinUseCase
import org.example.app.domain.upload.EligibleUploadsQuery
import org.example.app.fakes.ConfigFixtures
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeConfigApi
import org.example.app.fakes.FakeDeviceCheckinApi
import org.example.app.fakes.FakeSessionRepository
import org.example.app.fakes.FakeUploadStatusRepository
import org.example.app.fakes.ImmediateCoroutineDispatchers
import org.example.app.fakes.TestAppDirectories
import org.example.app.infrastructure.config.JsonConfigurationRepository
import org.example.app.infrastructure.config.RawConfigCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * F-041: a device registered to site A whose examiner enters site B's token. The fetch returns
 * B's config, but the check-in answers 409; B's config must be neither persisted nor activated.
 * Real [JsonConfigurationRepository]/[RawConfigCache] on a temp dir, fake network.
 */
class ConfigRefreshOtherSiteTest {

    private class Fixture(tempDir: Path) {
        val cache = RawConfigCache(TestAppDirectories(tempDir))
        val repository = JsonConfigurationRepository(cache)
        val configApi = FakeConfigApi()
        val checkinApi = FakeDeviceCheckinApi()
        private val settings = FakeAppSettingsRepository().apply {
            write(AppSettings(siteToken = "token-B", installationId = "install-1"))
        }
        val useCase = RefreshConfigurationUseCase(
            settingsRepository = settings,
            configApi = configApi,
            configurationRepository = repository,
            deviceCheckin = DeviceCheckinUseCase(
                settingsRepository = settings,
                installationIdProvider = InstallationIdProvider(settings),
                eligibleUploadsQuery = EligibleUploadsQuery(FakeSessionRepository(), FakeUploadStatusRepository()),
                api = checkinApi,
                appVersion = "1.4.0",
                dispatchers = ImmediateCoroutineDispatchers(),
            ),
        )

        /** Site A's config as the previous session left it: cached and active. */
        fun startOnSiteA() {
            cache.write(ConfigFixtures.fullProtocol)
            repository.loadCached()
        }
    }

    private val siteBConfig get() = ConfigFixtures.questionnaireOnly

    @Test
    fun `F-041 a 409 after the fetch keeps the previous config and its cache file`(@TempDir tempDir: Path) = runTest {
        val f = Fixture(tempDir)
        f.startOnSiteA()
        val before = f.repository.activeConfig.value
        f.configApi.enqueueSuccess(siteBConfig)
        f.checkinApi.nextResult = DeviceCheckinResult.RegisteredToAnotherSite

        val result = f.useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.RegisteredToAnotherSite), result)
        assertEquals(ConfigFixtures.fullProtocol, f.cache.read(), "cache file must stay site A's")
        assertEquals(before, f.repository.activeConfig.value)
        assertEquals(ConfigError.RegisteredToAnotherSite, f.useCase.lastError.value)
        assertEquals("settings.refresh.otherSite", ConfigError.RegisteredToAnotherSite.messageKey())
    }

    @Test
    fun `F-041 a network failure on the check-in still applies the fetched config`(@TempDir tempDir: Path) = runTest {
        val f = Fixture(tempDir)
        f.startOnSiteA()
        f.configApi.enqueueSuccess(siteBConfig)
        f.checkinApi.nextResult = DeviceCheckinResult.Failed

        val result = f.useCase.refresh()

        assertInstanceOf(RefreshConfigurationUseCase.Result.Success::class.java, result)
        assertEquals(siteBConfig, f.cache.read())
        assertNotEquals(ConfigFixtures.fullProtocol, f.cache.read())
        assertNull(f.useCase.lastError.value)
    }

    @Test
    fun `F-041 a recorded check-in applies the fetched config`(@TempDir tempDir: Path) = runTest {
        val f = Fixture(tempDir)
        f.startOnSiteA()
        f.configApi.enqueueSuccess(siteBConfig)
        f.checkinApi.nextResult = DeviceCheckinResult.Recorded

        val result = f.useCase.refresh()

        assertInstanceOf(RefreshConfigurationUseCase.Result.Success::class.java, result)
        assertEquals(siteBConfig, f.cache.read())
        assertEquals(1, f.checkinApi.calls.size)
    }

    @Test
    fun `F-041 a 409 with no prior config leaves nothing active and names the other site as the reason`(
        @TempDir tempDir: Path,
    ) = runTest {
        val f = Fixture(tempDir)
        f.repository.loadCached()
        f.configApi.enqueueSuccess(siteBConfig)
        f.checkinApi.nextResult = DeviceCheckinResult.RegisteredToAnotherSite

        val result = f.useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.RegisteredToAnotherSite), result)
        assertNull(f.repository.activeConfig.value)
        assertNull(f.cache.read(), "no cache file may be created")
        assertEquals(ConfigError.RegisteredToAnotherSite, f.useCase.lastError.value)
    }

    @Test
    fun `F-041 the startup refresh follows the same rule`(@TempDir tempDir: Path) = runTest {
        val f = Fixture(tempDir)
        f.startOnSiteA() // Main.kt: loadCached() ...
        f.configApi.enqueueSuccess(siteBConfig)
        f.checkinApi.nextResult = DeviceCheckinResult.RegisteredToAnotherSite

        f.useCase.refresh() // ... then the startup refresh

        assertEquals(ConfigFixtures.fullProtocol, f.cache.read())
        assertEquals(2, f.repository.activeConfig.value?.protocols?.size)
    }

    @Test
    fun `F-041 an invalid config never reaches the check-in`(@TempDir tempDir: Path) = runTest {
        val f = Fixture(tempDir)
        f.startOnSiteA()
        f.configApi.enqueueSuccess("""{"schemaVersion":99}""")

        f.useCase.refresh()

        assertEquals(0, f.checkinApi.calls.size)
        assertEquals(ConfigFixtures.fullProtocol, f.cache.read())
    }
}
