package org.example.updater

/**
 * The platform token this install asks the server for.
 *
 * Deliberately the same vocabulary as `:app`'s `HostOs.nativeDirName` — `linux-x86_64`,
 * `windows-x86_64`, `macosx-x86_64`, `macosx-arm64` — because for the ffmpeg component the token
 * *is* the target directory name that `FfmpegBinaryLocator` looks under at runtime. Duplicated
 * rather than shared: `:updater` stays dependency-light (§9) and does not depend on `:app`.
 *
 * In a GraalVM native image `os.name`/`os.arch` report the values the binary was built for, which
 * is what we want — the binary is per-platform anyway.
 */
object HostPlatform {
    val current: String by lazy { of(System.getProperty("os.name"), System.getProperty("os.arch")) }

    fun of(osName: String?, osArch: String?): String {
        val os = osName.orEmpty().lowercase()
        val arch = osArch.orEmpty().lowercase()
        return when {
            os.contains("win") -> "windows-x86_64"
            os.contains("mac") || os.contains("darwin") ->
                if (arch.contains("aarch64") || arch.contains("arm")) "macosx-arm64" else "macosx-x86_64"
            else -> "linux-x86_64"
        }
    }
}
