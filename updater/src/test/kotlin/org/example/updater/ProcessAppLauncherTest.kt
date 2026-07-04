package org.example.updater

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Pure path-selection logic behind launching the app (§9 pt 3), tested without spawning any
 * process. */
class ProcessAppLauncherTest {

    @Test
    fun `prefers the bundled runtime java when present`(@TempDir tempDir: Path) {
        val runtimeDir = tempDir.resolve("runtime")
        Files.createDirectories(runtimeDir.resolve("bin"))
        val bundledJava = runtimeDir.resolve("bin/java")
        Files.writeString(bundledJava, "#!/bin/sh")

        val resolved = ProcessAppLauncher.resolveJavaExecutable(
            runtimeDir = runtimeDir,
            isWindows = false,
            javaHome = "/usr/lib/jvm/fallback",
        )

        assertEquals(bundledJava, resolved)
    }

    @Test
    fun `uses java-exe on windows`(@TempDir tempDir: Path) {
        val runtimeDir = tempDir.resolve("runtime")
        Files.createDirectories(runtimeDir.resolve("bin"))
        val bundledJava = runtimeDir.resolve("bin/java.exe")
        Files.writeString(bundledJava, "stub")

        val resolved = ProcessAppLauncher.resolveJavaExecutable(
            runtimeDir = runtimeDir,
            isWindows = true,
            javaHome = "C:\\fallback",
        )

        assertEquals(bundledJava, resolved)
    }

    @Test
    fun `falls back to the current JVM's java when runtime is missing`(@TempDir tempDir: Path) {
        val runtimeDir = tempDir.resolve("runtime") // never created — dev/unpackaged layout

        val resolved = ProcessAppLauncher.resolveJavaExecutable(
            runtimeDir = runtimeDir,
            isWindows = false,
            javaHome = "/usr/lib/jvm/fallback",
        )

        assertEquals(Path.of("/usr/lib/jvm/fallback/bin/java"), resolved)
    }
}
