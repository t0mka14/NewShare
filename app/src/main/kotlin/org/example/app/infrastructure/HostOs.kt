package org.example.app.infrastructure

import java.util.Locale

/**
 * Host operating system. `:app` deliberately has almost no OS branching — audio, persistence
 * and networking are all portable — but camera capture and PTZ are not, so the checks are
 * collected here rather than scattered as ad-hoc `os.name` reads.
 */
enum class HostOs {
    WINDOWS,
    MAC,

    /**
     * Recognised so the native payload resolves for development and CI on Linux. Camera
     * *capture* is still Windows/macOS only — see [org.example.app.infrastructure.video.PlatformCaptureInput].
     */
    LINUX,
    OTHER;

    companion object {
        val current: HostOs = from(System.getProperty("os.name").orEmpty())

        val isWindows: Boolean get() = current == WINDOWS
        val isMac: Boolean get() = current == MAC

        internal fun from(osName: String): HostOs {
            val normalized = osName.lowercase(Locale.ROOT)
            return when {
                normalized.contains("win") -> WINDOWS
                normalized.contains("mac") || normalized.contains("darwin") -> MAC
                normalized.contains("linux") || normalized.contains("nux") -> LINUX
                else -> OTHER
            }
        }

        /** Directory name of the shipped native payload for this host (§9 `native/`). */
        val nativeDirName: String
            get() = when (current) {
                WINDOWS -> "windows-x86_64"
                MAC -> if (System.getProperty("os.arch").orEmpty().contains("aarch64")) {
                    "macosx-arm64"
                } else {
                    "macosx-x86_64"
                }
                LINUX -> "linux-x86_64"
                OTHER -> "unsupported"
            }
    }
}
