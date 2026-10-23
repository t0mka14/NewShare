package org.example.app.domain.upload

import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

/**
 * Outcome of one [UploadApi.uploadSession] attempt (§8.9). [Success]/[Rejected]/[Conflict] are
 * the server's actual answer; [NetworkFailure] and [ServerError] are transient. Either way,
 * upload is manual-only (§13 decision 34): a non-[Success] outcome only ever transitions the
 * session to `Failed` in `upload_status.json` (§5.4) — there is no background retry. The
 * examiner retries by pressing Upload again on the upload screen, which lists `Failed`
 * sessions alongside `NotUploaded` ones (see `EligibleUploadsQuery`).
 */
sealed interface UploadResult {
    /** The server accepted the `sessionId`. Also returned for a re-send of an already accepted
     * session with identical bytes, carrying the original receipt (§13 decision 62).
     * [serverResponse] is the receipt, kept verbatim. */
    data class Success(val serverResponse: JsonElement?) : UploadResult

    /** The server explicitly refused the upload for a reason other than a transient failure.
     * [reason] is a fixed category the caller localizes, never a raw server message (§11). */
    data class Rejected(val reason: RejectionReason) : UploadResult

    /** HTTP 409: this `sessionId` is already on the server **with different bytes**. Pressing
     * Upload again can never succeed; only an administrator can resolve it (§13 decision 62). */
    data object Conflict : UploadResult

    /** No response could be obtained at all (timeout, DNS, connection refused, TLS failure, …). */
    object NetworkFailure : UploadResult

    /** The server responded but with a non-2xx [status] that isn't a semantic rejection. */
    data class ServerError(val status: Int) : UploadResult
}

/** Why the server refused an upload ([UploadResult.Rejected]). */
enum class RejectionReason {
    /** HTTP 404: the device has no site (no or invalid token, or a token of another site). The
     * server keeps the upload in quarantine; once an administrator assigns the device, Upload
     * again gets 200 with the original receipt, so the session stays retryable. */
    DeviceNotRegistered,

    /** HTTP 401/403. */
    NotAuthorized,

    /** HTTP 413: over the server's body limit. */
    TooLarge,
}

/**
 * Uploads one session's processing archive (§5.4, §8.9). Implemented with Ktor by the
 * integration engineer; this interface is the sole contract `UploadSessionUseCase` depends on.
 * The upload payload is the ZIP itself plus the form fields below — nothing else is ever sent
 * (§8.9: "the master is inside the ZIP, once"). [siteToken] is `null` when none is set (the part
 * is then omitted); like the installation ID it is a credential and is never logged (§11).
 *
 * [onProgress] reports fractional upload progress (`0f..1f`) for the upload screen's
 * per-session bar (§8.9); implementations should call it at whatever granularity the transport
 * naturally provides (e.g. a Ktor multipart progress callback) — callers must tolerate it never
 * being called at all (e.g. a fake in tests).
 */
interface UploadApi {
    suspend fun uploadSession(
        zip: Path,
        installationId: String,
        sessionId: String,
        zipSha256: String,
        siteToken: String?,
        appVersion: String,
        onProgress: (Float) -> Unit,
    ): UploadResult
}
