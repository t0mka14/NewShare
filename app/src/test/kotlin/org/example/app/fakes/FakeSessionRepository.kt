package org.example.app.fakes

import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.session.Examination
import org.example.app.domain.session.ParticipantRecord
import org.example.app.domain.session.RecoveredMasterPart
import org.example.app.domain.session.SessionFolderNaming
import org.example.app.domain.session.SessionRepository
import org.example.app.domain.session.SessionSummary
import java.nio.file.Path
import java.nio.file.Paths

/**
 * In-memory [SessionRepository] for fast use-case unit tests that don't need real file I/O
 * (§10.1) — round-trip persistence itself is covered by integration tests against
 * [org.example.app.infrastructure.persistence.JsonSessionRepository] in temp dirs (§10.2).
 * Paths returned are synthetic (never touch disk); [findPartialMasterFiles] /
 * [finalizePartialMasterFile] are driven by [seedPartialMasterFile] so recovery-use-case tests
 * can simulate a crash without a real recorder.
 */
class FakeSessionRepository : SessionRepository {
    private val root: Path = Paths.get("fake-sessions-root")

    private val createdDirs = mutableSetOf<String>()
    private val participants = mutableMapOf<String, ParticipantRecord>()
    private val examinations = mutableMapOf<String, Examination>()
    private val configSnapshots = mutableMapOf<String, String>()
    private val partialMasterFiles = mutableMapOf<String, MutableList<Path>>()
    private val partialMasterFrameCounts = mutableMapOf<Path, Long>()
    private val finalizedMasterPartFrames = mutableMapOf<String, Long>()

    /** Calls to [writeExamination], in order, for assertions on the update sequence. */
    val examinationWrites = mutableListOf<Examination>()

    /** `partialFile -> format` for every [finalizePartialMasterFile] call, so recovery tests
     * can assert *which* format was used to reconstruct a given part's header (§13 decision 28). */
    val finalizeCalls = mutableMapOf<Path, CaptureFormat>()

    override fun sessionDir(folderName: String): Path = root.resolve(folderName)
    override fun masterDir(folderName: String): Path = sessionDir(folderName).resolve("master")
    override fun clipsDir(folderName: String): Path = sessionDir(folderName).resolve("clips")
    override fun defaultMasterFile(folderName: String): Path = masterDir(folderName).resolve("session_master.wav")

    override fun createSessionDirectory(folderName: String): Path {
        createdDirs += folderName
        return sessionDir(folderName)
    }

    override fun writeParticipant(folderName: String, participant: ParticipantRecord) {
        participants[folderName] = participant
    }

    override fun readParticipant(folderName: String): ParticipantRecord? = participants[folderName]

    override fun writeExamination(folderName: String, examination: Examination) {
        examinations[folderName] = examination
        examinationWrites += examination
    }

    override fun readExamination(folderName: String): Examination? = examinations[folderName]

    override fun writeConfigSnapshot(folderName: String, rawJson: String) {
        configSnapshots[folderName] = rawJson
    }

    override fun readConfigSnapshot(folderName: String): String? = configSnapshots[folderName]

    override fun listSessionFolderNames(): List<String> = createdDirs.toList().sorted()

    override fun listSessions(): List<SessionSummary> =
        listSessionFolderNames().mapNotNull { folderName ->
            val examination = examinations[folderName] ?: return@mapNotNull null
            SessionSummary(
                folderName = folderName,
                sessionId = examination.sessionId,
                patientCode = SessionFolderNaming.extractPatientCode(folderName, examination.sessionId),
                startedAt = examination.startedAt,
                recovered = examination.recovered,
                processingStatus = examination.processing?.status,
            )
        }

    /** Test setup: simulate a leftover `*.partial.wav` left by a crash, worth
     * [frameCount] frames once finalized. */
    fun seedPartialMasterFile(folderName: String, partialFile: Path, frameCount: Long) {
        partialMasterFiles.getOrPut(folderName) { mutableListOf() }.add(partialFile)
        partialMasterFrameCounts[partialFile] = frameCount
    }

    /** Test setup: simulate an already-cleanly-finalized master part worth [frameCount]
     * frames (§8.4's "finished cleanly but the log wasn't compacted" case). */
    fun seedFinalizedMasterPartFrames(folderName: String, frameCount: Long) {
        finalizedMasterPartFrames[folderName] = frameCount
    }

    override fun findPartialMasterFiles(folderName: String): List<Path> =
        partialMasterFiles[folderName]?.toList() ?: emptyList()

    override fun finalizePartialMasterFile(partialFile: Path, format: CaptureFormat): RecoveredMasterPart {
        finalizeCalls[partialFile] = format
        val frames = partialMasterFrameCounts.remove(partialFile) ?: 0L
        partialMasterFiles.values.forEach { it.remove(partialFile) }
        val finalPath = partialFile.resolveSibling(
            partialFile.fileName.toString().removeSuffix(".partial.wav") + ".wav",
        )
        return RecoveredMasterPart(finalPath, frames)
    }

    override fun latestFinalizedMasterPartFrames(folderName: String, format: CaptureFormat): Long? =
        finalizedMasterPartFrames[folderName]
}
