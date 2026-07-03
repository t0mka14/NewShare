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
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.example.app.domain.config.ConfigApi
import org.example.app.domain.config.ConfigFetchResult
import org.example.app.infrastructure.logging.LogPolicy

private val logger = KotlinLogging.logger {}

/**
 * Ktor-backed [ConfigApi] (§6.1).
 *
 * The endpoint is a **single configuration point**: [baseUrl] + [configPath] together
 * form the request URL. The server contract is pending (§13); today this defaults to the
 * placeholder `GET /api/config/{installationId}`. When the real contract lands, only the
 * constructor defaults (or the values the composition root passes in) need to change —
 * no call-site edits.
 *
 * [engine] is constructor-injected so tests can substitute Ktor's `MockEngine`; production
 * wiring (left to the composition root / `AppContainer`) can pass the default CIO engine or
 * omit the parameter entirely. No custom `TrustManager`/`HostnameVerifier` is installed
 * anywhere in this class, so HTTPS certificate validation uses the JVM's default trust
 * store (§6.1 pt 7) for both the default and any injected engine.
 *
 * **§11:** the installation ID is a bearer credential and must never be logged. This class
 * does not log the request URL, the installation ID, or raw exception messages (which can
 * embed the URL, e.g. `ConnectException: Connection refused: /api/config/<id>`) — see
 * [LogPolicy.safeDescribe].
 */
class KtorConfigApi(
    engine: HttpClientEngine = CIO.create(),
    private val baseUrl: String = "https://localhost/api",
    private val configPath: (installationId: String) -> String = { installationId -> "/config/$installationId" },
) : ConfigApi {

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    override suspend fun fetchConfig(installationId: String): ConfigFetchResult {
        val url = buildUrl(installationId)
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
            response.status.isInvalidInstallationId() -> {
                logger.info { "config fetch rejected installation id: HTTP ${response.status.value}" }
                ConfigFetchResult.InvalidInstallationId
            }
            else -> {
                logger.warn { "config fetch failed: HTTP ${response.status.value}" }
                ConfigFetchResult.ServerError(response.status.value)
            }
        }
    }

    /** Releases the underlying Ktor engine/connection pool. Safe to call multiple times. */
    fun close() = client.close()

    private fun buildUrl(installationId: String): String =
        baseUrl.trimEnd('/') + configPath(installationId)

    private fun HttpStatusCode.isInvalidInstallationId(): Boolean =
        this == HttpStatusCode.Unauthorized || this == HttpStatusCode.Forbidden || this == HttpStatusCode.NotFound
}
