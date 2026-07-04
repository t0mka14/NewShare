package org.example.app.fakes

import org.example.app.domain.session.ArchiveEntry
import org.example.app.domain.session.ArchiveResult
import org.example.app.domain.session.ManifestEntry
import org.example.app.domain.session.SessionArchiveManifest
import org.example.app.domain.session.SessionArchiveService
import java.nio.file.Path

/**
 * In-memory [SessionArchiveService] for fast `ProcessSessionUseCase` unit tests (§10.1): records
 * every [build] call's entry list (e.g. to assert the §8.8 exclusion rules were actually
 * applied by the use case) without touching real files or writing a real ZIP. Real
 * zipping/hashing correctness is covered by integration tests against
 * [org.example.app.infrastructure.persistence.ZipSessionArchiveService].
 */
class FakeSessionArchiveService : SessionArchiveService {
    val buildCalls = mutableListOf<BuildCall>()

    override fun build(
        zipFile: Path,
        entries: List<ArchiveEntry>,
        sessionId: String,
        configVersion: String,
        generatedAt: String,
    ): ArchiveResult {
        buildCalls += BuildCall(zipFile, entries, sessionId, configVersion, generatedAt)
        val manifest = SessionArchiveManifest(
            sessionId = sessionId,
            configVersion = configVersion,
            generatedAt = generatedAt,
            files = entries.map { ManifestEntry(it.zipPath, "fake-sha256") },
        )
        return ArchiveResult(zipFile, manifest)
    }

    data class BuildCall(
        val zipFile: Path,
        val entries: List<ArchiveEntry>,
        val sessionId: String,
        val configVersion: String,
        val generatedAt: String,
    )
}
