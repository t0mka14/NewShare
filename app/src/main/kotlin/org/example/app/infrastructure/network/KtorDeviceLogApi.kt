package org.example.app.infrastructure.network

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.example.app.domain.logs.DeviceLogApi
import org.example.app.domain.logs.DeviceLogResult
import org.example.app.infrastructure.logging.LogPolicy
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import kotlin.math.abs

private val logger = KotlinLogging.logger {}

/**
 * Ktor-backed [DeviceLogApi]: `POST {baseUrl}/device-logs`, `multipart/form-data`, the text
 * parts `siteToken`, `installationId`, `appVersion`, `utcOffset`, `fileName`, `sha256` and then
 * the gzip `file` last (the server rejects a text part after the file). Contract: web repo
 * `docs/device_logs/01_spec.md` §2. Timeouts and engine injection follow [KtorUploadApi].
 *
 * Status mapping: 2xx (a duplicate resend too) is [DeviceLogResult.Sent]; 400 and 413 are
 * [DeviceLogResult.Unsendable]; any other HTTP answer (404, 409, 429, 5xx) is
 * [DeviceLogResult.RetryLater]; a transport exception or an unreadable file is
 * [DeviceLogResult.FileFailed] (the caller skips that file and tries the next).
 *
 * **§11:** the token and the installation ID are never logged, nor is the file name or path
 * (the log lines carry only the HTTP status); exceptions go through [LogPolicy.safeDescribe].
 */
class KtorDeviceLogApi(
    engine: HttpClientEngine = CIO.create(),
    private val baseUrl: String = WEB_SERVER_BASE_URL,
    private val path: String = "/device-logs",
) : DeviceLogApi {

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = KtorUploadApi.SOCKET_TIMEOUT_MS
            connectTimeoutMillis = 10_000
        }
    }

    override suspend fun send(
        file: Path,
        fileName: String,
        sha256: String,
        siteToken: String,
        installationId: String,
        appVersion: String,
        utcOffset: ZoneOffset,
    ): DeviceLogResult {
        val response: HttpResponse
        try {
            val size = Files.size(file)
            response = client.submitFormWithBinaryData(
                url = baseUrl.trimEnd('/') + path,
                formData = formData {
                    append("siteToken", siteToken)
                    append("installationId", installationId)
                    append("appVersion", appVersion)
                    append("utcOffset", formatOffset(utcOffset))
                    append("fileName", fileName)
                    append("sha256", sha256)
                    appendInput(
                        key = "file",
                        headers = Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            append(HttpHeaders.ContentType, "application/gzip")
                        },
                        size = size,
                    ) {
                        FileInputStream(file.toFile()).asSource().buffered()
                    }
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "device log send failed, transport error: ${LogPolicy.safeDescribe(e)}" }
            return DeviceLogResult.FileFailed
        }

        val status = response.status.value
        return when {
            response.status.isSuccess() -> DeviceLogResult.Sent
            status == 400 || status == 413 -> {
                logger.warn { "device log send: server will not take the file (HTTP $status)" }
                DeviceLogResult.Unsendable
            }
            else -> {
                logger.warn { "device log send: retry later (HTTP $status)" }
                DeviceLogResult.RetryLater
            }
        }
    }

    /** Releases the underlying Ktor engine/connection pool. Safe to call multiple times. */
    fun close() = client.close()

    internal companion object {
        /** `+01:00`, `-05:30`, and `+00:00` for UTC (the server's pattern has no `Z`). */
        fun formatOffset(offset: ZoneOffset): String {
            val total = offset.totalSeconds
            val sign = if (total < 0) '-' else '+'
            val minutes = abs(total) / 60
            return "%c%02d:%02d".format(sign, minutes / 60, minutes % 60)
        }
    }
}
