package org.example.app.domain.upload

import org.example.app.domain.session.SessionRepository

/**
 * One row for the upload screen's session list (§8.9, §13 decision 34): a processed session
 * that is not yet successfully uploaded. [failureReason] carries the last attempt's outcome
 * summary (§8.10 `attempts[]`) so the UI can show it next to the session when [status] is
 * [UploadStatusValue.Failed]; `null` for a session that has never been attempted.
 */
data class EligibleUpload(
    val folderName: String,
    val sessionId: String,
    val patientCode: String,
    val status: UploadStatusValue,
    val failureReason: String?,
)

/**
 * §5.4/§8.9/§13 decision 34: upload is manual-only, so the upload screen's session list has no
 * persisted queue — it is computed on demand, directly from the two authorities session
 * metadata already has: [SessionRepository] (is there a processing archive?) and
 * [UploadStatusRepository] (`metadata/upload_status.json`, the single authority for upload
 * state, §5.4). A session is eligible while its archive exists and its status is
 * [UploadStatusValue.NotUploaded] or [UploadStatusValue.Failed] — `Uploaded` is terminal
 * (§8.9 decision 11) and therefore never listed again.
 *
 * §13 decision 35: a status of [UploadStatusValue.Uploading] here can only be the leftover
 * of an upload interrupted by a crash/kill (it is written just before the network call and
 * always overwritten when the attempt resolves), so it is reported as [UploadStatusValue.Failed]
 * with an "interrupted" reason and stays retryable — otherwise the session would be stuck
 * forever, since no background worker exists to reconcile it.
 */
class EligibleUploadsQuery(
    private val sessionRepository: SessionRepository,
    private val uploadStatusRepository: UploadStatusRepository,
) {
    fun eligibleSessions(): List<EligibleUpload> =
        sessionRepository.listSessions()
            .filter { sessionRepository.archiveExists(it.folderName) }
            .mapNotNull { summary ->
                val uploadStatus = uploadStatusRepository.read(summary.folderName) ?: UploadStatus()
                when (uploadStatus.status) {
                    UploadStatusValue.Uploaded -> null
                    UploadStatusValue.NotUploaded, UploadStatusValue.Failed -> EligibleUpload(
                        folderName = summary.folderName,
                        sessionId = summary.sessionId,
                        patientCode = summary.patientCode,
                        status = uploadStatus.status,
                        failureReason = uploadStatus.attempts.lastOrNull()?.outcome,
                    )
                    // Decision 35: leftover of a crash mid-upload — retryable, shown as failed.
                    UploadStatusValue.Uploading -> EligibleUpload(
                        folderName = summary.folderName,
                        sessionId = summary.sessionId,
                        patientCode = summary.patientCode,
                        status = UploadStatusValue.Failed,
                        failureReason = INTERRUPTED_REASON,
                    )
                }
            }

    companion object {
        /** Marker reason for decision-35 rows; the UI maps it to a localized message. */
        const val INTERRUPTED_REASON = "interrupted"
    }
}
