package org.example.updater

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Resolves the two pieces of updater configuration that vary per deployment: the install root
 * and the version-check endpoint. Both are documented single configuration points so the pending
 * server contract (§13 open q1) can land without touching call sites.
 *
 * Endpoint precedence: CLI arg (`--endpoint=<url>`) > `updater.properties` (`endpoint=<url>`)
 * placed beside the updater executable in `<install_dir>` > none. "None" is treated exactly like
 * "unreachable" by [Updater] (§9 pt 4: proceed to launch the existing app without updating).
 */
object UpdaterConfig {
    private const val PROPERTIES_FILE_NAME = "updater.properties"
    private const val ENDPOINT_KEY = "endpoint"
    private const val ENDPOINT_ARG_PREFIX = "--endpoint="
    private const val INSTALL_DIR_ARG_PREFIX = "--install-dir="

    fun resolveEndpoint(installDir: Path, args: Array<String>): String? {
        args.firstOrNull { it.startsWith(ENDPOINT_ARG_PREFIX) }
            ?.removePrefix(ENDPOINT_ARG_PREFIX)
            ?.let { return it }

        val propertiesFile = installDir.resolve(PROPERTIES_FILE_NAME)
        if (!Files.isRegularFile(propertiesFile)) return null
        val properties = Properties()
        Files.newInputStream(propertiesFile).use { properties.load(it) }
        return properties.getProperty(ENDPOINT_KEY)
    }

    /** Defaults to the process's working directory, which is `<install_dir>` per the §9 layout
     * (the updater is launched from there). `--install-dir=` overrides it, mainly for tests and
     * for a dev-mode where the updater jar isn't actually sitting in the install root. */
    fun resolveInstallDir(args: Array<String>): Path {
        val fromArgs = args.firstOrNull { it.startsWith(INSTALL_DIR_ARG_PREFIX) }
            ?.removePrefix(INSTALL_DIR_ARG_PREFIX)
        return if (fromArgs != null) Path.of(fromArgs) else Path.of("").toAbsolutePath()
    }
}
