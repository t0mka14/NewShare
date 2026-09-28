package org.example.app.domain.config

import org.example.app.domain.settings.AppSettingsRepository

/**
 * §5.5 `RefreshConfigurationUseCase`: site token (from [AppSettingsRepository]) →
 * [ConfigApi.fetchConfig] → on success [ConfigurationRepository.applyFetched]; failures map to
 * the §11 [ConfigError] taxonomy as a sealed [Result] the UI can localize without ever seeing
 * an exception or the token (§6.1 pt 7, §11).
 *
 * Offline fallback (§6.1 pt 4) is not a distinct error case here: when the transport fails and
 * a cached config is already active, that is the *designed* behavior, not a failure to
 * localize as an error dialog — [Result.OfflineUsingCache] carries the still-active config so
 * the caller can proceed silently (or show a subtle "using cached configuration" hint) instead
 * of a blocking error. Only "network unreachable with genuinely no cache" is a [ConfigError].
 */
class RefreshConfigurationUseCase(
    private val settingsRepository: AppSettingsRepository,
    private val configApi: ConfigApi,
    private val configurationRepository: ConfigurationRepository,
) {
    sealed interface Result {
        data class Success(val config: RemoteConfig) : Result
        data class OfflineUsingCache(val config: RemoteConfig) : Result
        data class Failed(val error: ConfigError) : Result
    }

    suspend fun refresh(): Result {
        val siteToken = settingsRepository.read()?.siteToken
        if (siteToken.isNullOrBlank()) {
            return Result.Failed(ConfigError.SiteTokenMissing)
        }

        return when (val fetch = configApi.fetchConfig(siteToken)) {
            is ConfigFetchResult.Success -> applyFetched(fetch.json)

            ConfigFetchResult.SiteTokenUnknown -> Result.Failed(ConfigError.SiteTokenRejected)
            ConfigFetchResult.SiteDeactivated -> Result.Failed(ConfigError.SiteDeactivated)
            ConfigFetchResult.RateLimited -> Result.Failed(ConfigError.RateLimited)

            is ConfigFetchResult.NetworkUnavailable -> {
                val cached = configurationRepository.activeConfig.value
                if (cached != null) {
                    Result.OfflineUsingCache(cached)
                } else {
                    Result.Failed(ConfigError.NetworkUnavailableNoCache)
                }
            }

            is ConfigFetchResult.ServerError -> Result.Failed(ConfigError.ServerError(fetch.httpStatus))
        }
    }

    private fun applyFetched(json: String): Result =
        when (val applied = configurationRepository.applyFetched(json)) {
            is ConfigApplyResult.Applied -> Result.Success(applied.config)

            is ConfigApplyResult.Malformed -> Result.Failed(ConfigError.Malformed(applied.detail))

            is ConfigApplyResult.Rejected -> {
                val schemaError = applied.errors
                    .filterIsInstance<ConfigValidationError.UnsupportedSchemaVersion>()
                    .firstOrNull()
                if (schemaError != null) {
                    Result.Failed(
                        ConfigError.SchemaUnsupported(schemaError.schemaVersion, schemaError.supportedRange),
                    )
                } else {
                    Result.Failed(ConfigError.ValidationFailed(applied.errors))
                }
            }
        }
}
