package org.example.app.infrastructure.network

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.example.app.domain.config.ConfigFetchResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.IOException

class KtorConfigApiTest {

    private val installationId = "SECRET-INSTALLATION-ID-42"

    @Test
    fun `success maps 200 body to Success with raw json`() = runTest {
        val engine = MockEngine { request ->
            assertTrue(request.url.toString().contains(installationId))
            respond(
                content = """{"schemaVersion":1}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = KtorConfigApi(engine = engine)

        val result = api.fetchConfig(installationId)

        assertEquals(ConfigFetchResult.Success("""{"schemaVersion":1}"""), result)
    }

    @Test
    fun `401 maps to InvalidInstallationId`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.Unauthorized) }
        val api = KtorConfigApi(engine = engine)

        assertEquals(ConfigFetchResult.InvalidInstallationId, api.fetchConfig(installationId))
    }

    @Test
    fun `403 maps to InvalidInstallationId`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.Forbidden) }
        val api = KtorConfigApi(engine = engine)

        assertEquals(ConfigFetchResult.InvalidInstallationId, api.fetchConfig(installationId))
    }

    @Test
    fun `404 maps to InvalidInstallationId`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
        val api = KtorConfigApi(engine = engine)

        assertEquals(ConfigFetchResult.InvalidInstallationId, api.fetchConfig(installationId))
    }

    @Test
    fun `500 maps to ServerError with status code`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val api = KtorConfigApi(engine = engine)

        val result = api.fetchConfig(installationId)

        assertEquals(ConfigFetchResult.ServerError(500), result)
    }

    @Test
    fun `other non-success status maps to ServerError`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.BadGateway) }
        val api = KtorConfigApi(engine = engine)

        assertEquals(ConfigFetchResult.ServerError(502), api.fetchConfig(installationId))
    }

    @Test
    fun `transport failure maps to NetworkUnavailable`() = runTest {
        val engine = MockEngine { throw IOException("simulated connect failure") }
        val api = KtorConfigApi(engine = engine)

        val result = api.fetchConfig(installationId)

        assertTrue(result is ConfigFetchResult.NetworkUnavailable)
    }

    @Test
    fun `installation id never appears in log output, even on transport failure`() = runTest {
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        val previousLevel = logger.level
        logger.level = Level.ALL
        try {
            val engine = MockEngine { throw IOException("Connection refused: /api/config/$installationId") }
            val api = KtorConfigApi(engine = engine)

            api.fetchConfig(installationId)

            val leaked = appender.list.any { it.formattedMessage.contains(installationId) }
            assertFalse(leaked, "installation id must never appear in a log line (§11)")
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
        }
    }

    @Test
    fun `NetworkUnavailable detail does not embed the installation id`() = runTest {
        val engine = MockEngine { throw IOException("Connection refused: /api/config/$installationId") }
        val api = KtorConfigApi(engine = engine)

        val result = api.fetchConfig(installationId) as ConfigFetchResult.NetworkUnavailable

        assertFalse(result.detail.contains(installationId))
    }
}
