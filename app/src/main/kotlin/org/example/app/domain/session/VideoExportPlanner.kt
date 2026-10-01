package org.example.app.domain.session

import org.example.app.domain.config.RecordingsFileNameRenderer
import org.example.app.domain.timeline.TakeSelector
import org.example.app.domain.timeline.TimelineEvent

/**
 * One take to remux into `clips/` (§8.8): the kept take of one filming task instance (a VIDEO task,
 * or a VOCAL one with `recordVideo`).
 */
data class VideoExportPlan(
    val taskIndex: Int,
    val repetition: Int,
    /** Session-relative raw take, e.g. `video/task03_rep01_take02.mjpeg` — `VideoTakeRecord.file`. */
    val source: String,
    val fps: Int,
    /** File name only (no `clips/` prefix): the audio clip's name with `.mp4`. */
    val fileName: String,
)

/**
 * Pure computation of "which take's video becomes which MP4" (§8.8), alongside
 * [ClipExportPlanner] and with the same inputs — no I/O, so it is unit-testable on plain data.
 *
 * The kept take is the last *stopped* one, exactly the take whose audio [ClipExportPlanner] cuts
 * from the original timeline. `timeline_edited.json` does not change it: the editor trims audio
 * sample ranges and has no notion of takes, and a take's video is one file that cannot be trimmed
 * to match anyway.
 *
 * An instance whose kept take has no `videoTakes[]` entry produces no plan and no error — a VOCAL
 * `recordVideo` take that fell back to audio only, or a VIDEO take whose camera never opened a
 * file. The record of that is the entry's absence, which the archive keeps.
 */
object VideoExportPlanner {
    fun plan(
        taskRecords: List<TaskRecord>,
        videoTakes: List<VideoTakeRecord>,
        originalEvents: List<TimelineEvent>,
        recordingsFileNameTemplate: String,
        installationId: String,
        fieldValues: Map<String, String>,
    ): List<VideoExportPlan> = taskRecords
        .filter { !it.skipped && (it.type == "VIDEO" || it.type == "VOCAL") }
        .mapNotNull { record ->
            val take = TakeSelector.lastTake(originalEvents, record.taskIndex, record.repetition)
                ?: return@mapNotNull null
            val video = videoTakes.lastOrNull {
                it.taskIndex == record.taskIndex && it.repetition == record.repetition && it.take == take
            } ?: return@mapNotNull null

            val baseName = RecordingsFileNameRenderer.render(
                template = recordingsFileNameTemplate,
                installationId = installationId,
                fieldValues = fieldValues,
                taskIndex = record.taskIndex,
                // A VIDEO task's subtype is optional, but `${task.subtype}` must render as something.
                subtype = record.subtype ?: record.type,
                repetition = record.repetition,
            )
            VideoExportPlan(
                taskIndex = record.taskIndex,
                repetition = record.repetition,
                source = video.file,
                fps = video.captureFormat.fps,
                fileName = "$baseName.mp4",
            )
        }
        .sortedBy { it.taskIndex }
}
