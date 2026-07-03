package org.example.app.domain

import java.nio.file.Path

/**
 * The only source of filesystem locations (§5.3). Domain and components never
 * build paths themselves; every persisted file lives under one of these roots.
 * Layout per §8.2.
 */
interface AppDirectories {
    val dataRoot: Path
    val configDir: Path
    val sessionsDir: Path
    val uploadQueueDir: Path
    val logsDir: Path

    /** Directory of one session, by its folder name (`yyyy-MM-dd_PatientCode_SessionId`, §8.2). */
    fun sessionDir(folderName: String): Path
}
