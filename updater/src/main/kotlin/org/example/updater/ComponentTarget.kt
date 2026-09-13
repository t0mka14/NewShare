package org.example.updater

import java.nio.file.Files
import java.nio.file.Path

/**
 * Validates the install-dir-relative `target` a [org.example.shared.model.ComponentDescriptor]
 * carries, before anything is moved or deleted through it.
 *
 * This is a path that arrived over the network and is used to *replace a directory wholesale*, so
 * it gets the same treatment as the zip-slip guard in [Replacer] — kept as a separate pure object
 * so the rules are table-testable without touching a filesystem (§10.1's approach for
 * [BackupDecision]).
 */
object ComponentTarget {
    /** Never writable by an update: the updater must not touch `data/` at all (§9 pt 5), and the
     * rest are the updater's own control files. */
    private val FORBIDDEN_ROOTS = setOf("data")
    private val FORBIDDEN_NAMES = setOf(
        "updater.properties", "update_pending.json", "installed.json", "updater.log", "update-staging",
    )

    /** Returns the absolute target, or null when the descriptor must be rejected. */
    fun resolve(installDir: Path, target: String): Path? {
        if (target.isBlank()) return null
        val raw = runCatching { Path.of(target) }.getOrNull() ?: return null
        if (raw.isAbsolute || raw.root != null) return null
        // Reject anything that is not already in canonical relative form: "..", ".", "a//b".
        if (raw.normalize() != raw) return null
        if (raw.any { it.toString() == ".." || it.toString() == "." }) return null
        if (raw.getName(0).toString() in FORBIDDEN_ROOTS) return null
        if (target in FORBIDDEN_NAMES || raw.fileName.toString() in FORBIDDEN_NAMES) return null

        val resolved = installDir.resolve(raw).normalize()
        if (resolved == installDir.normalize()) return null
        if (!resolved.startsWith(installDir.normalize())) return null
        // Textual containment is not enough: an existing symlink anywhere on the path could point
        // outside the install. Check the deepest ancestor that actually exists.
        var probe: Path? = resolved
        while (probe != null && !Files.exists(probe)) probe = probe.parent
        if (probe != null) {
            val realProbe = runCatching { probe.toRealPath() }.getOrNull() ?: return null
            val realInstall = runCatching { installDir.toRealPath() }.getOrNull() ?: return null
            if (!realProbe.startsWith(realInstall)) return null
        }
        return resolved
    }
}
