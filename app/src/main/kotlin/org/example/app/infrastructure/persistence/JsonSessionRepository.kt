package org.example.app.infrastructure.persistence

import kotlinx.serialization.json.Json
import org.example.app.domain.AppDirectories
import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.session.Examination
import org.example.app.domain.session.ParticipantRecord
import org.example.app.domain.session.RecoveredMasterPart
import org.example.app.domain.session.SessionFolderNaming
import org.example.app.domain.session.SessionRepository
import org.example.app.domain.session.SessionSummary
import org.example.app.infrastructure.audio.WavHeader
import org.example.app.infrastructure.audio.framesIn
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Production [SessionRepository] over the filesystem layout in §8.2. */
class JsonSessionRepository(
    private val directories: AppDirectories,
) : SessionRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun sessionDir(folderName: String): Path = directories.sessionDir(folderName)
    override fun masterDir(folderName: String): Path = sessionDir(folderName).resolve("master")
    override fun clipsDir(folderName: String): Path = sessionDir(folderName).resolve("clips")
    override fun archiveDir(folderName: String): Path = sessionDir(folderName).resolve("archive")
    override fun defaultMasterFile(folderName: String): Path = masterDir(folderName).resolve("session_master.wav")

    override fun archiveExists(folderName: String): Boolean {
        val dir = archiveDir(folderName)
        if (!Files.isDirectory(dir)) return false
        return Files.list(dir).use { stream -> stream.anyMatch { it.fileName.toString().endsWith(".zip") } }
    }
    private fun metadataDir(folderName: String): Path = sessionDir(folderName).resolve("metadata")
    private fun waveformCacheDir(folderName: String): Path = sessionDir(folderName).resolve("waveform_cache")

    private fun participantPath(folderName: String): Path = sessionDir(folderName).resolve("participant.json")
    private fun examinationPath(folderName: String): Path = sessionDir(folderName).resolve("examination.json")
    private fun configSnapshotPath(folderName: String): Path =
        sessionDir(folderName).resolve("task_configuration_snapshot.json")

    override fun createSessionDirectory(folderName: String): Path {
        val dir = sessionDir(folderName)
        Files.createDirectories(dir)
        Files.createDirectories(masterDir(folderName))
        Files.createDirectories(clipsDir(folderName))
        Files.createDirectories(metadataDir(folderName))
        Files.createDirectories(waveformCacheDir(folderName))
        return dir
    }

    override fun writeParticipant(folderName: String, participant: ParticipantRecord) {
        AtomicFileWriter.writeString(
            participantPath(folderName),
            json.encodeToString(ParticipantRecord.serializer(), participant),
        )
    }

    override fun readParticipant(folderName: String): ParticipantRecord? =
        AtomicFileWriter.readStringOrNull(participantPath(folderName))
            ?.let { json.decodeFromString(ParticipantRecord.serializer(), it) }

    override fun writeExamination(folderName: String, examination: Examination) {
        AtomicFileWriter.writeString(
            examinationPath(folderName),
            json.encodeToString(Examination.serializer(), examination),
        )
    }

    override fun readExamination(folderName: String): Examination? =
        AtomicFileWriter.readStringOrNull(examinationPath(folderName))
            ?.let { json.decodeFromString(Examination.serializer(), it) }

    override fun writeConfigSnapshot(folderName: String, rawJson: String) {
        AtomicFileWriter.writeString(configSnapshotPath(folderName), rawJson)
    }

    override fun readConfigSnapshot(folderName: String): String? =
        AtomicFileWriter.readStringOrNull(configSnapshotPath(folderName))

    override fun listSessionFolderNames(): List<String> {
        if (!Files.isDirectory(directories.sessionsDir)) return emptyList()
        return Files.list(directories.sessionsDir).use { stream ->
            stream.filter { Files.isDirectory(it) }.map { it.fileName.toString() }.sorted().toList()
        }
    }

    override fun listSessions(): List<SessionSummary> =
        listSessionFolderNames().mapNotNull { folderName ->
            val examination = try {
                readExamination(folderName)
            } catch (e: Exception) {
                null
            } ?: return@mapNotNull null

            SessionSummary(
                folderName = folderName,
                sessionId = examination.sessionId,
                patientCode = SessionFolderNaming.extractPatientCode(folderName, examination.sessionId),
                startedAt = examination.startedAt,
                recovered = examination.recovered,
                processingStatus = examination.processing?.status,
            )
        }

    override fun findPartialMasterFiles(folderName: String): List<Path> {
        val dir = masterDir(folderName)
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".partial.wav") }
                .sorted(compareBy { partNumber(it.fileName.toString()) })
                .toList()
        }
    }

    override fun finalizePartialMasterFile(partialFile: Path, format: CaptureFormat): RecoveredMasterPart {
        val length = Files.size(partialFile)
        val dataSize = (length - WavHeaderSize).coerceAtLeast(0)
        val header = WavHeader.build(format, dataSize)

        FileChannel.open(partialFile, StandardOpenOption.WRITE).use { channel ->
            channel.position(0)
            channel.write(ByteBuffer.wrap(header))
            channel.force(true)
        }

        val finalName = partialFile.fileName.toString().removeSuffix(".partial.wav") + ".wav"
        val finalPath = partialFile.resolveSibling(finalName)
        Files.move(partialFile, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)

        return RecoveredMasterPart(finalPath, format.framesIn(dataSize))
    }

    override fun latestFinalizedMasterPartFrames(folderName: String, format: CaptureFormat): Long? {
        val dir = masterDir(folderName)
        if (!Files.isDirectory(dir)) return null
        val files = Files.list(dir).use { stream ->
            stream.filter {
                val name = it.fileName.toString()
                name.endsWith(".wav") && !name.endsWith(".partial.wav")
            }.toList()
        }
        val latest = files.maxByOrNull { partNumber(it.fileName.toString()) } ?: return null
        val dataSize = (Files.size(latest) - WavHeaderSize).coerceAtLeast(0)
        return format.framesIn(dataSize)
    }

    /** `session_master.wav` -> 1, `session_master.part2.wav`/`.partial.wav` -> 2, ... */
    private fun partNumber(fileName: String): Int {
        val stripped = fileName.removeSuffix(".partial.wav").removeSuffix(".wav")
        val match = Regex("part(\\d+)$").find(stripped)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
    }

    private companion object {
        val WavHeaderSize = WavHeader.HEADER_SIZE.toLong()
    }
}
