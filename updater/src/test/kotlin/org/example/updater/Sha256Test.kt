package org.example.updater

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class Sha256Test {

    @Test
    fun `matches the known digest of a file's content`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("payload.bin")
        Files.write(file, "hello share".toByteArray())

        // sha256("hello share") computed independently via `printf 'hello share' | sha256sum`.
        val expected = "1691eddd2e056f8271d74d9f24cc370bed262d7c02d4510645f3ee7bfa3aa756"

        assertEquals(expected, Sha256.hex(file))
        assertTrue(Sha256.matches(file, expected))
        assertTrue(Sha256.matches(file, expected.uppercase()))
        assertTrue(Sha256.matches(file, "  $expected  "))
    }

    @Test
    fun `different content yields different digests`(@TempDir tempDir: Path) {
        val fileA = tempDir.resolve("a.bin")
        val fileB = tempDir.resolve("b.bin")
        Files.write(fileA, "content A".toByteArray())
        Files.write(fileB, "content B".toByteArray())

        assertFalse(Sha256.hex(fileA) == Sha256.hex(fileB))
    }

    @Test
    fun `mismatch is detected`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("payload.bin")
        Files.write(file, "actual content".toByteArray())

        assertFalse(Sha256.matches(file, "0".repeat(64)))
    }
}
