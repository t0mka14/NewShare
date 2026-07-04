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

/** Response body of `GET /api/version/latest` (§9 pt 1). Transient — never persisted, so no
 * `version` schema field per §12 (that requirement applies to persisted/transferred file
 * formats defined in this document; this is a stateless API response). */
@Serializable
data class VersionCheckResponse(
    val version: String,
    val downloadUrl: String,
    val checksum: String
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
