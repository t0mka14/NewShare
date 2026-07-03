package org.example.app.infrastructure.persistence

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Shared write-tmp-fsync-rename primitive (§8.10: every persisted JSON file in §8.10 uses
 * this pattern) used by every JSON-file repository in this package. Mirrors
 * `infrastructure/config/RawConfigCache`'s write sequence.
 */
internal object AtomicFileWriter {
    fun writeString(path: Path, content: String) {
        path.parent?.let { Files.createDirectories(it) }
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        FileChannel.open(
            tmp,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        ).use { channel ->
            val buffer = ByteBuffer.wrap(content.toByteArray(StandardCharsets.UTF_8))
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
            channel.force(true)
        }
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun readStringOrNull(path: Path): String? {
        if (!Files.exists(path)) return null
        return Files.readString(path, StandardCharsets.UTF_8)
    }
}
