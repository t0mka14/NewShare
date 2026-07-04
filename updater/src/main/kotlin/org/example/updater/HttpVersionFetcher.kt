package org.example.updater

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
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
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) : VersionFetcher {

    private val json = Json { ignoreUnknownKeys = true }

    override fun fetchLatest(): VersionCheckResult {
        return try {
            val request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                log.warn("Version check returned HTTP ${response.statusCode()}")
                return VersionCheckResult.Unreachable
            }
            val parsed = json.decodeFromString(VersionCheckResponse.serializer(), response.body())
            VersionCheckResult.Available(parsed)
        } catch (e: IOException) {
            log.warn("Version check unreachable: ${e.message}")
            VersionCheckResult.Unreachable
        } catch (e: SerializationException) {
            log.warn("Version check response did not parse: ${e.message}")
            VersionCheckResult.Unreachable
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            VersionCheckResult.Unreachable
        }
    }
}
