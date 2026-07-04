package org.example.app.domain.session

import kotlinx.serialization.Serializable

/** One `manifest.json.files[]` entry (§8.8): [path] is the ZIP-internal entry name (forward
 * slashes, no leading `/`), [sha256] is the hash of that entry's uncompressed bytes. */
@Serializable
data class ManifestEntry(val path: String, val sha256: String)

/**
 * `manifest.json`, the last entry written into a session's processing archive ZIP (§8.8):
 * SHA-256 of every *other* included file, plus [sessionId] and [configVersion] so the receiving
 * server can verify integrity and provenance without unzipping first. Not one of the primary
 * §8.10 session JSONs (it lives only inside the archive, itself a derived/regenerable artifact,
 * §8.8), but persisted the same way: `@Serializable` with a `version` field (§12).
 */
@Serializable
data class SessionArchiveManifest(
    val version: Int = 1,
    val sessionId: String,
    val configVersion: String,
    val generatedAt: String,
    val files: List<ManifestEntry>,
)
