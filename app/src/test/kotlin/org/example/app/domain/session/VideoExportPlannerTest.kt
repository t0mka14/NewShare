package org.example.app.domain.session

import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.example.app.domain.video.VideoCaptureFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VideoExportPlannerTest {

    private val template = "\${field.patient_code}_\${taskIndex}_\${task.subtype}_Rep\${repetition}"

    private fun stop(taskIndex: Int, take: Int) = TimelineEvent(
        type = TimelineEventType.STOP_BUTTON_PRESSED, sampleOffset = null, wallClock = "t",
        taskIndex = taskIndex, repetition = 1, take = take,
    )

    private fun take(taskIndex: Int, take: Int) = VideoTakeRecord(
        file = "video/task%02d_rep01_take%02d.mjpeg".format(taskIndex, take),
        taskIndex = taskIndex, repetition = 1, take = take,
        captureFormat = VideoCaptureFormat(1280, 720, 25),
    )

    private fun plan(records: List<TaskRecord>, takes: List<VideoTakeRecord>, events: List<TimelineEvent>) =
        VideoExportPlanner.plan(records, takes, events, template, "inst", mapOf("patient_code" to "HC001"))

    @Test
    fun `the last stopped take is kept, named like the audio clip`() {
        val plans = plan(
            listOf(TaskRecord(taskIndex = 4, type = "VOCAL", subtype = "MONOLOGUE", repetition = 1, takes = 2)),
            listOf(take(4, 1), take(4, 2)),
            listOf(stop(4, 1), stop(4, 2)),
        )

        assertEquals(listOf(VideoExportPlan(4, 1, "video/task04_rep01_take02.mjpeg", 25, "HC001_4_MONOLOGUE_Rep1.mp4")), plans)
    }

    /** The audio-only fallback of a filming VOCAL task: the kept take has no video. */
    @Test
    fun `a kept take without a video entry produces no plan`() {
        val plans = plan(
            listOf(TaskRecord(taskIndex = 0, type = "VOCAL", subtype = "PHONATION", repetition = 1, takes = 2)),
            listOf(take(0, 1)), // only the rejected take was filmed
            listOf(stop(0, 1), stop(0, 2)),
        )

        assertEquals(emptyList<VideoExportPlan>(), plans)
    }

    @Test
    fun `a VIDEO task without a subtype is named VIDEO`() {
        val plans = plan(
            listOf(TaskRecord(taskIndex = 1, type = "VIDEO", subtype = null, repetition = 1, takes = 1)),
            listOf(take(1, 1)),
            listOf(stop(1, 1)),
        )

        assertEquals("HC001_1_VIDEO_Rep1.mp4", plans.single().fileName)
    }

    @Test
    fun `a skipped instance produces no plan`() {
        val plans = plan(
            listOf(TaskRecord(taskIndex = 1, type = "VIDEO", repetition = 1, skipped = true)),
            listOf(take(1, 1)),
            emptyList(),
        )

        assertEquals(emptyList<VideoExportPlan>(), plans)
    }
}
