package org.example.app.infrastructure.logging

import java.nio.file.Path

/**
 * The directory Logback writes to, resolved the way `logback.xml` does
 * (`${LOG_PATH:-${user.dir}/data/logs}`): system property `LOG_PATH`, else the environment
 * variable, else `<user.dir>/data/logs`.
 */
object LogDirectory {
    fun resolve(
        property: (String) -> String? = System::getProperty,
        env: (String) -> String? = System::getenv,
        userDir: String = System.getProperty("user.dir"),
    ): Path {
        val configured = property("LOG_PATH")?.takeIf { it.isNotBlank() }
            ?: env("LOG_PATH")?.takeIf { it.isNotBlank() }
        return configured?.let { Path.of(it) } ?: Path.of(userDir, "data", "logs")
    }
}
