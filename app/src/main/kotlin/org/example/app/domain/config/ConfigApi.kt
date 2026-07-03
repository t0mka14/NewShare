package org.example.app.domain.config

/**
 * Fetches the configuration JSON for an installation ID (§6.1). The endpoint
 * path is a single configuration point in the implementation (placeholder
 * `GET /api/config/{installationId}`, §13). HTTPS with certificate validation.
 *
 * The installation ID acts as a bearer credential: it must never appear in
 * logs (§11).
 */
interface ConfigApi {
    suspend fun fetchConfig(installationId: String): ConfigFetchResult
}

sealed interface ConfigFetchResult {
    /** Raw response body; parsing/validation is the config loader's job. */
    data class Success(val json: String) : ConfigFetchResult

    /** Server answered but rejected the installation ID (unknown/disabled, §6.1). */
    data object InvalidInstallationId : ConfigFetchResult

    /** Server unreachable / transport failure → offline fallback to cache (§6.1). */
    data class NetworkUnavailable(val detail: String) : ConfigFetchResult

    /** Any other non-success HTTP response. */
    data class ServerError(val httpStatus: Int) : ConfigFetchResult
}
