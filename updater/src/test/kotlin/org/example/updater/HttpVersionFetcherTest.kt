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

    /** Query string of the last request the fake server saw, for the platform-parameter test. */
    private var lastQuery: String? = null

    private fun startServer(status: Int, body: String): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/api/version/latest") { exchange ->
            lastQuery = exchange.requestURI.query
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
            """{"release":"2.1.0","components":[
                 {"id":"app","target":"app","url":"http://example.test/app.zip","checksum":"abc123"},
                 {"id":"runtime","target":"runtime","url":"http://example.test/rt.zip","checksum":"def456"}]}""",
        )
        val fetcher = HttpVersionFetcher(endpoint, UpdaterLog(tempDir.resolve("updater.log")))

        val result = fetcher.fetchLatest()

        assertTrue(result is VersionCheckResult.Available)
        result as VersionCheckResult.Available
        assertEquals("2.1.0", result.response.release)
        assertEquals(listOf("app", "runtime"), result.response.components.map { it.id })
        assertEquals("http://example.test/app.zip", result.response.components[0].url)
        assertEquals("abc123", result.response.components[0].checksum)
        assertEquals("runtime", result.response.components[1].target)
    }

    private fun componentUrls(endpoint: String, tempDir: Path, vararg urls: String): List<String> {
        val body = """{"release":"2.1.0","components":[""" +
            urls.mapIndexed { i, u -> """{"id":"c$i","target":"t$i","url":"$u","checksum":"abc"}""" }.joinToString(",") + "]}"
        val server = startServer(200, body)
        val fetcher = HttpVersionFetcher(endpoint.replace("SERVER", server), UpdaterLog(tempDir.resolve("updater.log")))
        val result = fetcher.fetchLatest() as VersionCheckResult.Available
        return result.response.components.map { it.url }
    }

    @Test
    fun `resolves a relative component url against the endpoint`(@TempDir tempDir: Path) {
        val urls = componentUrls("SERVER", tempDir, "download/1.0.1/app.zip")
        val base = server!!.address.port
        assertEquals(listOf("http://127.0.0.1:$base/api/version/download/1.0.1/app.zip"), urls)
    }

    @Test
    fun `keeps an absolute component url and ignores the query string when resolving`(@TempDir tempDir: Path) {
        val urls = componentUrls(
            "SERVER?channel=beta", tempDir,
            "https://cdn.example.test/app.zip", "download/1.0.1/rt.zip",
        )
        val port = server!!.address.port
        assertEquals(
            listOf("https://cdn.example.test/app.zip", "http://127.0.0.1:$port/api/version/download/1.0.1/rt.zip"),
            urls,
        )
    }

    @Test
    fun `resolves against a prefixed https endpoint without its query`(@TempDir tempDir: Path) {
        // Pure resolution: no request is made, so the unreachable host does not matter.
        val fetcher = HttpVersionFetcher(
            "https://malenia.feld.cvut.cz/koubic/api/version/latest?platform=x",
            UpdaterLog(tempDir.resolve("updater.log")),
        )
        val out = fetcher.resolveComponentUrls(
            listOf(
                org.example.shared.model.ComponentDescriptor("app", "app", "download/1.0.1/app.zip", "abc"),
                org.example.shared.model.ComponentDescriptor("bad", "bad", "file:///etc/passwd", "abc"),
            ),
        )
        assertEquals(
            listOf("https://malenia.feld.cvut.cz/koubic/api/version/download/1.0.1/app.zip"),
            out.map { it.url },
        )
    }

    @Test
    fun `asks the server for this platform's components`(@TempDir tempDir: Path) {
        val endpoint = startServer(200, """{"release":"2.1.0","components":[]}""")
        val fetcher = HttpVersionFetcher(endpoint, UpdaterLog(tempDir.resolve("updater.log")), platform = "windows-x86_64")

        fetcher.fetchLatest()

        assertEquals("platform=windows-x86_64", lastQuery)
    }

    @Test
    fun `appends the platform to an endpoint that already has a query string`(@TempDir tempDir: Path) {
        val endpoint = startServer(200, """{"release":"2.1.0","components":[]}""") + "?channel=beta"
        val fetcher = HttpVersionFetcher(endpoint, UpdaterLog(tempDir.resolve("updater.log")), platform = "linux-x86_64")

        fetcher.fetchLatest()

        assertEquals("channel=beta&platform=linux-x86_64", lastQuery)
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
        val endpoint = startServer(200, """{"components":[]}""") // missing the required release
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
