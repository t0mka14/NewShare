package org.example.app.infrastructure.network

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import org.example.app.domain.AppDirectories
import org.example.app.domain.config.ExampleAudioCache
import org.example.app.infrastructure.logging.LogPolicy
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

/**
 * Resolves a config `audioExamplePath` to the URL that is fetched and keyed on, or `null` when it
 * must not be fetched.
 * - An absolute `http`/`https` URL with a host is returned **unchanged** (the exact same string, so
 *   cache keys of existing installs do not change).
 * - Anything else without a scheme is a path relative to [serverBase] (the build's server base, no
 *   `/api`): leading `/`s are stripped, so `/audio/x.wav` stays under a prefixed base such as
 *   `https://host/koubic`. The result is normalized and must stay under the base path; `..`
 *   escapes (also percent-encoded ones) are rejected.
 * - Any other scheme (`file:`, `C:`, …), a blank value or an unparsable one gives `null`.
 */
internal fun resolveExampleUrl(raw: String, serverBase: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    val uri = try { URI(value) } catch (e: Exception) { return null }
    if (uri.scheme != null) return value.takeIf { uri.isHttpWithHost() }

    val base = try { URI(serverBase.trim().trimEnd('/') + "/").normalize() } catch (e: Exception) { return null }
    if (!base.isHttpWithHost()) return null
    val path = value.trimStart('/')
    if (path.isEmpty()) return null
    val resolved = try { base.resolve(URI(path)).normalize() } catch (e: Exception) { return null }
    if (!resolved.isHttpWithHost()) return null
    if (resolved.host != base.host || resolved.port != base.port) return null
    val resolvedPath = resolved.path.orEmpty()
    val basePath = base.path.orEmpty()
    if (!resolvedPath.startsWith(basePath) || resolvedPath == basePath) return null
    if (resolvedPath.split('/').any { it == ".." || it == "." }) return null
    return resolved.toString()
}

private fun URI.isHttpWithHost(): Boolean =
    scheme?.lowercase() in setOf("http", "https") && !host.isNullOrBlank()

/**
 * Ktor-backed [ExampleAudioCache] (config alignment row 13). Each config value is first resolved
 * with [resolveExampleUrl] against [serverBaseUrl]; `sync`, `localFileFor` and pruning all use the
 * resolved URL. Files live in `configDir/example_audio/`, named `sha256(resolved url)` + the URL's
 * extension, so one URL always maps to one file: an unchanged URL is never downloaded again, and
 * new audio needs a new URL. Only `http`/`https` URLs are fetched; each download streams to a `.tmp` file, is capped at
 * [maxBytes], and is moved into place atomically, so a failed or oversized download leaves nothing
 * behind. HTTPS uses the JVM's default trust store, as [KtorConfigApi] does.
 */
class KtorExampleAudioCache(
    directories: AppDirectories,
    engine: HttpClientEngine = CIO.create(),
    private val maxBytes: Long = 20L * 1024 * 1024,
    private val serverBaseUrl: String = SERVER_BASE_URL,
) : ExampleAudioCache {

    private val cacheDir: Path = directories.configDir.resolve("example_audio")

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
        }
    }

    override fun localFileFor(url: String): Path? {
        val name = resolveExampleUrl(url, serverBaseUrl)?.let(::fileNameFor) ?: return null
        return cacheDir.resolve(name).takeIf { Files.isRegularFile(it) }
    }

    override suspend fun sync(urls: Set<String>) {
        val resolved = urls.associateWith { resolveExampleUrl(it, serverBaseUrl) }
        resolved.filterValues { it == null }.keys
            .forEach { logger.warn { "example audio skipped, not an http(s) URL or path under the server base: $it" } }
        val wanted = resolved.values.filterNotNull()
            .mapNotNull { url -> fileNameFor(url)?.let { url to it } }.toMap()

        try {
            Files.createDirectories(cacheDir)
        } catch (e: Exception) {
            logger.warn { "example audio cache dir unavailable: ${LogPolicy.safeDescribe(e)}" }
            return
        }
        prune(keep = wanted.values.toSet())
        for ((url, name) in wanted) {
            if (!Files.isRegularFile(cacheDir.resolve(name))) download(url, name)
        }
    }

    /** Releases the underlying Ktor engine/connection pool. */
    fun close() = client.close()

    private suspend fun download(url: String, name: String) {
        val target = cacheDir.resolve(name)
        val tmp = cacheDir.resolve("$name.tmp")
        try {
            val saved = client.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) {
                    logger.warn { "example audio download failed: HTTP ${response.status.value} for $url" }
                    return@execute false
                }
                val body = response.bodyAsChannel()
                FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = body.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        total += read
                        if (total > maxBytes) {
                            logger.warn { "example audio larger than $maxBytes bytes, skipped: $url" }
                            return@execute false
                        }
                        val chunk = ByteBuffer.wrap(buffer, 0, read)
                        while (chunk.hasRemaining()) out.write(chunk)
                    }
                    out.force(true)
                }
                true
            }
            if (saved) {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "example audio download failed for $url: ${LogPolicy.safeDescribe(e)}" }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    /** Deletes cached files (and stray `.tmp`s) that no current URL maps to. */
    private fun prune(keep: Set<String>) {
        try {
            Files.list(cacheDir).use { files ->
                files.filter { it.fileName.toString() !in keep }.forEach { Files.deleteIfExists(it) }
            }
        } catch (e: Exception) {
            logger.warn { "example audio prune failed: ${LogPolicy.safeDescribe(e)}" }
        }
    }

    companion object {
        private val SAFE_EXTENSION = Regex("[A-Za-z0-9]{1,5}")

        /** `sha256(url)` + the path's extension (`.wav` when missing or unusual); `null` for a
         *  URL that is not `http`/`https`. Takes an already [resolveExampleUrl]-resolved URL. */
        internal fun fileNameFor(url: String): String? {
            val uri = try { URI(url) } catch (e: Exception) { return null }
            if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
            val extension = uri.path.orEmpty().substringAfterLast('/').substringAfterLast('.', "")
                .takeIf { SAFE_EXTENSION.matches(it) }?.lowercase() ?: "wav"
            val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }
            return "$hash.$extension"
        }
    }
}
