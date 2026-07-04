package org.example.updater.fakes

import org.example.updater.Downloader
import java.nio.file.Files
import java.nio.file.Path

/** Writes [contentIfSuccessful] to the destination and reports [succeeds] — stands in for a real
 * HTTP download in tests with no network. */
class FakeDownloader(
    private val succeeds: Boolean,
    private val contentIfSuccessful: ByteArray = ByteArray(0),
) : Downloader {
    var lastUrl: String? = null
        private set

    override fun download(url: String, destination: Path): Boolean {
        lastUrl = url
        if (!succeeds) return false
        Files.write(destination, contentIfSuccessful)
        return true
    }
}
