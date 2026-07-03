package org.example.app.infrastructure.config

import org.example.app.domain.AppDirectories
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Bytes-in/bytes-out atomic persistence for the raw remote-config JSON at
 * `AppDirectories.configDir/config.json` (§6.1 pt 3).
 *
 * This is deliberately **not** the `ConfigurationRepository` from §5.4 — schema-version
 * checks, migration, and validation are Phase 2 / domain-engineer work. This class only
 * guarantees that a write is atomic: readers never observe a partially written file, and a
 * write that fails partway through never corrupts the previously cached config.
 *
 * Write sequence (§6.1 pt 3): write to `config.json.tmp`, `fsync`, then atomically rename
 * over `config.json`. If the process dies before the rename, `config.json` is untouched and
 * the stale `.tmp` file is simply overwritten by the next write attempt.
 */
class RawConfigCache(directories: AppDirectories) {

    private val configFile: Path = directories.configDir.resolve("config.json")
    private val tmpFile: Path = directories.configDir.resolve("config.json.tmp")

    /** Returns the cached raw config JSON, or `null` if no cache exists yet. */
    fun read(): String? {
        if (!Files.exists(configFile)) return null
        return Files.readString(configFile, StandardCharsets.UTF_8)
    }

    /**
     * Atomically replaces the cached config with [json]. On success, `config.json.tmp`
     * never exists on disk (it is removed by the rename step).
     */
    fun write(json: String) {
        Files.createDirectories(configFile.parent)
        FileChannel.open(
            tmpFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        ).use { channel ->
            val buffer = java.nio.ByteBuffer.wrap(json.toByteArray(StandardCharsets.UTF_8))
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
            channel.force(true)
        }
        Files.move(
            tmpFile,
            configFile,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
