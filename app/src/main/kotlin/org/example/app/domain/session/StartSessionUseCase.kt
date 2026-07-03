package org.example.app.domain.session

import org.example.app.domain.AppDirectories
import org.example.app.domain.Clock
import org.example.app.domain.DiskSpaceProvider
import org.example.app.domain.IdGenerator
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.config.PatientField
import org.example.app.domain.config.Protocol
import org.example.app.domain.participant.PatientCodeComposer
import org.example.app.domain.timeline.ProtocolExpansion
import org.example.app.domain.timeline.TaskInstanceExpander
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId

/**
 * §5.5 `StartSessionUseCase`: disk preflight, session directory creation, initial
 * `participant.json`/`examination.json`/config-snapshot persistence, protocol expansion.
 *
 * Deliberately does **not** drive `ContinuousSessionRecorder` — the caller (the Phase 2 UI's
 * SessionComponent) already knows the negotiated [CaptureFormat] (from calibration or a
 * pre-flight format probe) by the time it calls [start], and is responsible for actually
 * opening the recorder and calling `startWriting(result.masterFile)` once calibration is
 * confirmed (§8.1). This keeps the seam between session bookkeeping (this class) and live
 * audio (audio-engineer's) clean.
 */
class StartSessionUseCase(
    private val directories: AppDirectories,
    private val sessionRepository: SessionRepository,
    private val diskSpaceProvider: DiskSpaceProvider,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val clinicZone: ZoneId = ZoneId.systemDefault(),
) {
    data class Params(
        val installationId: String,
        val protocol: Protocol,
        val configVersion: String,
        /** Raw config JSON, persisted verbatim as `task_configuration_snapshot.json`
         * (§6.1 pt. 5) — never re-encoded from the parsed model. */
        val rawConfigJson: String,
        val patientFields: List<PatientField>,
        val participantFieldValues: Map<String, String>,
        /** `null` for a protocol with no VOCAL tasks (no-master session, §6.2). */
        val negotiatedFormat: CaptureFormat?,
    )

    data class Result(
        val sessionId: String,
        val folderName: String,
        val sessionDir: Path,
        /** `null` for no-master sessions — there is nothing for the caller to write to. */
        val masterFile: Path?,
        val expansion: ProtocolExpansion,
        val examination: Examination,
    )

    sealed interface Outcome {
        data class Started(val result: Result) : Outcome
        data class Rejected(val error: StorageError) : Outcome
    }

    fun start(params: Params): Outcome {
        val required = SessionDiskPreflight.requiredBytes(params.negotiatedFormat)
        val available = diskSpaceProvider.usableBytes(directories.sessionsDir)
        if (available < required) {
            return Outcome.Rejected(StorageError.InsufficientDiskSpace(required, available))
        }

        return try {
            Outcome.Started(createSession(params))
        } catch (e: java.io.IOException) {
            Outcome.Rejected(StorageError.WriteFailed(e.message ?: "session creation failed"))
        }
    }

    private fun createSession(params: Params): Result {
        val sessionId = idGenerator.newSessionId()
        val patientCode = PatientCodeComposer.compose(params.patientFields, params.participantFieldValues)
        val clinicLocalDate = LocalDate.ofInstant(clock.now(), clinicZone)
        val folderName = SessionFolderNaming.build(clinicLocalDate, patientCode, sessionId)
        val nowIso = clock.now().toString()

        val sessionDir = sessionRepository.createSessionDirectory(folderName)

        sessionRepository.writeParticipant(
            folderName,
            ParticipantRecord(fields = params.participantFieldValues, createdAt = nowIso),
        )
        sessionRepository.writeConfigSnapshot(folderName, params.rawConfigJson)

        val expansion = TaskInstanceExpander.expand(params.protocol.tasks)

        val examination = Examination(
            sessionId = sessionId,
            installationId = params.installationId,
            protocolName = params.protocol.name,
            configVersion = params.configVersion,
            startedAt = nowIso,
            captureFormat = params.negotiatedFormat,
        )
        sessionRepository.writeExamination(folderName, examination)

        val masterFile = params.negotiatedFormat?.let { sessionRepository.defaultMasterFile(folderName) }

        return Result(
            sessionId = sessionId,
            folderName = folderName,
            sessionDir = sessionDir,
            masterFile = masterFile,
            expansion = expansion,
            examination = examination,
        )
    }
}
