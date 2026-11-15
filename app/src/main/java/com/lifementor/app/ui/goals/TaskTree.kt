package com.lifementor.app.ui.goals

import com.lifementor.app.data.TaskItem

internal fun visibleTaskRows(tasks: List<TaskItem>, expandedIds: Set<Long>): List<TaskItem> {
    val children = tasks.groupBy { it.parentId }
    val rows = ArrayList<TaskItem>()
    fun append(parentId: Long?) {
        children[parentId].orEmpty().forEach { task ->
            rows.add(task)
            if (task.id in expandedIds) append(task.id)
        }
    }
    append(null)
    return rows
}
