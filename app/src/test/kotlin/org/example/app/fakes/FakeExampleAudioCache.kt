package org.example.app.fakes

import org.example.app.domain.config.ExampleAudioCache
import java.nio.file.Path

/** Programmable [ExampleAudioCache]: [cached] maps a URL to its "downloaded" file; [syncCalls]
 *  records every [sync] argument; [failSync] makes [sync] throw. */
class FakeExampleAudioCache(
    val cached: MutableMap<String, Path> = mutableMapOf(),
) : ExampleAudioCache {
    val syncCalls = mutableListOf<Set<String>>()
    var failSync: Boolean = false

    override fun localFileFor(url: String): Path? = cached[url]

    override suspend fun sync(urls: Set<String>) {
        syncCalls += urls
        if (failSync) error("FakeExampleAudioCache: simulated failure")
    }
}
