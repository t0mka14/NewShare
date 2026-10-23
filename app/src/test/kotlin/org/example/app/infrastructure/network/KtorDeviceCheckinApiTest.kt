package org.example.app.infrastructure.network

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.app.domain.upload.DeviceCheckin
import org.example.app.domain.upload.DeviceCheckinResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.IOException

class KtorDeviceCheckinApiTest {

    private val checkin = DeviceCheckin(
        siteToken = "SECRETsiteTOKEN000000",
        installationId = "SECRET-INSTALLATION-ID-42",
        appVersion = "1.4.0",
        pending = 3,
        failed = 1,
    )

    @Test
    fun `posts the json body to device-checkin and maps 200 to Recorded`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/device-checkin", request.url.encodedPath)
            val content = request.body as TextContent
            assertEquals(ContentType.Application.Json, content.contentType.withoutParameters())
            val body = Json.parseToJsonElement(content.text).jsonObject
            assertEquals(checkin.siteToken, body["siteToken"]!!.jsonPrimitive.content)
            assertEquals(checkin.installationId, body["installationId"]!!.jsonPrimitive.content)
            assertEquals("1.4.0", body["appVersion"]!!.jsonPrimitive.content)
            assertEquals(3, body["pending"]!!.jsonPrimitive.int)
            assertEquals(1, body["failed"]!!.jsonPrimitive.int)
            respond("""{"status":"ok"}""", HttpStatusCode.OK)
        }

        val result = KtorDeviceCheckinApi(engine = engine, baseUrl = "http://web.test/api/").checkIn(checkin)

        assertEquals(DeviceCheckinResult.Recorded, result)
    }

    @Test
    fun `status codes map to their results`() = runTest {
        val expected = mapOf(
            HttpStatusCode.NotFound to DeviceCheckinResult.InvalidToken,
            HttpStatusCode.Conflict to DeviceCheckinResult.RegisteredToAnotherSite,
            HttpStatusCode.BadRequest to DeviceCheckinResult.Failed,
            HttpStatusCode.TooManyRequests to DeviceCheckinResult.Failed,
            HttpStatusCode.InternalServerError to DeviceCheckinResult.Failed,
        )
        for ((status, result) in expected) {
            val api = KtorDeviceCheckinApi(engine = MockEngine { respondError(status) })
            assertEquals(result, api.checkIn(checkin), "HTTP ${status.value}")
        }
    }

    @Test
    fun `transport failure maps to Failed and logs neither the token nor the installation id`() = runTest {
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        val previousLevel = logger.level
        logger.level = Level.ALL
        try {
            val engine = MockEngine { throw IOException("refused: ${checkin.siteToken} ${checkin.installationId}") }

            assertEquals(DeviceCheckinResult.Failed, KtorDeviceCheckinApi(engine = engine).checkIn(checkin))

            val leaked = appender.list.any {
                it.formattedMessage.contains(checkin.siteToken) || it.formattedMessage.contains(checkin.installationId)
            }
            assertFalse(leaked, "credentials must never appear in a log line (§11)")
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
        }
    }
}
