package org.example.app.infrastructure.network

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.app.domain.upload.DeviceCheckin
import org.example.app.domain.upload.DeviceCheckinApi
import org.example.app.domain.upload.DeviceCheckinResult
import org.example.app.infrastructure.logging.LogPolicy

private val logger = KotlinLogging.logger {}

/**
 * Ktor-backed [DeviceCheckinApi] (§13 decision 62): `POST {baseUrl}/device-checkin` with a JSON
 * body on the web backend, next to [KtorConfigApi] and [KtorUploadApi]. Same timeouts as the
 * config fetch. Only the status code is interpreted: 200 recorded, 404 invalid token, 409 the
 * computer belongs to another site; anything else (400, 429, 5xx) is a plain failure.
 *
 * **§11:** the body carries the site token and the installation ID, so neither the body nor raw
 * exception messages are logged — see [LogPolicy.safeDescribe].
 */
class KtorDeviceCheckinApi(
    engine: HttpClientEngine = CIO.create(),
    private val baseUrl: String = WEB_SERVER_BASE_URL,
    private val checkinPath: String = "/device-checkin",
) : DeviceCheckinApi {

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
    }

    override suspend fun checkIn(checkin: DeviceCheckin): DeviceCheckinResult {
        val body = Json.encodeToString(
            CheckinBody.serializer(),
            CheckinBody(
                siteToken = checkin.siteToken,
                installationId = checkin.installationId,
                appVersion = checkin.appVersion,
                pending = checkin.pending,
                failed = checkin.failed,
            ),
        )
        val response: HttpResponse
        try {
            response = client.post(baseUrl.trimEnd('/') + checkinPath) {
                setBody(TextContent(body, ContentType.Application.Json))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "device check-in failed, transport error: ${LogPolicy.safeDescribe(e)}" }
            return DeviceCheckinResult.Failed
        }

        return when {
            response.status.isSuccess() -> DeviceCheckinResult.Recorded
            response.status == HttpStatusCode.NotFound -> {
                logger.info { "device check-in: site token not recognized (HTTP 404)" }
                DeviceCheckinResult.InvalidToken
            }
            response.status == HttpStatusCode.Conflict -> {
                logger.warn { "device check-in: this computer is registered to another site (HTTP 409)" }
                DeviceCheckinResult.RegisteredToAnotherSite
            }
            else -> {
                logger.warn { "device check-in failed: HTTP ${response.status.value}" }
                DeviceCheckinResult.Failed
            }
        }
    }

    /** Releases the underlying Ktor engine/connection pool. Safe to call multiple times. */
    fun close() = client.close()

    @Serializable
    private data class CheckinBody(
        val siteToken: String,
        val installationId: String,
        val appVersion: String,
        val pending: Int,
        val failed: Int,
    )
}
