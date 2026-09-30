package org.example.app.domain.config

/**
 * §11 `ConfigError` taxonomy, produced by [RefreshConfigurationUseCase] for the UI to
 * localize (built-in keys under `error.config.*`, §7). Never carries the site token or a
 * raw exception message (§11, §6.1 pt 7) — [ConfigFetchResult.NetworkUnavailable.detail] is
 * already sanitized to an exception class name by `ConfigApi` implementations before it
 * reaches here.
 */
sealed interface ConfigError {
    /** No site token has been entered in Settings yet (first run). */
    data object SiteTokenMissing : ConfigError

    /** The server knows no site with this token. */
    data object SiteTokenRejected : ConfigError

    /** The token's site has been deactivated on the server. */
    data object SiteDeactivated : ConfigError

    /** The server is throttling this address after too many failed lookups. */
    data object RateLimited : ConfigError

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

/**
 * The localization key (§7) that explains this error — shown by Settings after a refresh and
 * by the blocking "configuration required" screen. Here, not in `ui/`, so components can use it
 * without depending on the UI layer (§5.2).
 */
fun ConfigError.messageKey(): String = when (this) {
    ConfigError.SiteTokenMissing -> "error.config.siteTokenMissing"
    ConfigError.SiteTokenRejected -> "error.config.siteTokenRejected"
    ConfigError.SiteDeactivated -> "error.config.siteDeactivated"
    ConfigError.RateLimited -> "error.config.rateLimited"
    ConfigError.NetworkUnavailableNoCache -> "error.config.networkUnavailable"
    is ConfigError.SchemaUnsupported -> "error.config.schemaUnsupported"
    is ConfigError.ValidationFailed -> "error.config.validationFailed"
    is ConfigError.Malformed -> "error.config.malformed"
    is ConfigError.ServerError -> "settings.refresh.failed"
}
