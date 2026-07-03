package org.example.app.domain.timeline

import org.example.app.domain.config.CalibrationTask
import org.example.app.domain.config.InfoTask
import org.example.app.domain.config.VideoTask
import org.example.app.domain.config.VocalSubtype
import org.example.app.domain.config.VocalTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TaskInstanceExpanderTest {

    @Test
    fun `a task with nrepetition 1 produces a single instance`() {
        val task = InfoTask(titleKey = "info", nrepetition = 1)
        val result = TaskInstanceExpander.expand(listOf(task))
        assertEquals(1, result.instances.size)
        assertEquals(0, result.instances[0].taskIndex)
        assertEquals(1, result.instances[0].repetition)
    }

    @Test
    fun `a task with nrepetition N expands into N instances`() {
        val task = VocalTask(titleKey = "vocal", subtype = VocalSubtype.PHONATION, nrepetition = 3)
        val result = TaskInstanceExpander.expand(listOf(task))

        assertEquals(3, result.instances.size)
        assertEquals(listOf(1, 2, 3), result.instances.map { it.repetition })
        assertEquals(listOf(0, 1, 2), result.instances.map { it.taskIndex })
        assertEquals(List(3) { task }, result.instances.map { it.task })
    }

    @Test
    fun `taskIndex is a continuous position across multiple tasks and their repetitions`() {
        val tasks = listOf(
            CalibrationTask(titleKey = "cal", optimalLoudness = listOf(0.2, 0.5)),
            VocalTask(titleKey = "vocal", subtype = VocalSubtype.PHONATION, nrepetition = 2),
            InfoTask(titleKey = "info"),
        )
        val result = TaskInstanceExpander.expand(tasks)

        assertEquals(listOf(0, 1, 2, 3), result.instances.map { it.taskIndex })
        // calibration instance, two vocal repetitions, info instance
        assertEquals(listOf(1, 1, 2, 1), result.instances.map { it.repetition })
    }

    @Test
    fun `VIDEO tasks are excluded entirely and counted as skipped`() {
        val tasks = listOf(
            InfoTask(titleKey = "before"),
            VideoTask(titleKey = "video", nrepetition = 1),
            InfoTask(titleKey = "after"),
        )
        val result = TaskInstanceExpander.expand(tasks)

        assertEquals(2, result.instances.size)
        assertEquals(1, result.skippedVideoTaskCount)
        // taskIndex stays continuous across the gap left by the skipped VIDEO task
        assertEquals(listOf(0, 1), result.instances.map { it.taskIndex })
        assertEquals("before", result.instances[0].task.titleKey)
        assertEquals("after", result.instances[1].task.titleKey)
    }

    @Test
    fun `nrepetition 0 or negative is treated as a single instance`() {
        val task = InfoTask(titleKey = "info", nrepetition = 0)
        val result = TaskInstanceExpander.expand(listOf(task))
        assertEquals(1, result.instances.size)
    }

    @Test
    fun `empty task list expands to no instances`() {
        val result = TaskInstanceExpander.expand(emptyList())
        assertEquals(0, result.instances.size)
        assertEquals(0, result.skippedVideoTaskCount)
    }
}
