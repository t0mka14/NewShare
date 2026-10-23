package org.example.app.infrastructure.network

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Headers
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.example.app.domain.upload.RejectionReason
import org.example.app.domain.upload.UploadApi
import org.example.app.domain.upload.UploadResult
import org.example.app.infrastructure.logging.LogPolicy
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Ktor-backed [UploadApi] (§8.9).
 *
 * The endpoint is a **single configuration point**: [baseUrl] + [uploadPath] form the request
 * URL, mirroring [KtorConfigApi]. It defaults to `POST /api/upload` on the web backend
 * (`docs/dev/server-upload-integration.md`, §13 decision 62).
 *
 * Request shape (§8.9, normative): a `multipart/form-data` POST — the `installationId`,
 * `sessionId`, `zipSha256`, `siteToken` (omitted when none is set) and `appVersion` text parts,
 * then the ZIP file (streamed from disk, never fully materialized in memory). The server
 * rejects a text part after the ZIP, so the order matters. Nothing else is ever sent.
 *
 * Status mapping (decision 62): 404 is the server's quarantine for a device without a site
 * ([RejectionReason.DeviceNotRegistered], retryable once an administrator assigns it), 409 an
 * existing `sessionId` with different bytes ([UploadResult.Conflict]), 401/403/413 other
 * rejections; anything else non-2xx (400 checksum mismatch, 429, 503, 507, 5xx) is a
 * [UploadResult.ServerError].
 *
 * Timeouts: no cap on the whole request, since a session of hundreds of MB on a slow line takes
 * minutes; instead a stalled connection fails after [SOCKET_TIMEOUT_MS] without progress.
 *
 * [engine] is constructor-injected so tests can substitute Ktor's `MockEngine`; no custom
 * `TrustManager`/`HostnameVerifier` is installed, so HTTPS certificate validation uses the
 * JVM's default trust store, same as [KtorConfigApi].
 *
 * Progress: [onUpload] is Ktor's built-in upload-progress hook (backed by the `BodyProgress`
 * plugin the client installs automatically) — it fires as bytes are actually written to the
 * transport, independent of engine, so it drives [UploadApi.uploadSession]'s `onProgress`
 * faithfully for both the real CIO engine and `MockEngine` in tests. The fraction is clamped to
 * `0f..1f` and reported as `0f` whenever the content length is unknown.
 *
 * **§11 discipline (same as [KtorConfigApi]):** the installation ID and the site token must
 * never be logged.
 * Additionally here, the ZIP [Path] itself must never be logged — the session folder name (and
 * therefore the path) embeds the participant's `patientCode` (§8.2), which is participant data.
 * Log lines below reference only `sessionId` and HTTP status codes, and reduce every exception
 * through [LogPolicy.safeDescribe] rather than logging `Throwable.message` (which can embed the
 * request URL/body). The `io.ktor` logger level ceiling in `logback.xml` (§11) covers this
 * class the same way it covers [KtorConfigApi].
 */
class KtorUploadApi(
    engine: HttpClientEngine = CIO.create(),
    private val baseUrl: String = WEB_SERVER_BASE_URL,
    private val uploadPath: () -> String = { "/upload" },
) : UploadApi {

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
            connectTimeoutMillis = 10_000
        }
    }

    override suspend fun uploadSession(
        zip: Path,
        installationId: String,
        sessionId: String,
        zipSha256: String,
        siteToken: String?,
        appVersion: String,
        onProgress: (Float) -> Unit,
    ): UploadResult {
        val response: HttpResponse
        try {
            val zipSize = Files.size(zip)
            response = client.submitFormWithBinaryData(
                url = buildUrl(),
                formData = formData {
                    append("installationId", installationId)
                    append("sessionId", sessionId)
                    append("zipSha256", zipSha256)
                    if (siteToken != null) append("siteToken", siteToken)
                    append("appVersion", appVersion)
                    appendInput(
                        key = "zip",
                        headers = Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"${zip.fileName}\"")
                            append(HttpHeaders.ContentType, "application/zip")
                        },
                        size = zipSize,
                    ) {
                        FileInputStream(zip.toFile()).asSource().buffered()
                    }
                },
            ) {
                onUpload { bytesSentTotal, contentLength ->
                    val fraction = if (contentLength != null && contentLength > 0) {
                        (bytesSentTotal.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    onProgress(fraction)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "upload failed, transport error for session=$sessionId: ${LogPolicy.safeDescribe(e)}" }
            return UploadResult.NetworkFailure
        }

        val rejection = rejectionReason(response.status)
        return when {
            response.status.isSuccess() -> UploadResult.Success(parseServerResponse(response))
            rejection != null -> {
                logger.info { "upload rejected for session=$sessionId: HTTP ${response.status.value}" }
                UploadResult.Rejected(rejection)
            }
            response.status == HttpStatusCode.Conflict -> {
                logger.warn { "upload conflict for session=$sessionId: already on the server with different content (HTTP 409)" }
                UploadResult.Conflict
            }
            else -> {
                logger.warn { "upload failed for session=$sessionId: HTTP ${response.status.value}" }
                UploadResult.ServerError(response.status.value)
            }
        }
    }

    /** Releases the underlying Ktor engine/connection pool. Safe to call multiple times. */
    fun close() = client.close()

    private suspend fun parseServerResponse(response: HttpResponse): JsonElement? {
        val body = response.bodyAsText()
        if (body.isBlank()) return null
        return runCatching { Json.parseToJsonElement(body) }.getOrNull()
    }

    private fun buildUrl(): String = baseUrl.trimEnd('/') + uploadPath()

    private fun rejectionReason(status: HttpStatusCode): RejectionReason? = when (status) {
        HttpStatusCode.NotFound -> RejectionReason.DeviceNotRegistered
        HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> RejectionReason.NotAuthorized
        HttpStatusCode.PayloadTooLarge -> RejectionReason.TooLarge
        else -> null
    }

    companion object {
        /** Idle limit: fails a connection that moves no bytes for this long (the server and its
         * proxy allow 10 minutes). */
        const val SOCKET_TIMEOUT_MS: Long = 120_000
    }
}
