package org.example.app.infrastructure.network

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.test.runTest
import org.example.app.domain.upload.RejectionReason
import org.example.app.domain.upload.UploadResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class KtorUploadApiTest {

    private val installationId = "SECRET-INSTALLATION-ID-42"
    private val sessionId = "session-123"
    private val zipSha256 = "abc123deadbeef"
    private val siteToken = "SECRETsiteTOKEN000000"
    private val appVersion = "1.4.0"

    private fun tempZip(dir: Path): Path {
        val zip = dir.resolve("session.zip")
        Files.write(zip, ByteArray(4096) { it.toByte() })
        return zip
    }

    /** Forces the multipart body's [ByteReadChannel] to be fully read, the way a real
     * transport engine would — required for Ktor's `BodyProgress` plugin (wired via
     * [KtorUploadApi]'s `onUpload`) to actually observe bytes flowing, and lets us inspect the
     * multipart's raw bytes for field assertions. */
    private suspend fun drain(content: OutgoingContent): ByteArray =
        (content as OutgoingContent.ReadChannelContent).readFrom().readRemaining().readBytes()

    @Test
    fun `success maps 2xx with a json body to Success carrying the parsed element`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val engine = MockEngine { request ->
            drain(request.body)
            respond(
                content = """{"accepted":true}""",
                status = HttpStatusCode.OK,
            )
        }
        val api = KtorUploadApi(engine = engine)

        val result = api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {}

        assertTrue(result is UploadResult.Success)
        val body = (result as UploadResult.Success).serverResponse
        assertTrue(body != null)
        assertEquals("""{"accepted":true}""", body.toString())
    }

    @Test
    fun `success with a blank body maps to Success with a null server response`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val engine = MockEngine { request ->
            drain(request.body)
            respond(content = "", status = HttpStatusCode.OK)
        }
        val api = KtorUploadApi(engine = engine)

        val result = api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {}

        assertTrue(result is UploadResult.Success)
        assertEquals(null, (result as UploadResult.Success).serverResponse)
    }

    @Test
    fun `the multipart body carries the zip and all the text parts, text parts first`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        var capturedBytes: ByteArray? = null
        val engine = MockEngine { request ->
            capturedBytes = drain(request.body)
            respond(content = "", status = HttpStatusCode.OK)
        }
        val api = KtorUploadApi(engine = engine)

        api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {}

        val text = String(capturedBytes!!, StandardCharsets.ISO_8859_1)
        assertTrue(text.contains("name=installationId"))
        assertTrue(text.contains(installationId))
        assertTrue(text.contains("name=sessionId"))
        assertTrue(text.contains(sessionId))
        assertTrue(text.contains("name=zipSha256"))
        assertTrue(text.contains(zipSha256))
        assertTrue(text.contains("name=siteToken"))
        assertTrue(text.contains(siteToken))
        assertTrue(text.contains("name=appVersion"))
        assertTrue(text.contains(appVersion))
        // The server rejects a text part after the zip.
        // `(?<!file)` skips the `filename=` of the zip part itself.
        val lastPart = text.substring(Regex("(?<!file)name=").findAll(text).last().range.first)
        assertTrue(lastPart.startsWith("name=zip") && !lastPart.startsWith("name=zipSha256"), "zip must be the last part")
    }

    @Test
    fun `the siteToken part is omitted when no token is set`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        var capturedBytes: ByteArray? = null
        val engine = MockEngine { request ->
            capturedBytes = drain(request.body)
            respond(content = "", status = HttpStatusCode.OK)
        }
        val api = KtorUploadApi(engine = engine)

        api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken = null, appVersion = appVersion) {}

        val text = String(capturedBytes!!, StandardCharsets.ISO_8859_1)
        assertFalse(text.contains("name=siteToken"))
        assertTrue(text.contains("name=appVersion"))
    }

    @Test
    fun `progress callback is invoked and clamped to 0 point 0 through 1 point 0`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val engine = MockEngine { request ->
            drain(request.body)
            respond(content = "", status = HttpStatusCode.OK)
        }
        val api = KtorUploadApi(engine = engine)
        val progressValues = mutableListOf<Float>()

        api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) { progressValues += it }

        assertTrue(progressValues.isNotEmpty(), "onUpload should have reported at least one progress value")
        assertTrue(progressValues.all { it in 0f..1f }, "progress must be clamped to 0..1, got $progressValues")
        assertEquals(1f, progressValues.last(), "the final reported fraction should be 1.0 once the body is fully sent")
    }

    @Test
    fun `401 maps to Rejected`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val engine = MockEngine { respondError(HttpStatusCode.Unauthorized) }
        val api = KtorUploadApi(engine = engine)

        val result = api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {}

        assertTrue(result is UploadResult.Rejected)
    }

    @Test
    fun `403 maps to Rejected`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val engine = MockEngine { respondError(HttpStatusCode.Forbidden) }
        val api = KtorUploadApi(engine = engine)

        assertTrue(api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {} is UploadResult.Rejected)
    }

    @Test
    fun `404 quarantine maps to Rejected DeviceNotRegistered`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val engine = MockEngine {
            respond("""{"error":"device_not_registered","quarantined":true}""", HttpStatusCode.NotFound)
        }
        val api = KtorUploadApi(engine = engine)

        val result = api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {}

        assertEquals(UploadResult.Rejected(RejectionReason.DeviceNotRegistered), result)
    }

    @Test
    fun `409 maps to Conflict, not a retryable ServerError`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val engine = MockEngine { respond("""{"error":"session_exists"}""", HttpStatusCode.Conflict) }
        val api = KtorUploadApi(engine = engine)

        assertEquals(UploadResult.Conflict, api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {})
    }

    @Test
    fun `413 maps to Rejected TooLarge`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val engine = MockEngine { respondError(HttpStatusCode.PayloadTooLarge) }
        val api = KtorUploadApi(engine = engine)

        assertEquals(
            UploadResult.Rejected(RejectionReason.TooLarge),
            api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {},
        )
    }

    @Test
    fun `400, 429, 503 and 507 map to ServerError with their status`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        for (status in listOf(400, 429, 503, 507)) {
            val engine = MockEngine { respondError(HttpStatusCode.fromValue(status)) }
            val api = KtorUploadApi(engine = engine)

            assertEquals(
                UploadResult.ServerError(status),
                api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {},
            )
        }
    }

    @Test
    fun `500 maps to ServerError with the status code`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val api = KtorUploadApi(engine = engine)

        val result = api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {}

        assertTrue(result is UploadResult.ServerError)
        assertEquals(500, (result as UploadResult.ServerError).status)
    }

    @Test
    fun `other non-2xx status maps to ServerError`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val engine = MockEngine { respondError(HttpStatusCode.BadGateway) }
        val api = KtorUploadApi(engine = engine)

        val result = api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {}

        assertTrue(result is UploadResult.ServerError)
        assertEquals(502, (result as UploadResult.ServerError).status)
    }

    @Test
    fun `transport failure maps to NetworkFailure`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val engine = MockEngine { throw IOException("simulated connect failure") }
        val api = KtorUploadApi(engine = engine)

        val result = api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {}

        assertTrue(result is UploadResult.NetworkFailure)
    }

    @Test
    fun `installation id never appears in log output, even on transport failure`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        val previousLevel = logger.level
        logger.level = Level.ALL
        try {
            val engine = MockEngine { throw IOException("Connection refused: /api/upload?id=$installationId&t=$siteToken") }
            val api = KtorUploadApi(engine = engine)

            api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {}

            val leaked = appender.list.any { it.formattedMessage.contains(installationId) || it.formattedMessage.contains(siteToken) }
            assertFalse(leaked, "installation id and site token must never appear in a log line (§11)")
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
        }
    }

    @Test
    fun `the zip file path never appears in log output on transport failure`(@TempDir dir: Path) = runTest {
        val zip = tempZip(dir)
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        val previousLevel = logger.level
        logger.level = Level.ALL
        try {
            val engine = MockEngine { throw IOException("boom, path was $zip") }
            val api = KtorUploadApi(engine = engine)

            api.uploadSession(zip, installationId, sessionId, zipSha256, siteToken, appVersion) {}

            // The zip's path embeds the session folder name, which embeds patientCode (§8.2) —
            // participant data that must never reach a log line (§11).
            val leaked = appender.list.any { it.formattedMessage.contains(zip.toString()) }
            assertFalse(leaked, "the zip path (embeds patientCode) must never appear in a log line (§11)")
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
        }
    }
}
