package org.example.app.infrastructure

import org.example.app.domain.AppDirectories
import java.nio.file.Files
import java.nio.file.Path

/**
 * Filesystem layout per §8.2, rooted at [dataRoot] (default: `<working dir>/data`,
 * matching the §9 install layout `<install_dir>/data/`).
 */
class DefaultAppDirectories(
    override val dataRoot: Path = Path.of(System.getProperty("user.dir"), "data"),
) : AppDirectories {

    override val configDir: Path = dataRoot.resolve("config")
    override val sessionsDir: Path = dataRoot.resolve("sessions")
    override val logsDir: Path = dataRoot.resolve("logs")

    init {
        listOf(configDir, sessionsDir, logsDir).forEach(Files::createDirectories)
    }

    override fun sessionDir(folderName: String): Path = sessionsDir.resolve(folderName)
}
