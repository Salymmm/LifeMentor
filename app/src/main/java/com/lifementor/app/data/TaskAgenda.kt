package com.lifementor.app.data

import java.time.LocalDate
import java.time.ZoneOffset

object TaskAgenda {
    data class Groups(
        val today: List<TaskItem>,
        val overdue: List<TaskItem>,
        val future: List<TaskItem>,
        val unscheduled: List<TaskItem>,
    )

    fun groups(tasks: List<TaskItem>, activeGoalIds: Set<Long>, today: LocalDate): Groups {
        val eligible = tasks.filter { !it.done && it.level == 3 && it.goalId in activeGoalIds }
            .sortedWith(compareBy<TaskItem> { it.dueDate ?: Long.MAX_VALUE }.thenBy { it.id })
        val todayDate = dateMillis(today)
        return Groups(
            today = eligible.filter { it.dueDate == todayDate },
            overdue = eligible.filter { it.dueDate != null && it.dueDate < todayDate },
            future = eligible.filter { it.dueDate != null && it.dueDate > todayDate },
            unscheduled = eligible.filter { it.dueDate == null },
        )
    }

    fun dateMillis(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
