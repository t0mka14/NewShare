package org.example.app.infrastructure.network

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
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

    private val siteToken = "SECRET-SITE-TOKEN-0042"

    /** The web backend's error shape (`siteController.getSiteConfig`, `rateLimiter`). */
    private fun MockRequestHandleScope.respondWebError(status: HttpStatusCode, message: String) = respond(
        content = """{"error":"$message"}""",
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    @Test
    fun `success maps 200 body to Success with raw json`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/site-config/$siteToken", request.url.encodedPath)
            respond(
                content = """{"schemaVersion":1}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = KtorConfigApi(engine = engine, baseUrl = "http://web.test")

        val result = api.fetchConfig(siteToken)

        assertEquals(ConfigFetchResult.Success("""{"schemaVersion":1}"""), result)
    }

    @Test
    fun `base url path prefix is kept in front of site-config`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/prefix/site-config/$siteToken", request.url.encodedPath)
            respond("{}", HttpStatusCode.OK)
        }
        val api = KtorConfigApi(engine = engine, baseUrl = "http://web.test/prefix/")

        assertTrue(api.fetchConfig(siteToken) is ConfigFetchResult.Success)
    }

    @Test
    fun `404 Invalid token maps to SiteTokenUnknown`() = runTest {
        val engine = MockEngine { respondWebError(HttpStatusCode.NotFound, "Invalid token") }
        val api = KtorConfigApi(engine = engine)

        assertEquals(ConfigFetchResult.SiteTokenUnknown, api.fetchConfig(siteToken))
    }

    @Test
    fun `403 deactivated site maps to SiteDeactivated`() = runTest {
        val engine = MockEngine { respondWebError(HttpStatusCode.Forbidden, "This site has been deactivated.") }
        val api = KtorConfigApi(engine = engine)

        assertEquals(ConfigFetchResult.SiteDeactivated, api.fetchConfig(siteToken))
    }

    @Test
    fun `429 maps to RateLimited`() = runTest {
        val engine = MockEngine {
            respondWebError(HttpStatusCode.TooManyRequests, "Too many requests. Please try again later.")
        }
        val api = KtorConfigApi(engine = engine)

        assertEquals(ConfigFetchResult.RateLimited, api.fetchConfig(siteToken))
    }

    @Test
    fun `401 is not a token rejection, it maps to ServerError`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.Unauthorized) }
        val api = KtorConfigApi(engine = engine)

        assertEquals(ConfigFetchResult.ServerError(401), api.fetchConfig(siteToken))
    }

    @Test
    fun `500 maps to ServerError with status code`() = runTest {
        val engine = MockEngine { respondWebError(HttpStatusCode.InternalServerError, "Internal server error") }
        val api = KtorConfigApi(engine = engine)

        val result = api.fetchConfig(siteToken)

        assertEquals(ConfigFetchResult.ServerError(500), result)
    }

    @Test
    fun `other non-success status maps to ServerError`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.BadGateway) }
        val api = KtorConfigApi(engine = engine)

        assertEquals(ConfigFetchResult.ServerError(502), api.fetchConfig(siteToken))
    }

    @Test
    fun `transport failure maps to NetworkUnavailable`() = runTest {
        val engine = MockEngine { throw IOException("simulated connect failure") }
        val api = KtorConfigApi(engine = engine)

        val result = api.fetchConfig(siteToken)

        assertTrue(result is ConfigFetchResult.NetworkUnavailable)
    }

    @Test
    fun `site token never appears in log output, on transport failure or rejection`() = runTest {
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        val previousLevel = logger.level
        logger.level = Level.ALL
        try {
            KtorConfigApi(engine = MockEngine { throw IOException("Connection refused: /site-config/$siteToken") })
                .fetchConfig(siteToken)
            KtorConfigApi(engine = MockEngine { respondWebError(HttpStatusCode.NotFound, "Invalid token") })
                .fetchConfig(siteToken)

            val leaked = appender.list.any { it.formattedMessage.contains(siteToken) }
            assertFalse(leaked, "site token must never appear in a log line (§11)")
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
        }
    }

    @Test
    fun `NetworkUnavailable detail does not embed the site token`() = runTest {
        val engine = MockEngine { throw IOException("Connection refused: /site-config/$siteToken") }
        val api = KtorConfigApi(engine = engine)

        val result = api.fetchConfig(siteToken) as ConfigFetchResult.NetworkUnavailable

        assertFalse(result.detail.contains(siteToken))
    }
}
