package org.example.updater

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.example.shared.model.ComponentDescriptor
import org.example.shared.model.VersionCheckResponse
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Real [VersionFetcher], backed by `java.net.http.HttpClient` rather than Ktor (§9: keep the
 * updater dependency-light / native-image-friendly). [endpoint] is the single configuration
 * point for the full version-check URL (see [UpdaterConfig]) — the real server contract is still
 * pending (spec §13 open q1).
 */
class HttpVersionFetcher(
    private val endpoint: String,
    private val log: UpdaterLog,
    private val platform: String = HostPlatform.current,
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        // NORMAL: follows a proxy's 301/302 (e.g. a slash or host redirect), never https -> http.
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) : VersionFetcher {

    private val json = Json { ignoreUnknownKeys = true }

    /** The runtime and native components differ per platform, so the server needs to know which
     * install is asking. [endpoint] is a whole URL from configuration and may already carry a
     * query string, so the separator is chosen rather than assumed. */
    private fun endpointWithPlatform(): String =
        if (endpoint.contains('?')) "$endpoint&platform=$platform" else "$endpoint?platform=$platform"

    /**
     * The server may give component URLs relative to the version endpoint (`download/1.0.1/app.zip`,
     * so one backend serves several origins). They resolve against the endpoint URI without its
     * query, replacing the last path segment; absolute URLs pass through unchanged. A component
     * whose resolved URL is not http/https (or does not parse) is dropped with a log line.
     */
    internal fun resolveComponentUrls(components: List<ComponentDescriptor>): List<ComponentDescriptor> {
        val base = endpointWithoutQuery()
        return components.mapNotNull { component ->
            val resolved = runCatching { base.resolve(component.url) }.getOrNull()
            if (resolved == null || resolved.scheme?.lowercase() !in setOf("http", "https")) {
                log.warn("Skipping component '${component.id}': URL is not a usable http(s) URL")
                null
            } else {
                component.copy(url = resolved.toString())
            }
        }
    }

    private fun endpointWithoutQuery(): URI {
        val uri = URI.create(endpoint)
        return URI(uri.scheme, uri.rawAuthority, uri.path, null, null)
    }

    override fun fetchLatest(): VersionCheckResult {
        return try {
            val request = HttpRequest.newBuilder(URI.create(endpointWithPlatform()))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                log.warn("Version check returned HTTP ${response.statusCode()}")
                return VersionCheckResult.Unreachable
            }
            val parsed = json.decodeFromString(VersionCheckResponse.serializer(), response.body())
            VersionCheckResult.Available(parsed.copy(components = resolveComponentUrls(parsed.components)))
        } catch (e: IOException) {
            log.warn("Version check unreachable: ${e.describe()}")
            VersionCheckResult.Unreachable
        } catch (e: SerializationException) {
            log.warn("Version check response did not parse: ${e.describe()}")
            VersionCheckResult.Unreachable
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            VersionCheckResult.Unreachable
        }
    }
}
