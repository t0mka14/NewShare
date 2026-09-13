package org.example.updater.fakes

import org.example.updater.Downloader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Serves canned bytes per URL — stands in for a real HTTP download in tests with no network.
 *
 * [payloads] maps a URL to its content; a URL with no entry falls back to [contentIfSuccessful],
 * which keeps the single-component tests readable. [requestedUrls] records every call so tests can
 * assert what was *not* downloaded, which is the whole point of per-component staleness.
 */
class FakeDownloader(
    private val succeeds: Boolean = true,
    private val contentIfSuccessful: ByteArray = ByteArray(0),
    private val payloads: Map<String, ByteArray> = emptyMap(),
    private val failingUrls: Set<String> = emptySet(),
) : Downloader {
    val requestedUrls = mutableListOf<String>()

    val lastUrl: String? get() = requestedUrls.lastOrNull()

    override fun download(url: String, destination: Path): Boolean {
        requestedUrls += url
        if (!succeeds || url in failingUrls) return false
        Files.write(destination, payloads[url] ?: contentIfSuccessful)
        return true
    }
}
