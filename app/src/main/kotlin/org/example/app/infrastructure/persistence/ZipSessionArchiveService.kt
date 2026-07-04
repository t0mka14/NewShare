package org.example.app.infrastructure.persistence

import kotlinx.serialization.json.Json
import org.example.app.domain.session.ArchiveEntry
import org.example.app.domain.session.ArchiveResult
import org.example.app.domain.session.ManifestEntry
import org.example.app.domain.session.SessionArchiveManifest
import org.example.app.domain.session.SessionArchiveService
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Production [SessionArchiveService] (§8.8): streams every entry into a `.tmp` sibling ZIP
 * (SHA-256'd as it streams, never loading a whole file into memory), then appends
 * `manifest.json` and atomically renames into place — a crash mid-build never leaves a
 * half-written archive at the real path. */
class ZipSessionArchiveService : SessionArchiveService {

    private val json = Json { ignoreUnknownKeys = true }

    override fun build(
        zipFile: Path,
        entries: List<ArchiveEntry>,
        sessionId: String,
        configVersion: String,
        generatedAt: String,
    ): ArchiveResult {
        zipFile.parent?.let { Files.createDirectories(it) }
        val tmp = zipFile.resolveSibling(zipFile.fileName.toString() + ".tmp")
        val manifestEntries = mutableListOf<ManifestEntry>()
        lateinit var manifest: SessionArchiveManifest

        ZipOutputStream(BufferedOutputStream(Files.newOutputStream(tmp))).use { zip ->
            for (entry in entries) {
                val digest = MessageDigest.getInstance("SHA-256")
                zip.putNextEntry(ZipEntry(entry.zipPath))
                when (entry) {
                    is ArchiveEntry.FileEntry -> {
                        Files.newInputStream(entry.source).use { input ->
                            val buffer = ByteArray(COPY_BUFFER_BYTES)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                digest.update(buffer, 0, n)
                                zip.write(buffer, 0, n)
                            }
                        }
                    }
                    is ArchiveEntry.BytesEntry -> {
                        digest.update(entry.bytes)
                        zip.write(entry.bytes)
                    }
                }
                zip.closeEntry()
                manifestEntries += ManifestEntry(entry.zipPath, digest.digest().toHexString())
            }

            manifest = SessionArchiveManifest(
                sessionId = sessionId,
                configVersion = configVersion,
                generatedAt = generatedAt,
                files = manifestEntries,
            )
            val manifestBytes = json.encodeToString(SessionArchiveManifest.serializer(), manifest)
                .toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
            zip.write(manifestBytes)
            zip.closeEntry()
        }

        Files.move(tmp, zipFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return ArchiveResult(zipFile, manifest)
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val COPY_BUFFER_BYTES = 1 shl 16 // 64 KiB
        const val MANIFEST_ENTRY_NAME = "manifest.json"
    }
}
