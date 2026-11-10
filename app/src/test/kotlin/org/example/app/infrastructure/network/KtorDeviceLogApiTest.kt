package org.example.app.infrastructure.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.example.app.domain.logs.DeviceLogResult
import org.example.app.infrastructure.logging.captureLogs
import org.example.app.infrastructure.logging.fullText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset

/** AC 7: the multipart request and the status mapping of `POST /device-logs`. */
class KtorDeviceLogApiTest {

    private val siteToken = "SECRETsiteTOKEN000000"
    private val installationId = "install-ID-42"
    private val fileName = "app.2026-11-09.0.log.gz"
    private val sha256 = "ab".repeat(32)

    private fun logFile(dir: Path): Path =
        dir.resolve(fileName).also { Files.write(it, ByteArray(2048) { i -> i.toByte() }) }

    /** Reads the whole body the way a transport engine would; without an upload hook Ktor hands the
     * engine the multipart as a write-channel content. */
    private suspend fun drain(content: OutgoingContent): ByteArray = when (content) {
        is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readBytes()
        is OutgoingContent.WriteChannelContent -> coroutineScope {
            val channel = ByteChannel()
            launch {
                content.writeTo(channel)
                channel.close()
            }
            channel.readRemaining().readBytes()
        }
        else -> error("unexpected body ${content::class}")
    }

    private suspend fun KtorDeviceLogApi.sendIt(file: Path, offset: ZoneOffset = ZoneOffset.ofHours(1)) =
        send(file, fileName, sha256, siteToken, installationId, "1.4.0", offset)

    private fun apiAnswering(
        status: HttpStatusCode,
        onRequest: suspend (HttpRequestData, ByteArray) -> Unit = { _, _ -> },
    ) = KtorDeviceLogApi(
        engine = MockEngine { request ->
            onRequest(request, drain(request.body))
            respond(content = "", status = status)
        },
    )

    @Test
    fun `posts to the device-logs path with every field value and the file part last`(@TempDir dir: Path) = runTest {
        val file = logFile(dir)
        var method: HttpMethod? = null
        var url = ""
        var raw = ByteArray(0)
        val api = apiAnswering(HttpStatusCode.OK) { request, body ->
            method = request.method
            url = request.url.toString()
            raw = body
        }

        api.sendIt(file)

        assertEquals(HttpMethod.Post, method)
        assertEquals("$WEB_SERVER_BASE_URL/device-logs", url)
        val text = String(raw, StandardCharsets.ISO_8859_1)

        // Part names in body order.
        val names = Regex("""Content-Disposition: form-data; name=([A-Za-z0-9]+)""").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf("siteToken", "installationId", "appVersion", "utcOffset", "fileName", "sha256", "file"),
            names.map { it.trim('"') },
        )
        assertEquals("file", names.last().trim('"'))
        assertEquals(7, names.size)

        fun valueOf(name: String): String {
            val start = text.indexOf("name=$name")
            assertTrue(start >= 0, "part $name is missing")
            val bodyStart = text.indexOf("\r\n\r\n", start) + 4
            return text.substring(bodyStart, text.indexOf("\r\n", bodyStart))
        }
        assertEquals(siteToken, valueOf("siteToken"))
        assertEquals(installationId, valueOf("installationId"))
        assertEquals("1.4.0", valueOf("appVersion"))
        assertEquals("+01:00", valueOf("utcOffset"))
        assertEquals(fileName, valueOf("fileName"))
        assertEquals(sha256, valueOf("sha256"))

        val fileHeader = text.substring(text.indexOf("name=file"))
        assertTrue(fileHeader.contains("Content-Type: application/gzip"))
        assertTrue(raw.size > Files.size(file))
    }

    @Test
    fun `utc offsets are formatted with a sign and minutes, and Z becomes +00 00`() {
        assertEquals("+00:00", KtorDeviceLogApi.formatOffset(ZoneOffset.UTC))
        assertEquals("+01:00", KtorDeviceLogApi.formatOffset(ZoneOffset.ofHours(1)))
        assertEquals("-05:00", KtorDeviceLogApi.formatOffset(ZoneOffset.ofHours(-5)))
        assertEquals("+05:30", KtorDeviceLogApi.formatOffset(ZoneOffset.ofHoursMinutes(5, 30)))
        assertEquals("-09:30", KtorDeviceLogApi.formatOffset(ZoneOffset.ofHoursMinutes(-9, -30)))
    }

    @Test
    fun `UTC is sent as +00 00`(@TempDir dir: Path) = runTest {
        var raw = ByteArray(0)
        val api = apiAnswering(HttpStatusCode.OK) { _, body -> raw = body }

        api.sendIt(logFile(dir), ZoneOffset.UTC)

        val text = String(raw, StandardCharsets.ISO_8859_1)
        assertTrue(text.contains("\r\n\r\n+00:00\r\n"))
        assertFalse(text.contains("\r\n\r\nZ\r\n"))
    }

    @Test
    fun `200 maps to Sent`(@TempDir dir: Path) = runTest {
        assertEquals(DeviceLogResult.Sent, apiAnswering(HttpStatusCode.OK).sendIt(logFile(dir)))
    }

    @Test
    fun `400 and 413 map to Unsendable`(@TempDir dir: Path) = runTest {
        val file = logFile(dir)
        assertEquals(DeviceLogResult.Unsendable, apiAnswering(HttpStatusCode.BadRequest).sendIt(file))
        assertEquals(DeviceLogResult.Unsendable, apiAnswering(HttpStatusCode.PayloadTooLarge).sendIt(file))
    }

    @Test
    fun `404, 409, 429 and 500 map to RetryLater`(@TempDir dir: Path) = runTest {
        val file = logFile(dir)
        val statuses = listOf(
            HttpStatusCode.NotFound,
            HttpStatusCode.Conflict,
            HttpStatusCode.TooManyRequests,
            HttpStatusCode.InternalServerError,
        )
        for (status in statuses) {
            assertEquals(DeviceLogResult.RetryLater, apiAnswering(status).sendIt(file), "status ${status.value}")
        }
    }

    @Test
    fun `a network failure maps to FileFailed and does not throw`(@TempDir dir: Path) = runTest {
        val api = KtorDeviceLogApi(engine = MockEngine { throw IOException("connection refused") })

        assertEquals(DeviceLogResult.FileFailed, api.sendIt(logFile(dir)))
    }

    @Test
    fun `an unreadable file maps to FileFailed and does not throw`(@TempDir dir: Path) = runTest {
        val api = apiAnswering(HttpStatusCode.OK)

        assertEquals(DeviceLogResult.FileFailed, api.sendIt(dir.resolve("missing.log.gz")))
    }

    @Test
    fun `log privacy - no line from the api contains the token, the installation id, the file name or a path`(@TempDir dir: Path) {
        val file = logFile(dir)
        val apis = listOf(
            apiAnswering(HttpStatusCode.OK),
            apiAnswering(HttpStatusCode.BadRequest),
            apiAnswering(HttpStatusCode.NotFound),
            KtorDeviceLogApi(engine = MockEngine { throw IOException("refused: $siteToken $installationId $file") }),
        )

        val logs = captureLogs { runBlocking { apis.forEach { it.sendIt(file) } } }

        assertTrue(logs.any { it.loggerName.contains("KtorDeviceLogApi") })
        for (event in logs) {
            val text = event.fullText
            assertFalse(text.contains(siteToken), text)
            assertFalse(text.contains(installationId), text)
            assertFalse(text.contains(fileName), text)
            assertFalse(text.contains(dir.toString()), text)
        }
    }
}
