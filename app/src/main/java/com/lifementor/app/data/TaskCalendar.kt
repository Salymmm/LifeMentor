package com.lifementor.app.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneOffset

data class CalendarEventDraft(val title: String, val description: String, val begin: Long, val end: Long)

object TaskCalendar {
    fun monthCells(month: YearMonth): List<LocalDate?> {
        val offset = month.atDay(1).dayOfWeek.value - 1
        val size = ((offset + month.lengthOfMonth() + 6) / 7) * 7
        return List(size) { index ->
            val day = index - offset + 1
            if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
        }
    }

    fun byDate(tasks: List<TaskItem>, activeGoalIds: Set<Long>): Map<LocalDate, List<TaskItem>> =
        tasks.filter { it.dueDate != null && it.goalId in activeGoalIds }
            .sortedWith(compareBy<TaskItem> { it.done }.thenBy { it.id })
            .groupBy { Instant.ofEpochMilli(it.dueDate!!).atZone(ZoneOffset.UTC).toLocalDate() }

    fun eventDraft(task: TaskItem, goalTitle: String): CalendarEventDraft {
        val dueDate = requireNotNull(task.dueDate) { "Schedule a date for this task first" }
        val date = Instant.ofEpochMilli(dueDate).atZone(ZoneOffset.UTC).toLocalDate()
        return CalendarEventDraft(task.title, "LifeMentor task\nGoal: $goalTitle",
            TaskAgenda.dateMillis(date), TaskAgenda.dateMillis(date.plusDays(1)))
    }
}
