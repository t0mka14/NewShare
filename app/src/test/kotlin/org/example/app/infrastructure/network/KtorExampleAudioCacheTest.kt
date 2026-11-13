package org.example.app.infrastructure.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.example.app.fakes.TestAppDirectories
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class KtorExampleAudioCacheTest {

    private val url = "https://assets.example/audio/illustrations/phonation_a.wav"
    private val wav = ByteArray(1000) { it.toByte() }
    private val base = "https://malenia.feld.cvut.cz/koubic"
    private val resolvedRelative = "https://malenia.feld.cvut.cz/koubic/audio/illustrations/phonation_a.wav"

    private fun cacheDir(tempDir: Path) = TestAppDirectories(tempDir).configDir.resolve("example_audio")

    @Test
    fun `downloads a URL once, named by hash with its extension`(@TempDir tempDir: Path) = runTest {
        var requests = 0
        val cache = KtorExampleAudioCache(TestAppDirectories(tempDir), MockEngine { requests++; respond(wav, HttpStatusCode.OK) })

        cache.sync(setOf(url))
        cache.sync(setOf(url))

        val file = requireNotNull(cache.localFileFor(url))
        assertArrayEquals(wav, Files.readAllBytes(file))
        assertTrue(file.fileName.toString().matches(Regex("[0-9a-f]{64}\\.wav")))
        assertEquals(1, requests)
    }

    @Test
    fun `a failed download leaves no file and does not throw`(@TempDir tempDir: Path) = runTest {
        val notFound = KtorExampleAudioCache(TestAppDirectories(tempDir), MockEngine { respondError(HttpStatusCode.NotFound) })
        notFound.sync(setOf(url))
        assertNull(notFound.localFileFor(url))

        val broken = KtorExampleAudioCache(TestAppDirectories(tempDir), MockEngine { throw IOException("down") })
        broken.sync(setOf(url))
        assertNull(broken.localFileFor(url))
        assertEquals(emptyList<Path>(), Files.list(cacheDir(tempDir)).use { it.toList() })
    }

    @Test
    fun `non-http URLs and paths escaping the server base are skipped without a request`(@TempDir tempDir: Path) = runTest {
        var requests = 0
        val cache = KtorExampleAudioCache(
            TestAppDirectories(tempDir), MockEngine { requests++; respond(wav, HttpStatusCode.OK) }, serverBaseUrl = base,
        )

        cache.sync(setOf("file:///etc/passwd", "../../x.wav", "C:\\audio\\a.wav", " "))

        assertEquals(0, requests)
        assertNull(cache.localFileFor("file:///etc/passwd"))
        assertNull(cache.localFileFor("../../x.wav"))
    }

    @Test
    fun `a relative path resolves under the server base, with or without a leading slash`() {
        assertEquals(resolvedRelative, resolveExampleUrl("audio/illustrations/phonation_a.wav", base))
        assertEquals(resolvedRelative, resolveExampleUrl("/audio/illustrations/phonation_a.wav", base))
        assertEquals(resolvedRelative, resolveExampleUrl("audio/illustrations/phonation_a.wav", "$base/"))
        assertEquals(resolvedRelative, resolveExampleUrl("audio/./illustrations/phonation_a.wav", base))
        assertEquals(
            "http://192.168.122.183:10001/audio/illustrations/phonation_a.wav",
            resolveExampleUrl("audio/illustrations/phonation_a.wav", "http://192.168.122.183:10001"),
        )
    }

    @Test
    fun `an absolute http(s) URL is returned unchanged`() {
        assertEquals(url, resolveExampleUrl(url, base))
        val odd = "http://192.168.122.183:10001/audio/../audio/illustrations/phonation_a.wav?v=1"
        assertEquals(odd, resolveExampleUrl(odd, base))
    }

    @Test
    fun `escapes, other schemes and garbage are rejected`() {
        assertNull(resolveExampleUrl("../../x.wav", base))
        assertNull(resolveExampleUrl("audio/../../x.wav", base))
        assertNull(resolveExampleUrl("/../koubic2/x.wav", base))
        assertNull(resolveExampleUrl("audio/%2e%2e/%2e%2e/x.wav", base))
        assertNull(resolveExampleUrl("file:///etc/passwd", base))
        assertNull(resolveExampleUrl("ftp://h/x.wav", base))
        assertNull(resolveExampleUrl("https:///x.wav", base))
        assertNull(resolveExampleUrl("", base))
        assertNull(resolveExampleUrl("   ", base))
        assertNull(resolveExampleUrl("/", base))
        assertNull(resolveExampleUrl("a b|<>.wav", base))
        assertNull(resolveExampleUrl("audio/x.wav", "not a base"))
    }

    @Test
    fun `sync, localFileFor and prune agree on the resolved key for a relative path`(@TempDir tempDir: Path) = runTest {
        val relative = "audio/illustrations/phonation_a.wav"
        val requested = mutableListOf<String>()
        val cache = KtorExampleAudioCache(
            TestAppDirectories(tempDir),
            MockEngine { request -> requested += request.url.toString(); respond(wav, HttpStatusCode.OK) },
            serverBaseUrl = base,
        )

        cache.sync(setOf(relative))
        val file = requireNotNull(cache.localFileFor(relative))
        assertArrayEquals(wav, Files.readAllBytes(file))
        assertEquals(listOf(resolvedRelative), requested)
        assertEquals(KtorExampleAudioCache.fileNameFor(resolvedRelative), file.fileName.toString())
        assertEquals(file, cache.localFileFor("/$relative"))

        cache.sync(setOf(relative))

        assertEquals(file, cache.localFileFor(relative))
        assertTrue(Files.isRegularFile(file))
        assertEquals(1, requested.size)
        assertEquals(listOf(file), Files.list(cacheDir(tempDir)).use { it.toList() })
    }

    @Test
    fun `a download over the size cap is dropped, with no temp file left`(@TempDir tempDir: Path) = runTest {
        val cache = KtorExampleAudioCache(TestAppDirectories(tempDir), MockEngine { respond(wav, HttpStatusCode.OK) }, maxBytes = 100)

        cache.sync(setOf(url))

        assertNull(cache.localFileFor(url))
        assertEquals(emptyList<Path>(), Files.list(cacheDir(tempDir)).use { it.toList() })
    }

    @Test
    fun `files no longer referenced are pruned`(@TempDir tempDir: Path) = runTest {
        val other = "https://assets.example/audio/pataka.wav"
        val cache = KtorExampleAudioCache(TestAppDirectories(tempDir), MockEngine { respond(wav, HttpStatusCode.OK) })
        cache.sync(setOf(url, other))
        assertNotNull(cache.localFileFor(other))

        cache.sync(setOf(url))

        assertNotNull(cache.localFileFor(url))
        assertNull(cache.localFileFor(other))
    }

    @Test
    fun `an unknown URL has no file, and a URL without an extension defaults to wav`(@TempDir tempDir: Path) {
        val cache = KtorExampleAudioCache(TestAppDirectories(tempDir), MockEngine { respond(wav, HttpStatusCode.OK) })

        assertNull(cache.localFileFor(url))
        assertTrue(KtorExampleAudioCache.fileNameFor("https://h/audio?id=3")!!.endsWith(".wav"))
        assertFalse(KtorExampleAudioCache.fileNameFor("https://h/a.mp3")!!.endsWith(".wav"))
    }
}
