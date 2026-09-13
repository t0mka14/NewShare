package org.example.updater.model

import kotlinx.serialization.Serializable

/**
 * `<install_dir>/installed.json` — what the updater believes is currently installed.
 *
 * Records each component's artifact [checksum][InstalledComponent.checksum] rather than a version,
 * because that is what "is this stale?" compares against and it needs no versioning scheme shared
 * between the JDK, ffmpeg and the app.
 *
 * Not part of `data/` — the updater never touches that directory (§9 pt 5).
 */
@Serializable
data class InstalledState(
    val version: Int = 1,
    /** The last release successfully applied. Null on an install that predates the ledger, which
     * reads as "everything is stale" and re-syncs from the server once. */
    val release: String? = null,
    val components: Map<String, InstalledComponent> = emptyMap(),
    /** Releases whose apply was never confirmed by a successful app launch. Skipped until the
     * server offers a different one, which is what stops a broken release from being rolled back
     * and re-applied on every single launch. */
    val failedReleases: List<String> = emptyList(),
) {
    fun checksumOf(id: String): String? = components[id]?.checksum

    fun withComponent(id: String, target: String, checksum: String, at: String): InstalledState =
        copy(components = components + (id to InstalledComponent(target, checksum, at)))

    /** Capped so a long-lived install cannot grow the file without bound. */
    fun withFailedRelease(release: String): InstalledState =
        if (release in failedReleases) this
        else copy(failedReleases = (failedReleases + release).takeLast(MAX_FAILED_RELEASES))

    companion object { const val MAX_FAILED_RELEASES = 20 }
}

@Serializable
data class InstalledComponent(
    val target: String,
    val checksum: String,
    val installedAt: String,
)
