package org.example.updater

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

/** §10.1/§10.2 "version-check JSON parsing (fake/local HTTP ... no real network)". Uses the JDK's
 * built-in `com.sun.net.httpserver.HttpServer` bound to loopback — no mocking framework, no
 * external network, and keeps the updater's own dependency footprint unaffected by test-only
 * needs. */
class HttpVersionFetcherTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    private fun startServer(status: Int, body: String): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/api/version/latest") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        httpServer.start()
        server = httpServer
        return "http://127.0.0.1:${httpServer.address.port}/api/version/latest"
    }

    @Test
    fun `parses a well-formed response`(@TempDir tempDir: Path) {
        val endpoint = startServer(
            200,
            """{"version":"2.1.0","downloadUrl":"http://example.test/app-2.1.0.zip","checksum":"abc123"}""",
        )
        val fetcher = HttpVersionFetcher(endpoint, UpdaterLog(tempDir.resolve("updater.log")))

        val result = fetcher.fetchLatest()

        assertTrue(result is VersionCheckResult.Available)
        result as VersionCheckResult.Available
        assertEquals("2.1.0", result.response.version)
        assertEquals("http://example.test/app-2.1.0.zip", result.response.downloadUrl)
        assertEquals("abc123", result.response.checksum)
    }

    @Test
    fun `treats a server error as unreachable`(@TempDir tempDir: Path) {
        val endpoint = startServer(500, "internal error")
        val fetcher = HttpVersionFetcher(endpoint, UpdaterLog(tempDir.resolve("updater.log")))

        val result = fetcher.fetchLatest()

        assertEquals(VersionCheckResult.Unreachable, result)
    }

    @Test
    fun `treats a malformed JSON body as unreachable`(@TempDir tempDir: Path) {
        val endpoint = startServer(200, """{"version":"2.1.0"}""") // missing required fields
        val fetcher = HttpVersionFetcher(endpoint, UpdaterLog(tempDir.resolve("updater.log")))

        val result = fetcher.fetchLatest()

        assertEquals(VersionCheckResult.Unreachable, result)
    }

    @Test
    fun `treats connection refused as unreachable`(@TempDir tempDir: Path) {
        // Nothing listening on this port.
        val fetcher = HttpVersionFetcher("http://127.0.0.1:1/api/version/latest", UpdaterLog(tempDir.resolve("updater.log")))

        val result = fetcher.fetchLatest()

        assertEquals(VersionCheckResult.Unreachable, result)
    }
}
