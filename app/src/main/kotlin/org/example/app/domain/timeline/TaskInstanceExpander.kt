package org.example.app.domain.timeline

import org.example.app.domain.config.Task

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
 * Result of expanding a protocol's task list.
 *
 * `VIDEO` tasks used to be dropped here and counted as skipped (§13 decision 23), because
 * camera capture was out of scope. They are now navigable like any other type and occupy a
 * `taskIndex`; nothing is excluded from the expansion.
 */
data class ProtocolExpansion(
    val instances: List<TaskInstance>,
)

object TaskInstanceExpander {
    fun expand(tasks: List<Task>): ProtocolExpansion {
        val instances = mutableListOf<TaskInstance>()
        var index = 0

        for (task in tasks) {
            val repetitions = task.nrepetition.coerceAtLeast(1)
            for (repetition in 1..repetitions) {
                instances += TaskInstance(taskIndex = index, repetition = repetition, task = task)
                index++
            }
        }

        return ProtocolExpansion(instances)
    }
}
