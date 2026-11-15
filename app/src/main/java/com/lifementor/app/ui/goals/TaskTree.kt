package com.lifementor.app.ui.goals

import com.lifementor.app.data.TaskItem

internal data class TaskTreeRow(val task: TaskItem, val depth: Int)

internal fun visibleTaskRows(tasks: List<TaskItem>, expandedIds: Set<Long>): List<TaskTreeRow> {
    val children = tasks.groupBy { it.parentId }
    val rows = ArrayList<TaskTreeRow>()
    val pending = ArrayDeque<TaskTreeRow>()
    children[null].orEmpty().asReversed().forEach { pending.addLast(TaskTreeRow(it, 0)) }
    while (pending.isNotEmpty()) {
        val row = pending.removeLast()
        rows.add(row)
        if (row.task.id in expandedIds) {
            children[row.task.id].orEmpty().asReversed().forEach {
                pending.addLast(TaskTreeRow(it, row.depth + 1))
            }
        }
    }
    return rows
}
