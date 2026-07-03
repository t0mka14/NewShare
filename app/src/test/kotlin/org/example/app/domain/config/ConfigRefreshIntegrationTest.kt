package org.example.app.domain.config

import kotlinx.coroutines.test.runTest
import org.example.app.domain.settings.AppSettings
import org.example.app.fakes.ConfigFixtures
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeConfigApi
import org.example.app.fakes.TestAppDirectories
import org.example.app.infrastructure.config.JsonConfigurationRepository
import org.example.app.infrastructure.config.RawConfigCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * End-to-end fetch path (§10.2): [RefreshConfigurationUseCase] driving the real
 * [JsonConfigurationRepository]/[RawConfigCache] pair against a temp-dir filesystem, fed by
 * [FakeConfigApi] in place of a Ktor `MockEngine` — `KtorConfigApi` itself already has
 * dedicated `MockEngine` coverage (`KtorConfigApiTest`); this test instead proves the pieces
 * this task owns compose correctly: fetch -> validate -> atomic persist -> activate, and the
 * startup (cache -> active config) path on a fresh process using the same cache directory.
 */
class ConfigRefreshIntegrationTest {

    private fun settingsWithInstallationId(id: String = "install-1"): FakeAppSettingsRepository =
        FakeAppSettingsRepository().apply { write(AppSettings(installationId = id)) }

    @Test
    fun `fetch path persists atomically and activates the config`(@TempDir tempDir: Path) {
        val directories = TestAppDirectories(tempDir)
        val cache = RawConfigCache(directories)
        val repository = JsonConfigurationRepository(cache)
        val api = FakeConfigApi().apply { enqueueSuccess(ConfigFixtures.fullProtocol) }
        val useCase = RefreshConfigurationUseCase(settingsWithInstallationId(), api, repository)

        lateinit var result: RefreshConfigurationUseCase.Result
        runTest { result = useCase.refresh() }

        assertInstanceOf(RefreshConfigurationUseCase.Result.Success::class.java, result)
        assertEquals(ConfigFixtures.fullProtocol, cache.read(), "server response must be persisted verbatim")
        assertNotNull(repository.activeConfig.value)
        assertEquals(2, repository.activeConfig.value?.protocols?.size)
    }

    @Test
    fun `a later process startup reads what the fetch path persisted`(@TempDir tempDir: Path) {
        val directories = TestAppDirectories(tempDir)

        // "Process 1": fetch and cache.
        val firstRunRepository = JsonConfigurationRepository(RawConfigCache(directories))
        val api = FakeConfigApi().apply { enqueueSuccess(ConfigFixtures.fullProtocol) }
        runTest {
            RefreshConfigurationUseCase(settingsWithInstallationId(), api, firstRunRepository).refresh()
        }

        // "Process 2": fresh repository instance, same directories, no network call — startup path only.
        val secondRunRepository = JsonConfigurationRepository(RawConfigCache(directories))
        val loaded = secondRunRepository.loadCached()

        assertNotNull(loaded)
        assertEquals(2, loaded?.protocols?.size)
    }

    @Test
    fun `offline with a previously cached config keeps it active and reports OfflineUsingCache`(@TempDir tempDir: Path) {
        val directories = TestAppDirectories(tempDir)
        val cache = RawConfigCache(directories)
        cache.write(ConfigFixtures.fullProtocol)
        val repository = JsonConfigurationRepository(cache)
        repository.loadCached()

        val api = FakeConfigApi().apply { enqueueNetworkUnavailable() }
        val useCase = RefreshConfigurationUseCase(settingsWithInstallationId(), api, repository)

        lateinit var result: RefreshConfigurationUseCase.Result
        runTest { result = useCase.refresh() }

        assertInstanceOf(RefreshConfigurationUseCase.Result.OfflineUsingCache::class.java, result)
        assertNotNull(repository.activeConfig.value)
    }

    @Test
    fun `offline with no cache at all blocks with NetworkUnavailableNoCache`(@TempDir tempDir: Path) {
        val directories = TestAppDirectories(tempDir)
        val repository = JsonConfigurationRepository(RawConfigCache(directories))
        val api = FakeConfigApi().apply { enqueueNetworkUnavailable() }
        val useCase = RefreshConfigurationUseCase(settingsWithInstallationId(), api, repository)

        lateinit var result: RefreshConfigurationUseCase.Result
        runTest { result = useCase.refresh() }

        assertEquals(
            RefreshConfigurationUseCase.Result.Failed(ConfigError.NetworkUnavailableNoCache),
            result,
        )
    }
}
