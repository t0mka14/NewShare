package org.example.updater

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.example.updater.model.InstalledState
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Reads and writes [InstalledState] at `<install_dir>/installed.json`.
 *
 * Writes are atomic — temp sibling, `force(true)`, then an atomic rename — mirroring `:app`'s
 * `AtomicFileWriter`. Duplicated rather than shared for the same reason as [HostPlatform]:
 * `:updater` does not depend on `:app`. A half-written ledger would be worse than none, because it
 * describes what is on disk.
 */
class InstalledStateStore(private val file: Path, private val log: UpdaterLog) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /** Never throws: a missing or unreadable ledger degrades to an empty one, which reads as
     * "everything is stale" and re-syncs from the server rather than guessing what is installed. */
    fun load(): InstalledState {
        if (!Files.isRegularFile(file)) return InstalledState()
        return try {
            json.decodeFromString(InstalledState.serializer(), Files.readString(file))
        } catch (e: IOException) {
            log.warn("Could not read installed.json: ${e.describe()}"); InstalledState()
        } catch (e: SerializationException) {
            log.warn("Could not parse installed.json: ${e.describe()}"); InstalledState()
        }
    }

    fun save(state: InstalledState) {
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        try {
            FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                channel.write(java.nio.ByteBuffer.wrap(
                    (json.encodeToString(InstalledState.serializer(), state) + "\n").toByteArray()))
                channel.force(true)
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            log.error("Could not write installed.json", e)
            runCatching { Files.deleteIfExists(temp) }
        }
    }
}
