package org.example.app.domain.upload

import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

/**
 * Outcome of one [UploadApi.uploadSession] attempt (§8.9). [Success]/[Rejected] are terminal
 * (server actually answered); [NetworkFailure] and [ServerError] are transient. Either way,
 * upload is manual-only (§13 decision 34): a non-[Success] outcome only ever transitions the
 * session to `Failed` in `upload_status.json` (§5.4) — there is no background retry. The
 * examiner retries by pressing Upload again on the upload screen, which lists `Failed`
 * sessions alongside `NotUploaded` ones (see `EligibleUploadsQuery`).
 */
sealed interface UploadResult {
    /** The server accepted the `sessionId` (§8.9 idempotency: it can never accept it again).
     * [serverResponse] is kept verbatim, opaque (server response shape is not yet specified,
     * §13 open question 1). */
    data class Success(val serverResponse: JsonElement?) : UploadResult

    /** The server explicitly refused the upload for a reason other than a transient failure
     * (e.g. unknown/disabled installation ID, already-uploaded `sessionId`) — [reason] is a
     * short, localizable-by-the-caller summary, not a raw server message (§11). */
    data class Rejected(val reason: String) : UploadResult

    /** No response could be obtained at all (timeout, DNS, connection refused, TLS failure, …). */
    object NetworkFailure : UploadResult

    /** The server responded but with a non-2xx [status] that isn't a semantic rejection. */
    data class ServerError(val status: Int) : UploadResult
}

/**
 * Uploads one session's processing archive (§5.4, §8.9). Implemented with Ktor by the
 * integration engineer; this interface is the sole contract `UploadSessionUseCase` depends on.
 * The upload payload is the ZIP itself plus three form fields — nothing else is ever sent
 * (§8.9: "the master is inside the ZIP, once").
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
        onProgress: (Float) -> Unit,
    ): UploadResult
}
