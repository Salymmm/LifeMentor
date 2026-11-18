package com.lifementor.app.data

data class CalendarSyncSource(val goals: List<Goal>, val tasks: List<TaskItem>)
data class CalendarSyncItem(val taskId: Long, val draft: CalendarEventDraft)
data class OwnedCalendarEvent(val id: Long, val taskId: Long, val draft: CalendarEventDraft,
    val allDay: Boolean = true, val timezone: String = "UTC", val recurring: Boolean = false)
data class CalendarSyncUpdate(val event: OwnedCalendarEvent, val item: CalendarSyncItem)
data class CalendarSyncPlan(val inserts: List<CalendarSyncItem>, val updates: List<CalendarSyncUpdate>,
    val deletes: List<OwnedCalendarEvent>, val unchanged: Int)

class CalendarSyncIdentity(owner: String) {
    private val prefix = "lifementor://calendar-sync/$owner/"
    fun uri(taskId: Long): String = "$prefix$taskId"
    fun taskId(uri: String?): Long? =
        uri?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toLongOrNull()?.takeIf { uri(it) == uri }
}

object CalendarSyncPlanner {
    fun desired(source: CalendarSyncSource): List<CalendarSyncItem> {
        val goals = source.goals.associateBy { it.id }
        return source.tasks.filter { it.dueDate != null }.sortedBy { it.id }.map { task ->
            val goal = requireNotNull(goals[task.goalId]) { "A task has no matching goal. Sync was not started." }
            val draft = TaskCalendar.eventDraft(task, goal.title)
            CalendarSyncItem(task.id, draft.copy(description = draft.description +
                "\nTask status: ${if (task.done) "Completed" else "Not completed"}"))
        }.also { require(it.map { item -> item.taskId }.distinct().size == it.size) { "Duplicate task IDs. Sync was not started." } }
    }

    fun plan(desired: List<CalendarSyncItem>, existing: List<OwnedCalendarEvent>): CalendarSyncPlan {
        val remaining = existing.groupBy { it.taskId }.toMutableMap()
        val inserts = mutableListOf<CalendarSyncItem>()
        val updates = mutableListOf<CalendarSyncUpdate>()
        val deletes = mutableListOf<OwnedCalendarEvent>()
        var unchanged = 0
        desired.forEach { item ->
            val matches = remaining.remove(item.taskId).orEmpty().sortedBy { it.id }
            val event = matches.firstOrNull()
            if (event == null) inserts.add(item)
            else {
                if (event.draft == item.draft && event.allDay && event.timezone == "UTC" && !event.recurring) unchanged++
                else updates.add(CalendarSyncUpdate(event, item))
                deletes.addAll(matches.drop(1))
            }
        }
        deletes.addAll(remaining.values.flatten())
        return CalendarSyncPlan(inserts, updates, deletes.sortedBy { it.id }, unchanged)
    }
}
