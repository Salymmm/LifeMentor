package com.lifementor.app.data

internal data class DecompositionDraft(val goalId: Long, val goalTitle: String, val tasks: List<TaskItem>) {
    fun editTask(id: Long, title: String, date: Long?) =
        copy(tasks = tasks.map { if (it.id == id) it.edited(title, date) else it })

    fun withoutBranch(id: Long): DecompositionDraft {
        val removed = taskBranchIds(tasks, id)
        return copy(tasks = tasks.filterNot { it.id in removed })
    }

    fun appendTo(goal: Goal?, existing: List<TaskItem>): List<TaskItem> {
        require(goal?.id == goalId && goal.status == "active" && !goal.decomposed) {
            "This goal is no longer available for AI decomposition."
        }
        require(tasks.isNotEmpty()) { "Keep at least one task to save the draft." }
        val byId = tasks.associateBy { it.id }
        require(byId.size == tasks.size && tasks.all {
            it.goalId == goalId && it.level in 1..3 &&
                (if (it.level == 1) it.parentId == null else byId[it.parentId]?.level == it.level - 1)
        }) { "The draft contains an invalid task hierarchy." }

        val savedById = existing.associateBy { it.id }
        val additions = tasks.map { it.edited(it.title, it.dueDate) }.filter { task ->
            val saved = savedById[task.id]
            require(saved == null || saved == task) { "A draft task conflicts with an existing task. Generate a new draft." }
            saved == null
        }
        return existing + additions
    }
}
