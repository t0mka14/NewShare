package org.example.app.domain.session

import org.example.app.domain.participant.PatientCodeComposer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Builds and parses the session directory name `yyyy-MM-dd_PatientCode_SessionId` (§8.2). The
 * date is clinic-local (JSON timestamps inside the session remain UTC); `PatientCode` is
 * sanitized to `[A-Za-z0-9_-]`; `SessionId` comes from [org.example.app.domain.IdGenerator]
 * and is assumed underscore-free and fixed-content (it is treated as an opaque suffix by
 * [extractPatientCode], not re-derived from the folder name).
 */
object SessionFolderNaming {
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun build(clinicLocalDate: LocalDate, patientCode: String, sessionId: String): String =
        "${clinicLocalDate.format(DATE_FORMAT)}_${PatientCodeComposer.sanitize(patientCode)}_$sessionId"

    /**
     * Recovers the `PatientCode` segment of [folderName] given the [sessionId] already known
     * from that session's `examination.json` (the authoritative source, §8.10) — this avoids
     * guessing where the patient-code segment ends when it legitimately contains `_`
     * characters. Assumes the standard 10-character ISO local date prefix (`yyyy-MM-dd`).
     */
    fun extractPatientCode(folderName: String, sessionId: String): String {
        val afterDate = folderName.drop(DATE_PREFIX_LENGTH) // "yyyy-MM-dd_" is 11 chars
        return afterDate.removeSuffix("_$sessionId")
    }

    private const val DATE_PREFIX_LENGTH = 11
}
