package org.example.updater

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration

fun interface Downloader {
    /** Downloads [url] to [destination]. Never throws — failure is reported via the return value
     * so [Updater] treats "download failed" and "checksum mismatch" uniformly (§11: discard, log,
     * launch existing app). */
    fun download(url: String, destination: Path): Boolean
}

class HttpDownloader(
    private val log: UpdaterLog,
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
) : Downloader {
    override fun download(url: String, destination: Path): Boolean {
        return try {
            val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination))
            val ok = response.statusCode() in 200..299
            if (!ok) log.warn("Download returned HTTP ${response.statusCode()}")
            ok
        } catch (e: Exception) {
            log.warn("Download failed: ${e.message}")
            false
        }
    }
}
