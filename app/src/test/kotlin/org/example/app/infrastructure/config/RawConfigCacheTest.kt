package org.example.app.infrastructure.config

import org.example.app.domain.AppDirectories
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Minimal [AppDirectories] test double rooted at a JUnit temp dir. */
private class TestAppDirectories(private val root: Path) : AppDirectories {
    override val dataRoot: Path = root
    override val configDir: Path = root.resolve("config")
    override val sessionsDir: Path = root.resolve("sessions")
    override val uploadQueueDir: Path = root.resolve("upload_queue")
    override val logsDir: Path = root.resolve("logs")
    override fun sessionDir(folderName: String): Path = sessionsDir.resolve(folderName)
}

class RawConfigCacheTest {

    @Test
    fun `read returns null when no cache exists`(@TempDir tempDir: Path) {
        val cache = RawConfigCache(TestAppDirectories(tempDir))

        assertNull(cache.read())
    }

    @Test
    fun `write then read round-trips the raw json`(@TempDir tempDir: Path) {
        val dirs = TestAppDirectories(tempDir)
        val cache = RawConfigCache(dirs)

        cache.write("""{"schemaVersion":1,"a":"b"}""")

        assertEquals("""{"schemaVersion":1,"a":"b"}""", cache.read())
    }

    @Test
    fun `write never leaves the tmp file behind on success`(@TempDir tempDir: Path) {
        val dirs = TestAppDirectories(tempDir)
        val cache = RawConfigCache(dirs)

        cache.write("""{"v":1}""")

        val tmp = dirs.configDir.resolve("config.json.tmp")
        assertFalse(Files.exists(tmp), "tmp file must not survive a successful write")
        assertTrue(Files.exists(dirs.configDir.resolve("config.json")))
    }

    @Test
    fun `second write overwrites the first and still leaves no tmp file`(@TempDir tempDir: Path) {
        val dirs = TestAppDirectories(tempDir)
        val cache = RawConfigCache(dirs)

        cache.write("""{"v":1}""")
        cache.write("""{"v":2}""")

        assertEquals("""{"v":2}""", cache.read())
        assertFalse(Files.exists(dirs.configDir.resolve("config.json.tmp")))
    }

    @Test
    fun `a crash between tmp write and rename leaves the old cache intact`(@TempDir tempDir: Path) {
        val dirs = TestAppDirectories(tempDir)
        val cache = RawConfigCache(dirs)

        // Establish an existing cache, as if from a prior successful run.
        cache.write("""{"v":"old"}""")

        // Simulate a process crash after the tmp file was written but before the atomic
        // rename executed: write garbage straight to the tmp path, bypassing the class.
        Files.createDirectories(dirs.configDir)
        Files.writeString(
            dirs.configDir.resolve("config.json.tmp"),
            "not even valid json, partially written",
            StandardCharsets.UTF_8,
        )

        // The cache readers only ever look at config.json, so the crash must not be visible.
        assertEquals("""{"v":"old"}""", cache.read())

        // A subsequent successful write recovers cleanly and cleans up the stale tmp file.
        cache.write("""{"v":"new"}""")
        assertEquals("""{"v":"new"}""", cache.read())
        assertFalse(Files.exists(dirs.configDir.resolve("config.json.tmp")))
    }
}
