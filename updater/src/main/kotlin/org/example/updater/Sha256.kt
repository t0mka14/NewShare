package org.example.updater

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** SHA-256 verification for downloaded update packages (§9 pt 2, §11 "checksum mismatch ⇒
 * discard, log, launch existing app"). */
object Sha256 {
    fun hex(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(file: Path, expectedHex: String): Boolean =
        hex(file).equals(expectedHex.trim(), ignoreCase = true)
}
