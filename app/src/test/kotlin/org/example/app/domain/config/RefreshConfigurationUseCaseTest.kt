package org.example.app.domain.config

import kotlinx.coroutines.test.runTest
import org.example.app.domain.settings.AppSettings
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeConfigApi
import org.example.app.fakes.FakeConfigurationRepository
import org.example.app.fakes.FakeExampleAudioCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RefreshConfigurationUseCaseTest {

    private val sampleConfig = RemoteConfig(schemaVersion = 1, configVersion = "v1", defaultLanguage = "en")

    private fun useCase(
        siteToken: String? = "token-42",
        configApi: FakeConfigApi = FakeConfigApi(),
        configurationRepository: FakeConfigurationRepository = FakeConfigurationRepository(),
    ): Triple<RefreshConfigurationUseCase, FakeConfigApi, FakeConfigurationRepository> {
        val settings = FakeAppSettingsRepository()
        if (siteToken != null) settings.write(AppSettings(siteToken = siteToken))
        return Triple(
            RefreshConfigurationUseCase(settings, configApi, configurationRepository),
            configApi,
            configurationRepository,
        )
    }

    @Test
    fun `no site token yields SiteTokenMissing without calling the api`() = runTest {
        val (useCase, api, _) = useCase(siteToken = null)

        val result = useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.SiteTokenMissing), result)
        assertEquals(0, api.requestedSiteTokens.size)
    }

    @Test
    fun `blank site token yields SiteTokenMissing`() = runTest {
        val (useCase, _, _) = useCase(siteToken = "   ")

        val result = useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.SiteTokenMissing), result)
    }

    @Test
    fun `successful fetch applies and returns Success`() = runTest {
        val repo = FakeConfigurationRepository()
        repo.enqueueApplyResult(ConfigApplyResult.Applied(sampleConfig))
        val api = FakeConfigApi()
        api.enqueueSuccess("""{"schemaVersion":1}""")
        val (useCase, _, _) = useCase(configApi = api, configurationRepository = repo)

        val result = useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Success(sampleConfig), result)
    }

    @Test
    fun `uses the site token from settings, never a literal`() = runTest {
        val api = FakeConfigApi()
        api.enqueueNetworkUnavailable()
        val (useCase, _, _) = useCase(siteToken = "the-real-token", configApi = api)

        useCase.refresh()

        assertEquals(listOf("the-real-token"), api.requestedSiteTokens)
    }

    @Test
    fun `unknown site token maps to SiteTokenRejected`() = runTest {
        val api = FakeConfigApi()
        api.enqueueSiteTokenUnknown()
        val (useCase, _, _) = useCase(configApi = api)

        val result = useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.SiteTokenRejected), result)
    }

    @Test
    fun `deactivated site maps to SiteDeactivated`() = runTest {
        val api = FakeConfigApi()
        api.enqueueSiteDeactivated()
        val (useCase, _, _) = useCase(configApi = api)

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.SiteDeactivated), useCase.refresh())
    }

    @Test
    fun `rate limiting maps to RateLimited`() = runTest {
        val api = FakeConfigApi()
        api.enqueueRateLimited()
        val (useCase, _, _) = useCase(configApi = api)

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.RateLimited), useCase.refresh())
    }

    @Test
    fun `an installation id alone is not enough to fetch`() = runTest {
        val settings = FakeAppSettingsRepository().apply { write(AppSettings(installationId = "pc-1")) }
        val api = FakeConfigApi()
        val useCase = RefreshConfigurationUseCase(settings, api, FakeConfigurationRepository())

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.SiteTokenMissing), useCase.refresh())
        assertEquals(0, api.requestedSiteTokens.size)
    }

    @Test
    fun `network unavailable with no cache maps to NetworkUnavailableNoCache`() = runTest {
        val api = FakeConfigApi()
        api.enqueueNetworkUnavailable()
        val repo = FakeConfigurationRepository(initialConfig = null)
        val (useCase, _, _) = useCase(configApi = api, configurationRepository = repo)

        val result = useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.NetworkUnavailableNoCache), result)
    }

    @Test
    fun `network unavailable with a cached config falls back offline without an error`() = runTest {
        val api = FakeConfigApi()
        api.enqueueNetworkUnavailable()
        val repo = FakeConfigurationRepository(initialConfig = sampleConfig)
        val (useCase, _, _) = useCase(configApi = api, configurationRepository = repo)

        val result = useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.OfflineUsingCache(sampleConfig), result)
    }

    @Test
    fun `server error maps to ServerError with the http status`() = runTest {
        val api = FakeConfigApi()
        api.enqueueServerError(503)
        val (useCase, _, _) = useCase(configApi = api)

        val result = useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.ServerError(503)), result)
    }

    @Test
    fun `malformed fetched config maps to ConfigError Malformed`() = runTest {
        val repo = FakeConfigurationRepository()
        repo.enqueueApplyResult(ConfigApplyResult.Malformed("not json"))
        val api = FakeConfigApi()
        api.enqueueSuccess("not json")
        val (useCase, _, _) = useCase(configApi = api, configurationRepository = repo)

        val result = useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.Malformed("not json")), result)
    }

    @Test
    fun `rejected fetch with an unsupported schema version maps to SchemaUnsupported`() = runTest {
        val repo = FakeConfigurationRepository()
        val schemaError = ConfigValidationError.UnsupportedSchemaVersion(99, 1..1)
        repo.enqueueApplyResult(ConfigApplyResult.Rejected(listOf(schemaError)))
        val api = FakeConfigApi()
        api.enqueueSuccess("""{"schemaVersion":99}""")
        val (useCase, _, _) = useCase(configApi = api, configurationRepository = repo)

        val result = useCase.refresh()

        assertInstanceOf(RefreshConfigurationUseCase.Result.Failed::class.java, result)
        val error = (result as RefreshConfigurationUseCase.Result.Failed).error
        assertInstanceOf(ConfigError.SchemaUnsupported::class.java, error)
        assertEquals(99, (error as ConfigError.SchemaUnsupported).schemaVersion)
        assertEquals(1..1, error.supportedRange)
    }

    @Test
    fun `rejected fetch with other validation errors maps to ValidationFailed`() = runTest {
        val repo = FakeConfigurationRepository()
        val validationError = ConfigValidationError.MissingTaskIndexPlaceholder("Share")
        repo.enqueueApplyResult(ConfigApplyResult.Rejected(listOf(validationError)))
        val api = FakeConfigApi()
        api.enqueueSuccess("""{"schemaVersion":1}""")
        val (useCase, _, _) = useCase(configApi = api, configurationRepository = repo)

        val result = useCase.refresh()

        assertEquals(
            RefreshConfigurationUseCase.Result.Failed(ConfigError.ValidationFailed(listOf(validationError))),
            result,
        )
    }

    @Test
    fun `lastError tracks the latest refresh and clears on success`() = runTest {
        val repo = FakeConfigurationRepository()
        repo.enqueueApplyResult(ConfigApplyResult.Applied(sampleConfig))
        val api = FakeConfigApi()
        api.enqueueSiteTokenUnknown()
        api.enqueueSuccess("""{"schemaVersion":1}""")
        val (useCase, _, _) = useCase(configApi = api, configurationRepository = repo)
        assertNull(useCase.lastError.value)

        useCase.refresh()
        assertEquals(ConfigError.SiteTokenRejected, useCase.lastError.value)

        useCase.refresh()
        assertNull(useCase.lastError.value)
    }

    @Test
    fun `site token never appears in the returned error`() = runTest {
        val api = FakeConfigApi()
        api.enqueueNetworkUnavailable(detail = "java.net.ConnectException")
        val (useCase, _, _) = useCase(siteToken = "SUPER-SECRET-ID", configApi = api)

        val result = useCase.refresh()

        assertNull((result.toString()).let { if (it.contains("SUPER-SECRET-ID")) it else null })
    }

    @Test
    fun `usingCachedConfig is set by an offline fallback and cleared by the next success`() = runTest {
        val api = FakeConfigApi()
        val repo = FakeConfigurationRepository(initialConfig = sampleConfig)
        val (useCase, _, _) = useCase(configApi = api, configurationRepository = repo)

        api.enqueueNetworkUnavailable()
        useCase.refresh()
        assertEquals(true, useCase.usingCachedConfig.value)

        api.enqueueSuccess("""{"schemaVersion":1}""")
        repo.enqueueApplyResult(ConfigApplyResult.Applied(sampleConfig))
        useCase.refresh()
        assertEquals(false, useCase.usingCachedConfig.value)
    }

    private fun configWithExamples(vararg urls: String?) = sampleConfig.copy(
        protocols = listOf(
            Protocol(
                name = "P",
                recordingsFileName = "\${taskIndex}",
                tasks = urls.map { VocalTask(titleKey = "t", subtype = VocalSubtype.PHONATION, audioExamplePath = it) },
            ),
        ),
    )

    @Test
    fun `a successful refresh syncs exactly the config's example-audio URLs`() = runTest {
        val cache = FakeExampleAudioCache()
        val repo = FakeConfigurationRepository()
        val config = configWithExamples("https://a/x.wav", null, "https://a/x.wav", "https://a/y.wav")
        repo.enqueueApplyResult(ConfigApplyResult.Applied(config))
        val api = FakeConfigApi().apply { enqueueSuccess("{}") }
        val settings = FakeAppSettingsRepository().apply { write(AppSettings(siteToken = "t")) }

        RefreshConfigurationUseCase(settings, api, repo, cache).refresh()

        assertEquals(listOf(setOf("https://a/x.wav", "https://a/y.wav")), cache.syncCalls)
    }

    @Test
    fun `offline or failed refreshes leave the example-audio cache alone`() = runTest {
        val cache = FakeExampleAudioCache()
        val settings = FakeAppSettingsRepository().apply { write(AppSettings(siteToken = "t")) }
        val api = FakeConfigApi().apply { enqueueNetworkUnavailable(); enqueueSiteTokenUnknown() }
        val useCase = RefreshConfigurationUseCase(settings, api, FakeConfigurationRepository(initialConfig = sampleConfig), cache)

        useCase.refresh()
        useCase.refresh()

        assertTrue(cache.syncCalls.isEmpty())
    }

    @Test
    fun `a failing example-audio cache does not fail the refresh`() = runTest {
        val cache = FakeExampleAudioCache().apply { failSync = true }
        val repo = FakeConfigurationRepository().apply { enqueueApplyResult(ConfigApplyResult.Applied(sampleConfig)) }
        val api = FakeConfigApi().apply { enqueueSuccess("{}") }
        val settings = FakeAppSettingsRepository().apply { write(AppSettings(siteToken = "t")) }

        val result = RefreshConfigurationUseCase(settings, api, repo, cache).refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Success(sampleConfig), result)
    }
}
