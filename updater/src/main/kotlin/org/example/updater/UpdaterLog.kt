package org.example.updater

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

/**
 * A small plain-text log at `<install_dir>/updater.log` — deliberately not `data/logs/` (the
 * updater must never touch `data/`, §9 pt 5) and deliberately not Logback/kotlin-logging (an
 * extra dependency the updater doesn't need; §9 keeps this module dependency-light).
 */
class UpdaterLog(private val logFile: Path) {
    fun info(message: String) = write("INFO", message)
    fun warn(message: String) = write("WARN", message)

    fun error(message: String, throwable: Throwable? = null) =
        write("ERROR", if (throwable != null) "$message: ${throwable}" else message)

    private fun write(level: String, message: String) {
        val line = "${Instant.now()} [$level] $message\n"
        Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
}
