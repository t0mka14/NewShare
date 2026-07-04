package org.example.app.domain.session

import org.example.app.domain.audio.CaptureFormat
import java.nio.file.Path

/** One row for the session browser / recovery scan (§8.11, §8.4). */
data class SessionSummary(
    val folderName: String,
    val sessionId: String,
    val patientCode: String,
    val startedAt: String,
    val recovered: Boolean,
    val processingStatus: ProcessingStatus?,
)

/** A master part file after crash-recovery finalization (§8.4). */
data class RecoveredMasterPart(val path: Path, val frameCount: Long)

/**
 * Session metadata + directory-layout ownership (§5.4, §8.2, §8.10). Every method resolves
 * paths internally via `AppDirectories`; callers pass only the session's folder name (never a
 * raw [Path] they built themselves).
 *
 * `participant.json` is written once at session start and never updated again (§8.1/§8.10).
 * `examination.json` uses the same [writeExamination] for both its initial write and every
 * later incremental update — both go through the same atomic tmp+rename so a crash never
 * leaves a torn file (§8.10 write lifecycle).
 */
interface SessionRepository {
    fun sessionDir(folderName: String): Path
    fun masterDir(folderName: String): Path
    fun clipsDir(folderName: String): Path

    /** `archive/` — home of the processing ZIP (§8.2, §8.8); [SessionArchivePaths] resolves
     * the exact `<PatientCode>_<SessionId>.zip` file name within it. */
    fun archiveDir(folderName: String): Path

    /** True if the session has at least one processing archive `*.zip` on disk (§8.8) — the
     * precondition `UploadSessionUseCase` checks before it will attempt an upload. */
    fun archiveExists(folderName: String): Boolean

    /** `master/session_master.wav` — the canonical part-1 master file path. */
    fun defaultMasterFile(folderName: String): Path

    /** Creates the session directory tree (`master/`, `clips/`, `metadata/`,
     * `waveform_cache/`) and returns the session root. */
    fun createSessionDirectory(folderName: String): Path

    fun writeParticipant(folderName: String, participant: ParticipantRecord)
    fun readParticipant(folderName: String): ParticipantRecord?

    fun writeExamination(folderName: String, examination: Examination)
    fun readExamination(folderName: String): Examination?

    /** `task_configuration_snapshot.json` — the config JSON is persisted verbatim
     * (pass-through, no re-parse/re-encode) so later config pushes never affect a
     * recorded session (§6.1 pt. 5). */
    fun writeConfigSnapshot(folderName: String, rawJson: String)
    fun readConfigSnapshot(folderName: String): String?

    /** All session folder names currently on disk, for recovery scans and the browser. */
    fun listSessionFolderNames(): List<String>

    /** [listSessionFolderNames] joined with each session's `examination.json`. Sessions
     * whose `examination.json` is missing or unreadable are omitted (never thrown) so one
     * corrupt session cannot break the whole listing. */
    fun listSessions(): List<SessionSummary>

    /**
     * `*.partial.wav` staging files left under `master/` by a crash (§8.4), in ascending
     * part order. Empty if the master was cleanly finalized, or for a no-master session.
     */
    fun findPartialMasterFiles(folderName: String): List<Path>

    /**
     * Rebuilds [partialFile]'s header from [format] + actual file length — the on-disk
     * header is never trusted as-is because a crash mid periodic-patch can leave it torn
     * (§8.4) — then atomically renames it to drop the `.partial` suffix.
     */
    fun finalizePartialMasterFile(partialFile: Path, format: CaptureFormat): RecoveredMasterPart

    /**
     * Frame count of the highest-numbered, already-finalized (non-partial) master part, for
     * the case where the master finished cleanly but the timeline log was left uncompacted
     * by a crash between finalize and compaction. `null` if no master part exists.
     */
    fun latestFinalizedMasterPartFrames(folderName: String, format: CaptureFormat): Long?
}
