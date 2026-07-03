package org.example.app.domain.upload

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** `metadata/upload_status.json.status` (§8.9). `Uploaded` is terminal — a successfully
 * uploaded session can never be uploaded again. */
@Serializable
enum class UploadStatusValue { NotUploaded, Uploading, Uploaded, Failed }

/** One `attempts[]` entry (§8.10). `outcome` is a short human-readable summary (e.g. an
 * `UploadError` variant name), not a stack trace (§11). */
@Serializable
data class UploadAttempt(val at: String, val outcome: String)

/**
 * `metadata/upload_status.json` (§5.4, §8.9, §8.10) — the single authority for a session's
 * upload state; `upload_queue/queue.json` is only a derived index rebuilt from this (§5.4).
 * `serverResponse` is an opaque JSON blob (shape not yet specified server-side, §13 open
 * question 1) kept verbatim for diagnostics.
 */
@Serializable
data class UploadStatus(
    val version: Int = 1,
    val status: UploadStatusValue = UploadStatusValue.NotUploaded,
    val zipSha256: String? = null,
    val uploadedAt: String? = null,
    val serverResponse: JsonElement? = null,
    val attempts: List<UploadAttempt> = emptyList(),
    val attemptCount: Int = 0,
)
