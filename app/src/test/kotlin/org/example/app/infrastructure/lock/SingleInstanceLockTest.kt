package org.example.app.infrastructure.lock

import org.example.app.domain.AppDirectories
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
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

class SingleInstanceLockTest {

    @Test
    fun `acquire succeeds when no other holder exists`(@TempDir tempDir: Path) {
        val lock = SingleInstanceLock(TestAppDirectories(tempDir))

        assertTrue(lock.acquire())

        lock.release()
    }

    @Test
    fun `a second lock on the same data dir fails to acquire while the first holds it`(@TempDir tempDir: Path) {
        val dirs = TestAppDirectories(tempDir)
        val first = SingleInstanceLock(dirs)
        val second = SingleInstanceLock(dirs)

        assertTrue(first.acquire())
        assertFalse(second.acquire(), "second instance must not acquire while the first holds the lock")

        first.release()
    }

    @Test
    fun `acquire succeeds again via a fresh channel after the first holder releases`(@TempDir tempDir: Path) {
        val dirs = TestAppDirectories(tempDir)
        val first = SingleInstanceLock(dirs)
        val second = SingleInstanceLock(dirs)

        assertTrue(first.acquire())
        first.release()

        assertTrue(second.acquire(), "lock must be re-acquirable by a separate instance once released")

        second.release()
    }

    @Test
    fun `acquire is idempotent on the same instance`(@TempDir tempDir: Path) {
        val lock = SingleInstanceLock(TestAppDirectories(tempDir))

        assertTrue(lock.acquire())
        assertTrue(lock.acquire())

        lock.release()
    }

    @Test
    fun `release is safe to call multiple times, including without a prior acquire`(@TempDir tempDir: Path) {
        val lock = SingleInstanceLock(TestAppDirectories(tempDir))

        lock.release()
        lock.release()

        assertTrue(lock.acquire())
        lock.release()
        lock.release()
    }
}
