package org.example.app.domain.session

import java.nio.file.Path

/**
 * One entry to place in a processing archive ZIP (§8.8): either a real file already on disk
 * ([FileEntry] — participant.json, examination.json, timeline JSONs, clips, and the
 * temp-staged converted master) or in-memory bytes synthesized only for the archive
 * ([BytesEntry] — used for `manifest.json` itself, computed last from the hashes of every
 * other entry). [zipPath] is the ZIP-internal entry name (forward slashes, no leading `/`,
 * mirroring the session directory's own relative layout for everything but the master).
 */
sealed interface ArchiveEntry {
    val zipPath: String

    data class FileEntry(override val zipPath: String, val source: Path) : ArchiveEntry
    data class BytesEntry(override val zipPath: String, val bytes: ByteArray) : ArchiveEntry
}

data class ArchiveResult(val zipFile: Path, val manifest: SessionArchiveManifest)

/**
 * Builds a session's processing archive ZIP + manifest (§8.8) from an already-decided list of
 * entries. Kept as a domain port — implemented over real `java.util.zip`/file I/O by
 * [org.example.app.infrastructure.persistence.ZipSessionArchiveService] — so [ProcessSessionUseCase]
 * itself never depends on infrastructure directly (§12), and so unit tests can substitute a
 * fake that records the entry list (e.g. to assert the §8.8 exclusion rules were applied)
 * without any real zipping.
 */
interface SessionArchiveService {
    /**
     * Writes [zipFile] (atomically: temp file + rename) containing every one of [entries] plus
     * a final `manifest.json` (SHA-256 of each entry + [sessionId] + [configVersion] +
     * [generatedAt]) computed from them. [entries] must **not** itself include a
     * `manifest.json` entry — this method is the sole producer of that entry.
     */
    fun build(
        zipFile: Path,
        entries: List<ArchiveEntry>,
        sessionId: String,
        configVersion: String,
        generatedAt: String,
    ): ArchiveResult
}
