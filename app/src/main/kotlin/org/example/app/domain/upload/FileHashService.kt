package org.example.app.domain.upload

import java.nio.file.Path

/**
 * Computes the SHA-256 of a whole file (§8.9: "ZIP SHA-256" sent alongside the upload payload).
 * Kept as its own tiny domain port — implemented over real file I/O by
 * [org.example.app.infrastructure.persistence.Sha256FileHashService] — rather than folded into
 * [org.example.app.domain.session.SessionArchiveService] (which hashes the ZIP's *entries*, not
 * the ZIP file itself) so `UploadSessionUseCase` can always (re-)hash the archive fresh at
 * upload time, independent of whatever `ProcessSessionUseCase` last did.
 */
interface FileHashService {
    fun sha256(file: Path): String
}
