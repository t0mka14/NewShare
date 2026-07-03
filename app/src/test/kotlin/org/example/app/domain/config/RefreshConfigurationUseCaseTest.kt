package org.example.app.domain.config

import kotlinx.coroutines.test.runTest
import org.example.app.domain.settings.AppSettings
import org.example.app.fakes.FakeAppSettingsRepository
import org.example.app.fakes.FakeConfigApi
import org.example.app.fakes.FakeConfigurationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RefreshConfigurationUseCaseTest {

    private val sampleConfig = RemoteConfig(schemaVersion = 1, configVersion = "v1", defaultLanguage = "en")

    private fun useCase(
        installationId: String? = "install-42",
        configApi: FakeConfigApi = FakeConfigApi(),
        configurationRepository: FakeConfigurationRepository = FakeConfigurationRepository(),
    ): Triple<RefreshConfigurationUseCase, FakeConfigApi, FakeConfigurationRepository> {
        val settings = FakeAppSettingsRepository()
        if (installationId != null) settings.write(AppSettings(installationId = installationId))
        return Triple(
            RefreshConfigurationUseCase(settings, configApi, configurationRepository),
            configApi,
            configurationRepository,
        )
    }

    @Test
    fun `no installation id yields InstallationIdMissing without calling the api`() = runTest {
        val (useCase, api, _) = useCase(installationId = null)

        val result = useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.InstallationIdMissing), result)
        assertEquals(0, api.requestedInstallationIds.size)
    }

    @Test
    fun `blank installation id yields InstallationIdMissing`() = runTest {
        val (useCase, _, _) = useCase(installationId = "   ")

        val result = useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.InstallationIdMissing), result)
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
    fun `uses the installation id from settings, never a literal`() = runTest {
        val api = FakeConfigApi()
        api.enqueueNetworkUnavailable()
        val (useCase, _, _) = useCase(installationId = "the-real-id", configApi = api)

        useCase.refresh()

        assertEquals(listOf("the-real-id"), api.requestedInstallationIds)
    }

    @Test
    fun `invalid installation id maps to InstallationIdRejected`() = runTest {
        val api = FakeConfigApi()
        api.enqueueInvalidInstallationId()
        val (useCase, _, _) = useCase(configApi = api)

        val result = useCase.refresh()

        assertEquals(RefreshConfigurationUseCase.Result.Failed(ConfigError.InstallationIdRejected), result)
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
    fun `installation id never appears in the returned error`() = runTest {
        val api = FakeConfigApi()
        api.enqueueNetworkUnavailable(detail = "java.net.ConnectException")
        val (useCase, _, _) = useCase(installationId = "SUPER-SECRET-ID", configApi = api)

        val result = useCase.refresh()

        assertNull((result.toString()).let { if (it.contains("SUPER-SECRET-ID")) it else null })
    }
}
