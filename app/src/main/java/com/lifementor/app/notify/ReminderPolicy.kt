package com.lifementor.app.notify

import com.lifementor.app.data.TaskItem
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

internal object ReminderPolicy {
    fun candidate(tasks: List<TaskItem>, activeGoalIds: Set<Long>, today: LocalDate): TaskItem? =
        tasks.filter { eligible(it, activeGoalIds) }
            .filter { task ->
                val date = task.dueDate?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                date == today || date == today.plusDays(1)
            }
            .minWithOrNull(compareBy<TaskItem> { it.dueDate }.thenBy { it.id })

    fun currentTask(tasks: List<TaskItem>, activeGoalIds: Set<Long>, taskId: Long, expectedDate: Long?): TaskItem? =
        tasks.find { it.id == taskId && it.dueDate == expectedDate && eligible(it, activeGoalIds) }

    fun nextDelayMillis(now: ZonedDateTime, time: LocalTime = LocalTime.of(8, 0)): Long {
        var next = now.toLocalDate().atTime(time).atZone(now.zone)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
        return Duration.between(now, next).toMillis()
    }

    private fun eligible(task: TaskItem, activeGoalIds: Set<Long>) =
        !task.done && task.level == 3 && task.goalId in activeGoalIds
}
