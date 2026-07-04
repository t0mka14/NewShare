package org.example.app.domain.session

/**
 * Filters which relative session-directory paths participate in the processing archive ZIP
 * (§8.8). [ProcessSessionUseCase] walks the real session directory and passes every relative
 * regular-file path it finds through [filter] — kept as a pure function over plain strings
 * (rather than inline in the use case) so the exclusion rule itself is unit-testable without a
 * real directory tree.
 *
 * Excluded: `archive/` (the ZIP's own home — self-inclusion would be nonsensical), `waveform_cache/`
 * (regenerable editor cache), `timeline.events.jsonl` (the raw audit log, superseded by the
 * compacted `timeline_original.json`/`timeline_edited.json`), `metadata/` (local upload
 * bookkeeping, not clinical data), and `master/` (the *raw* master directory — the archive gets
 * a single synthesized, converted/concatenated `master/session_master.wav` entry from a
 * temp-staged file instead, so the immutable original part file(s) are never read into the ZIP
 * directly, §8.1).
 */
object SessionArchiveContents {
    private val excludedTopLevelDirs = setOf("archive", "waveform_cache", "metadata", "master")
    private val excludedFiles = setOf("timeline.events.jsonl")

    fun isIncluded(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/').removePrefix("/")
        if (normalized in excludedFiles) return false
        val topLevel = normalized.substringBefore('/')
        return topLevel !in excludedTopLevelDirs
    }

    fun filter(relativePaths: List<String>): List<String> = relativePaths.filter(::isIncluded)
}
