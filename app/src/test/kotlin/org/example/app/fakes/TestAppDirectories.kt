package org.example.app.fakes

import org.example.app.domain.AppDirectories
import java.nio.file.Files
import java.nio.file.Path

/**
 * [AppDirectories] rooted at a caller-supplied temp directory (pair with JUnit 5's
 * `@TempDir` in the test). Layout mirrors §8.2. Directories are created eagerly so
 * repositories/use cases under test never have to worry about missing parents.
 */
class TestAppDirectories(root: Path) : AppDirectories {
    override val dataRoot: Path = root.createDirs()
    override val configDir: Path = root.resolve("config").createDirs()
    override val sessionsDir: Path = root.resolve("sessions").createDirs()
    override val uploadQueueDir: Path = root.resolve("upload_queue").createDirs()
    override val logsDir: Path = root.resolve("logs").createDirs()

    override fun sessionDir(folderName: String): Path = sessionsDir.resolve(folderName).createDirs()

    private companion object {
        fun Path.createDirs(): Path = also { Files.createDirectories(it) }
    }
}
