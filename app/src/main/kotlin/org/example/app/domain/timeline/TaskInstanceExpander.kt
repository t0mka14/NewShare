package org.example.app.domain.timeline

import org.example.app.domain.config.Task
import org.example.app.domain.config.VideoTask

/**
 * One navigable screen in the expanded protocol (§8.3): a task with `nrepetition: N` expands
 * into N task instances ("Rep 1" ... "Rep N"), navigated by [taskIndex] (position in the
 * expanded list), never by object identity (§8.6 — fixes the original app's
 * `indexOf`-on-data-class navigation bug, §4).
 *
 * `taskIndex` is 0-based here (plain list position). UI-facing "task N of M" numbering is
 * `taskIndex + 1`; this is an assumption pending lead confirmation since §6.2 only specifies
 * that the value is *derived from position*, not its base.
 */
data class TaskInstance(
    val taskIndex: Int,
    val repetition: Int,
    val task: Task,
)

/**
 * Result of expanding a protocol's task list. [skippedVideoTaskCount] counts `VIDEO` tasks
 * that were excluded entirely (not just non-navigable) because VIDEO is out of scope for this
 * phase (§2 non-goals); the app skips them with a logged warning, so they neither occupy a
 * task-instance slot nor consume a `taskIndex`.
 */
data class ProtocolExpansion(
    val instances: List<TaskInstance>,
    val skippedVideoTaskCount: Int,
)

object TaskInstanceExpander {
    fun expand(tasks: List<Task>): ProtocolExpansion {
        val instances = mutableListOf<TaskInstance>()
        var skippedVideoTaskCount = 0
        var index = 0

        for (task in tasks) {
            if (task is VideoTask) {
                skippedVideoTaskCount++
                continue
            }
            val repetitions = task.nrepetition.coerceAtLeast(1)
            for (repetition in 1..repetitions) {
                instances += TaskInstance(taskIndex = index, repetition = repetition, task = task)
                index++
            }
        }

        return ProtocolExpansion(instances, skippedVideoTaskCount)
    }
}
