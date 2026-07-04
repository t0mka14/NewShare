package org.example.app.domain.session

import org.example.app.domain.audio.CaptureFormat
import org.example.app.domain.timeline.TimelineEditedSegment
import org.example.app.domain.timeline.TimelineEvent
import org.example.app.domain.timeline.TimelineEventType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ClipExportPlannerTest {

    private val format = CaptureFormat.PREFERRED
    private val sessionDir = Path.of("fake-session-dir")
    private val masterFile = sessionDir.resolve("master/session_master.wav")
    private val singlePart = listOf(MasterPart(masterFile, format, 0L, null))
    private val template = "\${installationId}_\${patientCode}_\${taskIndex}_\${task.subtype}_\${repetition}"

    private fun start(taskIndex: Int, repetition: Int, take: Int, sample: Long) = TimelineEvent(
        type = TimelineEventType.START_BUTTON_PRESSED, sampleOffset = sample, wallClock = "2026-07-01T09:00:00Z",
        taskIndex = taskIndex, repetition = repetition, take = take,
    )

    private fun stop(taskIndex: Int, repetition: Int, take: Int, sample: Long) = TimelineEvent(
        type = TimelineEventType.STOP_BUTTON_PRESSED, sampleOffset = sample, wallClock = "2026-07-01T09:01:00Z",
        taskIndex = taskIndex, repetition = repetition, take = take,
    )

    private fun reject(taskIndex: Int, repetition: Int, take: Int, sample: Long) = TimelineEvent(
        type = TimelineEventType.TAKE_REJECTED, sampleOffset = sample, wallClock = "2026-07-01T09:01:30Z",
        taskIndex = taskIndex, repetition = repetition, take = take, reason = "EXAMINER_REPEAT",
    )

    private fun vocalRecord(taskIndex: Int, repetition: Int, subtype: String = "PHONATION") = TaskRecord(
        taskIndex = taskIndex, type = "VOCAL", subtype = subtype, repetition = repetition, takes = 1,
    )

    @Test
    fun `spec worked example - only the last (third) take of each repetition is planned`() {
        // Two task instances (repetitions 1 and 2 of the same VOCAL task, taskIndex 0 and 1 in
        // the expanded protocol), each tried 3 times — only the 3rd take's range is expected.
        val events = mutableListOf<TimelineEvent>()
        val expectedRanges = mutableMapOf<Int, LongRange>()
        var sample = 0L
        for (taskIndex in 0..1) {
            var lastStart = 0L
            var lastStop = 0L
            for (take in 1..3) {
                lastStart = sample
                events += start(taskIndex, 1, take, sample); sample += 1000
                lastStop = sample
                events += stop(taskIndex, 1, take, sample); sample += 100
                if (take < 3) events += reject(taskIndex, 1, take, sample)
            }
            expectedRanges[taskIndex] = lastStart until lastStop
        }

        val result = ClipExportPlanner.plan(
            vocalTaskRecords = listOf(vocalRecord(taskIndex = 0, repetition = 1), vocalRecord(taskIndex = 1, repetition = 1)),
            originalEvents = events,
            editedSegments = null,
            masterParts = singlePart,
            recordingsFileNameTemplate = template,
            installationId = "install1",
            patientCode = "HC001",
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(2, result.plans.size)
        assertEquals(expectedRanges[0], result.plans.first { it.taskIndex == 0 }.localRange)
        assertEquals(expectedRanges[1], result.plans.first { it.taskIndex == 1 }.localRange)
    }

    @Test
    fun `uses edited segments over original takes when present`() {
        val events = listOf(start(0, 1, 1, 0), stop(0, 1, 1, 1000))
        val edited = listOf(TimelineEditedSegment(taskIndex = 0, repetition = 1, startSample = 50, stopSample = 900))

        val result = ClipExportPlanner.plan(
            vocalTaskRecords = listOf(vocalRecord(0, 1)),
            originalEvents = events,
            editedSegments = edited,
            masterParts = singlePart,
            recordingsFileNameTemplate = template,
            installationId = "install1",
            patientCode = "HC001",
        )

        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.plans.size)
        assertEquals(50L until 900L, result.plans[0].localRange) // edited range, not the original [0, 1000)
    }

    @Test
    fun `missing edited segment for a VOCAL instance is a planning error`() {
        val events = listOf(start(0, 1, 1, 0), stop(0, 1, 1, 1000))
        val result = ClipExportPlanner.plan(
            vocalTaskRecords = listOf(vocalRecord(0, 1)),
            originalEvents = events,
            editedSegments = emptyList(), // edited file exists but has no entry for (0, 1)
            masterParts = singlePart,
            recordingsFileNameTemplate = template,
            installationId = "install1",
            patientCode = "HC001",
        )

        assertTrue(result.plans.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is ClipPlanningError.MissingEditedSegment)
    }

    @Test
    fun `no completed take for a non-skipped VOCAL instance is a planning error`() {
        val result = ClipExportPlanner.plan(
            vocalTaskRecords = listOf(vocalRecord(0, 1)),
            originalEvents = emptyList(),
            editedSegments = null,
            masterParts = singlePart,
            recordingsFileNameTemplate = template,
            installationId = "install1",
            patientCode = "HC001",
        )

        assertTrue(result.plans.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is ClipPlanningError.MissingTakeRange)
    }

    @Test
    fun `a range spanning a master part boundary is a planning error`() {
        val interruption = Interruption(sampleOffset = 500L, start = "t", partFile = "master/session_master.part2.wav", captureFormat = format)
        val parts = MasterPartMap.build(masterFile, format, listOf(interruption), sessionDir)
        val events = listOf(start(0, 1, 1, 400), stop(0, 1, 1, 600)) // straddles the 500 boundary

        val result = ClipExportPlanner.plan(
            vocalTaskRecords = listOf(vocalRecord(0, 1)),
            originalEvents = events,
            editedSegments = null,
            masterParts = parts,
            recordingsFileNameTemplate = template,
            installationId = "install1",
            patientCode = "HC001",
        )

        assertTrue(result.plans.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is ClipPlanningError.SpansPartBoundary)
    }

    @Test
    fun `clip file name is rendered from the template and resolved part`() {
        val events = listOf(start(3, 1, 1, 0), stop(3, 1, 1, 1000))
        val result = ClipExportPlanner.plan(
            vocalTaskRecords = listOf(vocalRecord(taskIndex = 3, repetition = 1, subtype = "PATAKA")),
            originalEvents = events,
            editedSegments = null,
            masterParts = singlePart,
            recordingsFileNameTemplate = template,
            installationId = "install1",
            patientCode = "HC001",
        )

        assertEquals("install1_HC001_3_PATAKA_1.wav", result.plans[0].clipFileName)
        assertEquals(masterFile, result.plans[0].sourcePart.file)
    }
}
