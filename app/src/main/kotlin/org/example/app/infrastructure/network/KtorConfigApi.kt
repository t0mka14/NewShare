package org.example.app.infrastructure.network

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.example.app.domain.config.ConfigApi
import org.example.app.domain.config.ConfigFetchResult
import org.example.app.infrastructure.logging.LogPolicy

private val logger = KotlinLogging.logger {}

/**
 * Ktor-backed [ConfigApi] (§6.1): `GET {baseUrl}/site-config/{siteToken}` on the web backend
 * (config alignment row 1, [WEB_SERVER_BASE_URL]).
 *
 * The endpoint is a **single configuration point**: [baseUrl] + [configPath] together form the
 * request URL; changing the server means changing the constructor defaults (or the values the
 * composition root passes in) — no call-site edits.
 *
 * Only the status code is interpreted; the web's error bodies (`{"error": "..."}`) are not
 * parsed: 404 unknown token, 403 site deactivated, 429 too many failed lookups.
 *
 * [engine] is constructor-injected so tests can substitute Ktor's `MockEngine`; production
 * wiring (left to the composition root / `AppContainer`) can pass the default CIO engine or
 * omit the parameter entirely. No custom `TrustManager`/`HostnameVerifier` is installed
 * anywhere in this class, so HTTPS certificate validation uses the JVM's default trust
 * store (§6.1 pt 7) for both the default and any injected engine.
 *
 * **§11:** the site token is a bearer credential and must never be logged. This class
 * does not log the request URL, the token, or raw exception messages (which can
 * embed the URL, e.g. `ConnectException: Connection refused: /site-config/<token>`) — see
 * [LogPolicy.safeDescribe].
 */
class KtorConfigApi(
    engine: HttpClientEngine = CIO.create(),
    private val baseUrl: String = WEB_SERVER_BASE_URL,
    private val configPath: (siteToken: String) -> String = { siteToken -> "/site-config/${siteToken.encodeURLPathPart()}" },
) : ConfigApi {

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    override suspend fun fetchConfig(siteToken: String): ConfigFetchResult {
        val url = buildUrl(siteToken)
        val response: HttpResponse
        try {
            response = client.get(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "config fetch failed, transport error: ${LogPolicy.safeDescribe(e)}" }
            return ConfigFetchResult.NetworkUnavailable(LogPolicy.safeDescribe(e))
        }

        return when {
            response.status.isSuccess() -> ConfigFetchResult.Success(response.bodyAsText())
            response.status == HttpStatusCode.NotFound -> {
                logger.info { "config fetch: site token not recognized (HTTP 404)" }
                ConfigFetchResult.SiteTokenUnknown
            }
            response.status == HttpStatusCode.Forbidden -> {
                logger.info { "config fetch: site deactivated (HTTP 403)" }
                ConfigFetchResult.SiteDeactivated
            }
            response.status == HttpStatusCode.TooManyRequests -> {
                logger.warn { "config fetch: rate limited by the server (HTTP 429)" }
                ConfigFetchResult.RateLimited
            }
            else -> {
                logger.warn { "config fetch failed: HTTP ${response.status.value}" }
                ConfigFetchResult.ServerError(response.status.value)
            }
        }
    }

    /** Releases the underlying Ktor engine/connection pool. Safe to call multiple times. */
    fun close() = client.close()

    private fun buildUrl(siteToken: String): String =
        baseUrl.trimEnd('/') + configPath(siteToken)
}
