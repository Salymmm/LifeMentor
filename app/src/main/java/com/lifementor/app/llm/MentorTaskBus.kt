package com.lifementor.app.llm

/** Process-local queue for user-confirmed cross-mentor suggestions; it does not invoke other models. */
object MentorTaskBus {
    private const val MAX_PER_TARGET = 6
    private val acceptedByTarget = LinkedHashMap<String, MutableList<MentorHandoffSuggestion>>()

    /** Stores only proposals explicitly accepted by the user. */
    @Synchronized
    fun accept(suggestion: MentorHandoffSuggestion): Boolean {
        if (suggestion.sourceMentorId.isBlank() || suggestion.targetMentorId.isBlank()) return false
        if (suggestion.sourceMentorId == suggestion.targetMentorId || suggestion.taskTitle.isBlank()) return false
        if (suggestion.taskTitle.length > MAX_TASK_TITLE_LENGTH || suggestion.rationale.length > MAX_RATIONALE_LENGTH) return false
        val targetTasks = acceptedByTarget.getOrPut(suggestion.targetMentorId) { mutableListOf() }
        if (targetTasks.any { it.taskTitle.equals(suggestion.taskTitle, ignoreCase = true) }) return false
        targetTasks += suggestion
        while (targetTasks.size > MAX_PER_TARGET) targetTasks.removeAt(0)
        return true
    }

    @Synchronized
    fun contextFor(targetMentorId: String): String = acceptedByTarget[targetMentorId]
        .orEmpty()
        .joinToString("\n") { "- 来自 ${it.sourceMentorId}：${it.taskTitle}（${it.rationale}）" }
        .take(MAX_CONTEXT_LENGTH)

    @Synchronized
    fun remove(id: String): Boolean {
        val target = acceptedByTarget.values.firstOrNull { entries -> entries.any { it.id == id } } ?: return false
        target.removeAll { it.id == id }
        return true
    }

    @Synchronized
    fun clearAll() {
        acceptedByTarget.clear()
    }

    private const val MAX_CONTEXT_LENGTH = 1200
    private const val MAX_TASK_TITLE_LENGTH = 120
    private const val MAX_RATIONALE_LENGTH = 240
}
