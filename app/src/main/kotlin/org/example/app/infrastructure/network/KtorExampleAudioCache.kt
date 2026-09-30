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
 * Ktor-backed [ExampleAudioCache] (config alignment row 13). Files live in
 * `configDir/example_audio/`, named `sha256(url)` + the URL's extension, so one URL always maps to
 * one file: an unchanged URL is never downloaded again, and new audio needs a new URL. Only
 * `http`/`https` URLs are fetched; each download streams to a `.tmp` file, is capped at
 * [maxBytes], and is moved into place atomically, so a failed or oversized download leaves nothing
 * behind. HTTPS uses the JVM's default trust store, as [KtorConfigApi] does.
 */
class KtorExampleAudioCache(
    directories: AppDirectories,
    engine: HttpClientEngine = CIO.create(),
    private val maxBytes: Long = 20L * 1024 * 1024,
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
        val name = fileNameFor(url) ?: return null
        return cacheDir.resolve(name).takeIf { Files.isRegularFile(it) }
    }

    override suspend fun sync(urls: Set<String>) {
        val wanted = urls.mapNotNull { url -> fileNameFor(url)?.let { url to it } }.toMap()
        urls.filter { it !in wanted }.forEach { logger.warn { "example audio skipped, not an http(s) URL: $it" } }

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
         *  URL that is not `http`/`https`. */
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
