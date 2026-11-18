package com.lifementor.app.data

internal fun taskBranchIds(tasks: List<TaskItem>, rootId: Long): Set<Long> {
    val root = tasks.find { it.id == rootId } ?: return emptySet()
    val children = tasks.filter { it.goalId == root.goalId }.groupBy { it.parentId }
    val ids = mutableSetOf<Long>()
    val pending = ArrayDeque<Long>()
    pending.addLast(rootId)
    while (pending.isNotEmpty()) {
        val id = pending.removeLast()
        if (ids.add(id)) children[id].orEmpty().forEach { pending.addLast(it.id) }
    }
    return ids
}
