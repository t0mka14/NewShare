package org.example.app.infrastructure.persistence

import org.example.app.domain.upload.FileHashService
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Production [FileHashService]: streams the file so hashing a ~165 MiB archive (§8.1 sizing
 * assumption) never loads it whole into memory. */
class Sha256FileHashService : FileHashService {
    override fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 1 shl 16 // 64 KiB
    }
}
