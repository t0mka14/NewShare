package org.example.app.domain.config

/**
 * Fetches the configuration JSON for a site (§6.1): `GET /site-config/{siteToken}` on the web
 * backend (config alignment row 1). HTTPS with certificate validation in production.
 *
 * The site token is the bearer credential: it must never appear in logs (§11).
 */
interface ConfigApi {
    suspend fun fetchConfig(siteToken: String): ConfigFetchResult
}

sealed interface ConfigFetchResult {
    /** Raw response body; parsing/validation is the config loader's job. */
    data class Success(val json: String) : ConfigFetchResult

    /** The server knows no site with this token (web: 404 `Invalid token`). */
    data object SiteTokenUnknown : ConfigFetchResult

    /** The token's site exists but has been deactivated (web: 403). */
    data object SiteDeactivated : ConfigFetchResult

    /** Too many failed lookups from this address; the web throttles them (429). */
    data object RateLimited : ConfigFetchResult

    /** Server unreachable / transport failure → offline fallback to cache (§6.1). */
    data class NetworkUnavailable(val detail: String) : ConfigFetchResult

    /** Any other non-success HTTP response. */
    data class ServerError(val httpStatus: Int) : ConfigFetchResult
}
