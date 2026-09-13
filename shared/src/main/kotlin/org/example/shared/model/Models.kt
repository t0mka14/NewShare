package org.example.shared.model

import kotlinx.serialization.Serializable

/**
 * Semver-ish `major.minor.patch` version, comparable for the updater's "is the server version
 * newer than the locally installed one" check (§9). Deliberately narrow: no pre-release/build
 * metadata, since the update package's `version.json` and the server's `version` field are both
 * plain `x.y.z` strings per spec.
 */
@Serializable
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^(\d+)\.(\d+)\.(\d+)$""")

        /**
         * Parses a plain `x.y.z` string. Returns `null` for anything malformed (missing parts,
         * non-numeric parts, extra pre-release/build suffixes, leading `v`, etc.) — callers treat
         * a malformed *remote* version as "not newer" (§10.1, §11: invalid response ⇒ launch
         * existing app without updating) rather than throwing.
         */
        fun parse(raw: String): AppVersion? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return runCatching {
                AppVersion(major.toInt(), minor.toInt(), patch.toInt())
            }.getOrNull()
        }
    }
}

/**
 * Response body of `GET /api/version/latest?platform=<platform>` (§9 pt 1).
 *
 * A **release** is a set of independently downloadable **components**. The updater compares each
 * component against what it recorded at install time and fetches only the ones that differ, so a
 * release that only changes the bundled ffmpeg does not re-download the 77 MB app package.
 *
 * Transient — never persisted, so no `version` schema field per §12 (that requirement applies to
 * persisted file formats; this is a stateless API response).
 */
@Serializable
data class VersionCheckResponse(
    /** Plain `x.y.z`, ordered: the updater ignores a release that is not newer than the installed
     * one (§9 pt 2, §10.1 "malformed versions treated as not-newer"). */
    val release: String,
    val components: List<ComponentDescriptor> = emptyList(),
)

/**
 * One replaceable piece of the install.
 *
 * There is deliberately **no version field**: [checksum] already identifies the artifact exactly,
 * and the updater records it at install time, so "is this stale?" is one string comparison with no
 * version semantics to get wrong. It also sidesteps the fact that the pieces have no common
 * versioning scheme — the JDK, ffmpeg (`7.1-1.5.11`) and the app share no comparable format.
 */
@Serializable
data class ComponentDescriptor(
    /** Stable identity used as the ledger key and in logs, e.g. `app`, `runtime`, `ffmpeg`. */
    val id: String,
    /** Install-dir-relative directory this component owns wholesale, e.g. `native/ffmpeg/linux-x86_64`.
     * Validated by the updater before use — it is a path from the network. */
    val target: String,
    val url: String,
    /** SHA-256 of the artifact at [url], lowercase hex. Both the integrity check and the identity. */
    val checksum: String,
)

/**
 * On-disk `app/version.json` written inside the update package and read by the updater to know
 * the currently installed app version (§9 pt 2). Persisted ⇒ carries a schema `version` field
 * per §12, separate from the semantic `appVersion` string.
 */
@Serializable
data class AppVersionFile(
    val version: Int = 1,
    val appVersion: String
)
