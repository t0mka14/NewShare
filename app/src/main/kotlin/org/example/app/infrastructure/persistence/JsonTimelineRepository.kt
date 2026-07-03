package org.example.app.infrastructure.persistence

import kotlinx.serialization.json.Json
import org.example.app.domain.AppDirectories
import org.example.app.domain.timeline.TimelineEdited
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventCodec
import org.example.app.domain.timeline.TimelineOriginal
import org.example.app.domain.timeline.TimelineParseResult
import org.example.app.domain.timeline.TimelineRepository
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Production [TimelineRepository] over the filesystem layout in §8.2/§8.3. */
class JsonTimelineRepository(
    private val directories: AppDirectories,
) : TimelineRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private fun eventsPath(folderName: String): Path =
        directories.sessionDir(folderName).resolve("timeline.events.jsonl")
    private fun originalPath(folderName: String): Path =
        directories.sessionDir(folderName).resolve("timeline_original.json")
    private fun editedPath(folderName: String): Path =
        directories.sessionDir(folderName).resolve("timeline_edited.json")

    /** Reopens the log for append on every call rather than holding one long-lived handle
     * (unlike the WAV writer, §8.4 point 1 — that constraint is specific to the master file's
     * Windows file-sharing concern). Event rate is low, so the extra open/close per call is
     * negligible (§8.3). */
    override fun appendEvent(folderName: String, event: TimelineEvent) {
        val path = eventsPath(folderName)
        path.parent?.let { Files.createDirectories(it) }
        FileChannel.open(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND,
        ).use { channel ->
            val line = TimelineEventCodec.encodeLine(event) + "\n"
            val buffer = ByteBuffer.wrap(line.toByteArray(StandardCharsets.UTF_8))
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
            channel.force(true)
        }
    }

    override fun eventLogExists(folderName: String): Boolean = Files.exists(eventsPath(folderName))

    override fun readEventLog(folderName: String): TimelineParseResult {
        val path = eventsPath(folderName)
        if (!Files.exists(path)) return TimelineParseResult(emptyList(), tornLastLine = false)
        return TimelineEventCodec.decodeLines(Files.readString(path, StandardCharsets.UTF_8))
    }

    override fun originalExists(folderName: String): Boolean = Files.exists(originalPath(folderName))

    override fun writeOriginal(folderName: String, timeline: TimelineOriginal) {
        AtomicFileWriter.writeString(
            originalPath(folderName),
            json.encodeToString(TimelineOriginal.serializer(), timeline),
        )
    }

    override fun readOriginal(folderName: String): TimelineOriginal? =
        AtomicFileWriter.readStringOrNull(originalPath(folderName))
            ?.let { json.decodeFromString(TimelineOriginal.serializer(), it) }

    override fun editedExists(folderName: String): Boolean = Files.exists(editedPath(folderName))

    override fun writeEdited(folderName: String, timeline: TimelineEdited) {
        AtomicFileWriter.writeString(
            editedPath(folderName),
            json.encodeToString(TimelineEdited.serializer(), timeline),
        )
    }

    override fun readEdited(folderName: String): TimelineEdited? =
        AtomicFileWriter.readStringOrNull(editedPath(folderName))
            ?.let { json.decodeFromString(TimelineEdited.serializer(), it) }
}
