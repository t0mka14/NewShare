package org.example.updater

import org.example.shared.model.VersionCheckResponse

/** Outcome of a version check (§9 pt 1). Modeled as a result rather than a throwing call so
 * [Updater] never needs try/catch around network + parsing: "server unreachable" and "response
 * didn't parse" both collapse to the same [Unreachable] handling (§9 pt 4, §11 "version check
 * unreachable ⇒ proceed without updating"). */
sealed interface VersionCheckResult {
    data class Available(val response: VersionCheckResponse) : VersionCheckResult
    data object Unreachable : VersionCheckResult
}

fun interface VersionFetcher {
    fun fetchLatest(): VersionCheckResult
}
