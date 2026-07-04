package org.example.app.domain.session

import java.nio.file.Path

/**
 * The one deterministic `archive/<PatientCode>_<SessionId>.zip` path for a session (§8.2,
 * §8.8) — shared by [ProcessSessionUseCase] (which writes it) and
 * `org.example.app.domain.upload.UploadSessionUseCase` (which reads it), so the two use cases
 * can never disagree on where the archive lives.
 */
object SessionArchivePaths {
    fun zipFileName(patientCode: String, sessionId: String): String = "${patientCode}_$sessionId.zip"

    fun zipFile(sessionRepository: SessionRepository, folderName: String, examination: Examination): Path {
        val patientCode = SessionFolderNaming.extractPatientCode(folderName, examination.sessionId)
        return sessionRepository.archiveDir(folderName).resolve(zipFileName(patientCode, examination.sessionId))
    }
}
