package org.example.updater

import java.nio.file.Files
import java.nio.file.Path

fun interface AppLauncher {
    /** Starts the app. Must return immediately without waiting for the app process to exit
     * (§9 pt 3: "launch ... and exit"). */
    fun launch(layout: InstallLayout)
}

/**
 * Launches `runtime/bin/java -jar app/app.jar` (§9 pt 3, the bundled jlink JRE). Falls back to
 * the updater's own JVM's `java` binary when `runtime/` is absent — the dev/unpackaged layout,
 * since packaging the bundled runtime is out of scope for this change (§9 packaging is a
 * separate concern).
 */
class ProcessAppLauncher(private val log: UpdaterLog) : AppLauncher {

    override fun launch(layout: InstallLayout) {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val javaExecutable = resolveJavaExecutable(layout.runtimeDir, isWindows, System.getProperty("java.home"), log)
        val appJar = layout.appDir.resolve("app.jar")
        log.info("Launching $javaExecutable -jar $appJar")
        ProcessBuilder(javaExecutable.toString(), "-jar", appJar.toString())
            .directory(layout.installDir.toFile())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        // Deliberately not waiting on the process — the updater's job ends here (§9 pt 3).
    }

    companion object {
        /** Pure path-selection logic, factored out so it's unit-testable without spawning a
         * process. */
        fun resolveJavaExecutable(runtimeDir: Path, isWindows: Boolean, javaHome: String, log: UpdaterLog? = null): Path {
            val binName = if (isWindows) "java.exe" else "java"
            val bundled = runtimeDir.resolve("bin").resolve(binName)
            if (Files.isRegularFile(bundled)) return bundled
            log?.warn("Bundled runtime not found at $bundled; falling back to current JVM's java")
            return Path.of(javaHome, "bin", binName)
        }
    }
}
