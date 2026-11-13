package org.example.app.domain.config

import java.nio.file.Path

/**
 * Local copies of the config's example audio (config alignment row 13): `VocalTask.audioExamplePath`
 * is either an absolute `http(s)://…` URL or a path relative to the build's server base URL (e.g.
 * `audio/illustrations/phonation_a.wav`; a leading `/` is still relative to the base). It is
 * downloaded when a new config is applied so the example button works offline afterwards. Both
 * methods take the value exactly as the config holds it; the implementation resolves it. A value
 * that was never downloaded (or failed to, or does not resolve to an http(s) URL under the server
 * base) has no file, and the task screen then shows no example button.
 */
interface ExampleAudioCache {
    /** The cached file for [url] (absolute or server-relative, as in the config), or `null` when it is not cached. */
    fun localFileFor(url: String): Path?

    /**
     * Downloads the [urls] not cached yet and deletes cached files no longer referenced.
     * Best effort: a failed download is logged and skipped, never thrown.
     */
    suspend fun sync(urls: Set<String>)

    /** No cache at all — every task has no example button. */
    object None : ExampleAudioCache {
        override fun localFileFor(url: String): Path? = null
        override suspend fun sync(urls: Set<String>) = Unit
    }
}

/** Every example-audio URL the config references, across all protocols. */
fun RemoteConfig.exampleAudioUrls(): Set<String> =
    protocols.asSequence()
        .flatMap { it.tasks }
        .filterIsInstance<VocalTask>()
        .mapNotNull { it.audioExamplePath?.takeIf(String::isNotBlank) }
        .toSet()
