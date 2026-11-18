package com.lifementor.app.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.TaskCalendar
import com.lifementor.app.data.TaskItem
import java.time.LocalDate

class CalendarViewModel : ViewModel() {
    data class Snapshot(
        val byDate: Map<LocalDate, List<TaskItem>>,
        val goalTitles: Map<Long, String>,
        val unscheduledCount: Int,
    )

    var snapshot by mutableStateOf(load())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private fun load(): Snapshot {
        val goals = GameStore.loadGoals().filter { it.status == "active" }
        val activeIds = goals.map { it.id }.toSet()
        val tasks = GameStore.loadTasks()
        return Snapshot(TaskCalendar.byDate(tasks, activeIds), goals.associate { it.id to it.title },
            tasks.count { it.goalId in activeIds && !it.done && it.dueDate == null })
    }

    fun refresh() { snapshot = load() }

    fun toggle(taskId: Long) {
        try {
            GameStore.toggleTask(taskId)
            error = null
            refresh()
        } catch (_: Exception) { error = "Could not save task status. Try again." }
    }

    fun setDate(taskId: Long, date: Long?) {
        try {
            GameStore.setTaskDueDate(taskId, date)
            error = null
            refresh()
        } catch (_: Exception) { error = "Could not save the date. Try again." }
    }
}
