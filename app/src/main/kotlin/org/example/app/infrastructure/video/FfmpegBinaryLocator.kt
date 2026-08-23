package org.example.app.infrastructure.video

import io.github.oshai.kotlinlogging.KotlinLogging
import org.example.app.infrastructure.HostOs
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Finds the bundled ffmpeg executable.
 *
 * ffmpeg ships as a side-loaded payload under `<install_dir>/native/ffmpeg/<platform>/`
 * rather than inside `app.jar`, so a routine app update does not re-download ~25 MB of
 * unchanged native code (§9 install layout).
 *
 * Resolution order: the explicit override property, then the install payload, then the
 * Gradle unpack directory used by `./gradlew :app:run` and the preview harnesses, then
 * whatever is on `PATH`. A miss is reported as
 * [org.example.app.domain.video.VideoError.CaptureBackendUnavailable] by the caller —
 * never a dialog and never a throw from an initializer, which is how the original app
 * handled a missing ffmpeg.
 */
class FfmpegBinaryLocator(
    private val installRoot: Path = Path.of(System.getProperty("user.dir")),
) {
    /** The executable, or null when no usable ffmpeg was found. */
    fun locate(): Path? = cached ?: resolve().also { cached = it }

    @Volatile private var cached: Path? = null

    private fun resolve(): Path? {
        val executable = if (HostOs.isWindows) "ffmpeg.exe" else "ffmpeg"

        System.getProperty(OVERRIDE_PROPERTY)?.takeIf { it.isNotBlank() }?.let { override ->
            val base = Path.of(override)
            // Accept the executable itself, its directory, or the parent holding one
            // directory per platform (which is how the Gradle unpack task lays it out).
            val candidates = if (Files.isDirectory(base)) {
                listOf(base.resolve(executable), base.resolve(HostOs.nativeDirName).resolve(executable))
            } else {
                listOf(base)
            }
            candidates.firstOrNull(Files::isExecutable)?.let { return it }
            logger.warn { "$OVERRIDE_PROPERTY=$override yielded no executable ffmpeg; ignoring" }
        }

        val candidates = listOf(
            installRoot.resolve("native").resolve("ffmpeg").resolve(HostOs.nativeDirName).resolve(executable),
            installRoot.resolve("app").resolve("build").resolve("native").resolve("ffmpeg")
                .resolve(HostOs.nativeDirName).resolve(executable),
            installRoot.resolve("build").resolve("native").resolve("ffmpeg")
                .resolve(HostOs.nativeDirName).resolve(executable),
        )
        candidates.firstOrNull(Files::isExecutable)?.let { return it }

        onPath(executable)?.let {
            logger.info { "using ffmpeg from PATH at $it; the bundled native payload was not found" }
            return it
        }

        logger.warn { "no ffmpeg found; looked at ${candidates.joinToString()} and on PATH" }
        return null
    }

    private fun onPath(executable: String): Path? =
        System.getenv("PATH").orEmpty()
            .split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { Path.of(it).resolve(executable) }
            .firstOrNull(Files::isExecutable)

    private companion object {
        const val OVERRIDE_PROPERTY = "share.ffmpeg.path"
    }
}
