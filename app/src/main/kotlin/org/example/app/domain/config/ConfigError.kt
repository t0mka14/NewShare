package org.example.app.domain.config

/**
 * §11 `ConfigError` taxonomy, produced by [RefreshConfigurationUseCase] for the UI to
 * localize (built-in keys under `error.config.*`, §7). Never carries the installation ID or a
 * raw exception message (§11, §6.1 pt 7) — [ConfigFetchResult.NetworkUnavailable.detail] is
 * already sanitized to an exception class name by `ConfigApi` implementations before it
 * reaches here.
 */
sealed interface ConfigError {
    /** No installation ID has been entered in Settings yet (first run). */
    data object InstallationIdMissing : ConfigError

    /** Server rejected the installation ID as unknown/disabled (§6.1). */
    data object InstallationIdRejected : ConfigError

    /** Transport failure and no cached config to fall back to (§6.1 pt 4). */
    data object NetworkUnavailableNoCache : ConfigError

    /** Server responded outside [ConfigValidator.SUPPORTED_SCHEMA_VERSIONS] (§6.1 pt 6). */
    data class SchemaUnsupported(val schemaVersion: Int, val supportedRange: IntRange) : ConfigError

    /** Response parsed but failed [ConfigValidator] (bad discriminator, VOCAL before CALIBRATION, …). */
    data class ValidationFailed(val errors: List<ConfigValidationError>) : ConfigError

    /** Response body did not parse/decode as configuration JSON at all. */
    data class Malformed(val detail: String) : ConfigError

    /** Any other non-success HTTP response. */
    data class ServerError(val httpStatus: Int) : ConfigError
}
