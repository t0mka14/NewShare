package org.example.app.domain.upload

import kotlinx.coroutines.withContext
import org.example.app.domain.Clock
import org.example.app.domain.CoroutineDispatchers
import org.example.app.domain.session.SessionArchivePaths
import org.example.app.domain.session.SessionRepository

/**
 * §5.5/§8.9 `UploadSessionUseCase`: uploads one already-processed session's archive ZIP,
 * driving `metadata/upload_status.json` through its state machine
 * (`NotUploaded`/`Failed` → `Uploading` → `Uploaded`/`Failed`, §8.9/§8.10). The ZIP's SHA-256 is
 * recomputed fresh on every call (never trusted from a previous run — a session could have been
 * reprocessed since, §8.8) and passed to [UploadApi] alongside the form fields; nothing else is
 * ever sent (§8.9).
 *
 * Idempotency (§8.9 decision 11): a session whose status is already `Uploaded` is refused
 * before any network call and before touching `upload_status.json` at all — `Uploaded` is
 * terminal in the app, matching the server's own one-time acceptance of a `sessionId`.
 */
class UploadSessionUseCase(
    private val sessionRepository: SessionRepository,
    private val uploadStatusRepository: UploadStatusRepository,
    private val uploadApi: UploadApi,
    private val fileHashService: FileHashService,
    private val clock: Clock,
    private val dispatchers: CoroutineDispatchers,
) {
    sealed interface Outcome {
        data class Success(val status: UploadStatus) : Outcome
        data class Failed(val status: UploadStatus, val reason: String) : Outcome

        /** Refused before any [UploadApi] call or `upload_status.json` write: no processed
         * archive exists yet, or the session is already `Uploaded` (terminal, §8.9). */
        data class Refused(val reason: String) : Outcome
    }

    suspend fun upload(folderName: String, onProgress: (Float) -> Unit = {}): Outcome = withContext(dispatchers.io) {
        val examination = sessionRepository.readExamination(folderName)
            ?: return@withContext Outcome.Refused("examination.json missing for session")

        if (!sessionRepository.archiveExists(folderName)) {
            return@withContext Outcome.Refused("no processing archive — run ProcessSessionUseCase first")
        }

        val current = uploadStatusRepository.read(folderName) ?: UploadStatus()
        if (current.status == UploadStatusValue.Uploaded) {
            return@withContext Outcome.Refused("session already uploaded (terminal, §8.9)")
        }

        val uploading = current.copy(status = UploadStatusValue.Uploading)
        uploadStatusRepository.write(folderName, uploading)

        val zipFile = SessionArchivePaths.zipFile(sessionRepository, folderName, examination)
        val zipSha256 = fileHashService.sha256(zipFile)

        val result = try {
            uploadApi.uploadSession(
                zip = zipFile,
                installationId = examination.installationId,
                sessionId = examination.sessionId,
                zipSha256 = zipSha256,
                onProgress = onProgress,
            )
        } catch (e: Exception) {
            UploadResult.NetworkFailure
        }

        val attemptAt = clock.now().toString()
        val outcome = when (result) {
            is UploadResult.Success -> {
                val status = uploading.recordAttempt(attemptAt, "Success").copy(
                    status = UploadStatusValue.Uploaded,
                    zipSha256 = zipSha256,
                    uploadedAt = attemptAt,
                    serverResponse = result.serverResponse,
                )
                Outcome.Success(status)
            }
            is UploadResult.Rejected -> failedOutcome(uploading, attemptAt, zipSha256, "Rejected: ${result.reason}", result.reason)
            UploadResult.NetworkFailure -> failedOutcome(uploading, attemptAt, zipSha256, "NetworkFailure", "network failure")
            is UploadResult.ServerError -> failedOutcome(
                uploading,
                attemptAt,
                zipSha256,
                "ServerError(${result.status})",
                "server error ${result.status}",
            )
        }

        val finalStatus = when (outcome) {
            is Outcome.Success -> outcome.status
            is Outcome.Failed -> outcome.status
            is Outcome.Refused -> uploading // unreachable here, but keeps `when` exhaustive
        }
        uploadStatusRepository.write(folderName, finalStatus)
        outcome
    }

    private fun failedOutcome(
        uploading: UploadStatus,
        attemptAt: String,
        zipSha256: String,
        attemptOutcome: String,
        reason: String,
    ): Outcome.Failed {
        val status = uploading.recordAttempt(attemptAt, attemptOutcome).copy(
            status = UploadStatusValue.Failed,
            zipSha256 = zipSha256,
        )
        return Outcome.Failed(status, reason)
    }

    private fun UploadStatus.recordAttempt(at: String, outcome: String): UploadStatus =
        copy(attempts = attempts + UploadAttempt(at, outcome), attemptCount = attemptCount + 1)
}
