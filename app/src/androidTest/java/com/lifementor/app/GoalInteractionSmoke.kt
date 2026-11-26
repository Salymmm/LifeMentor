package com.lifementor.app

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Bundle
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.DecompositionDraft
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Goal
import com.lifementor.app.data.TaskItem
import com.lifementor.app.notify.TaskReminders
import com.lifementor.app.data.TaskAgenda
import com.lifementor.app.ui.calendar.CalendarViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.io.File
import org.json.JSONObject

/** Uses isolated files and negative fixture IDs; never resets user reminders or user data. */
internal object GoalInteractionSmoke {
    fun run(instrumentation: Instrumentation) {
        val targetContext = instrumentation.targetContext
        val directory = File(targetContext.cacheDir, "goal-smoke-${System.nanoTime()}").apply { mkdirs() }
        val isolatedReminderPrefs = "goal-smoke-reminders-${System.nanoTime()}"
        val context = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(if (name == "task_reminders") isolatedReminderPrefs else name, mode)
        }
        var failure: Throwable? = null
        var checks = 0
        fun verify(condition: Boolean, description: String) {
            check(condition) { description }
            checks++
        }
        instrumentation.runOnMainSync {
            try {
                AppStorage.init(context)
                val goal = Goal(-100, "Isolated goal", "pathfinder", decomposed = true)
                val task = TaskItem(-101, goal.id, null, 3, "Previously completed action",
                    done = true, doneAt = 1000, xpGiven = true, dueDate = 1794700800000L)
                GameStore.saveGoals(listOf(goal))
                GameStore.saveTasks(listOf(task))
                GameStore.addXP(10, task.title, goal.domain)
                val xpBefore = GameStore.loadXP()
                GameStore.setGoalDone(goal.id)
                verify(GameStore.loadGoals().single().status == "done", "Goal completion persists")
                GameStore.reopenGoal(goal.id)
                verify(GameStore.loadGoals().single() == goal, "Undo restores active goal and keeps decomposition state")
                verify(GameStore.loadTasks().single() == task, "Undo preserves completion, hierarchy and task date")
                verify(GameStore.loadXP() == xpBefore, "Undo does not alter rewards")
                GameStore.reopenGoal(goal.id)
                verify(GameStore.loadXP() == xpBefore, "Repeated undo does not duplicate rewards")
                verify(GameStore.loadTasks().single() == task, "Repeated undo preserves task progress")
                var parentId: Long? = null
                val manualTasks = (1..8).map { depth ->
                    GameStore.addTask(goal.id, "手动子任务 $depth", parentId,
                        if (depth == 8) 1794787200000L else null).also { parentId = it.id }
                }
                verify(manualTasks.first().parentId == null, "Root manual task has no parent")
                verify(manualTasks.zipWithNext().all { (parent, child) -> child.parentId == parent.id },
                    "Manual subtasks keep all eight parent relationships")
                verify(manualTasks.all { it.level == 3 }, "Manual nesting preserves action rewards at every depth")
                verify(manualTasks.first().dueDate == null && manualTasks.last().dueDate == 1794787200000L,
                    "Dates remain optional and independent of nesting")
                verify(GameStore.loadTasks() == listOf(task) + manualTasks, "Chinese hierarchy and dates survive storage")
                GameStore.updateGoal(goal.id, "新的中文目标", "scholar")
                verify(GameStore.loadGoals().single() == goal.copy(title = "新的中文目标", domain = "scholar"),
                    "Goal edit preserves status and AI state")
                verify(GameStore.loadTasks() == listOf(task) + manualTasks, "Goal edit preserves its full task tree")
                verify(GameStore.loadXP() == xpBefore, "Goal edit preserves historical rewards")
                GameStore.updateTask(task.id, "已编辑完成的任务", null)
                val editedTask = task.copy(title = "已编辑完成的任务", dueDate = null)
                verify(GameStore.loadTasks().first() == editedTask, "Task editing preserves completion and identity")
                verify(GameStore.loadXP() == xpBefore, "Task editing preserves historical XP")
                val removed = GameStore.deleteTaskTree(manualTasks[3].id)
                verify(removed == manualTasks.drop(3).map { it.id }.toSet(), "Deletion removes exactly the selected branch")
                verify(GameStore.loadTasks() == listOf(editedTask) + manualTasks.take(3), "Deletion preserves ancestors and other tasks")
                verify(GameStore.loadXP() == xpBefore, "Deletion preserves earned XP")
                verify(TaskReminders.reminderTime(context) == LocalTime.of(8, 0), "Default reminder time is 08:00")
                TaskReminders.setReminderTime(context, LocalTime.of(21, 35))
                verify(TaskReminders.reminderTime(context) == LocalTime.of(21, 35), "Custom time persists in isolated preferences")
                verify(!TaskReminders.isEnabled(context), "Choosing time does not enable reminders or schedule work when disabled")
                val calendar = CalendarViewModel()
                verify(calendar.snapshot.unscheduledCount == 3, "Calendar shows remaining undated active tasks")
                val calendarDay = LocalDate.of(2026, 12, 31)
                val calendarTaskId = manualTasks.first().id
                calendar.setDate(calendarTaskId, TaskAgenda.dateMillis(calendarDay))
                verify(calendar.snapshot.byDate[calendarDay]?.single()?.id == calendarTaskId,
                    "Calendar date changes refresh the selected day and persist")
                val xpTotal = GameStore.loadXP().sumOf { it.amount }
                calendar.toggle(calendarTaskId)
                verify(calendar.snapshot.byDate[calendarDay]?.single()?.done == true,
                    "Calendar keeps completed tasks visible")
                calendar.toggle(calendarTaskId)
                calendar.toggle(calendarTaskId)
                verify(GameStore.loadXP().sumOf { it.amount } == xpTotal + 10,
                    "Calendar undo/recompletion does not duplicate rewards")
                verify(GameStore.loadCalendarSyncSource().tasks == GameStore.loadTasks(), "Bulk sync reads a complete source snapshot")
                val taskFile = File(directory, "tasks.json")
                val savedTasks = taskFile.readText()
                taskFile.writeText("{broken JSON")
                val rejectsCorruptSource = runCatching { GameStore.loadCalendarSyncSource() }.isFailure
                taskFile.writeText(savedTasks)
                verify(rejectsCorruptSource, "Corrupt source aborts sync instead of deleting all events")

                val draftGoal = GameStore.addGoal("Try AI decomposition", "pathfinder")
                val manual = GameStore.addTask(draftGoal.id, "Keep this task")
                val milestone = TaskItem(-200, draftGoal.id, null, 1, "Draft milestone")
                val week = TaskItem(-201, draftGoal.id, milestone.id, 2, "Draft week")
                val day = TaskItem(-202, draftGoal.id, week.id, 3, "Draft day")
                val draft = DecompositionDraft(draftGoal.id, draftGoal.title, listOf(milestone, week, day))
                val dueDate = TaskAgenda.dateMillis(calendarDay)
                val edited = draft.withoutBranch(week.id).editTask(milestone.id, "Scheduled milestone", dueDate)
                val beforeConfirmation = GameStore.loadCalendarSyncSource()
                verify(beforeConfirmation.goals.single { it.id == draftGoal.id } == draftGoal,
                    "Editing a decomposition draft leaves the goal eligible")
                verify(beforeConfirmation.tasks.filter { it.goalId == draftGoal.id } == listOf(manual),
                    "Editing a decomposition draft leaves saved tasks unchanged")
                GameStore.confirmDecomposition(edited)
                val confirmed = GameStore.loadCalendarSyncSource()
                verify(confirmed.goals.single { it.id == draftGoal.id }.decomposed,
                    "Confirmation marks the goal decomposed")
                verify(confirmed.tasks.filter { it.goalId == draftGoal.id } ==
                    listOf(manual, milestone.copy(title = "Scheduled milestone", dueDate = dueDate)),
                    "Confirmation appends only the retained edited branch and preserves the manual task")
                verify(CalendarViewModel().snapshot.byDate[calendarDay].orEmpty().any { it.id == milestone.id },
                    "The confirmed date appears on the calendar")
                verify(runCatching { GameStore.confirmDecomposition(edited) }.isFailure &&
                    GameStore.loadCalendarSyncSource() == confirmed,
                    "A second confirmation leaves storage unchanged")

                val interruptedGoal = Goal(-300, "Interrupted confirmation", "scholar")
                val interruptedTask = TaskItem(-301, interruptedGoal.id, null, 1, "Already committed task")
                GameStore.saveGoals(confirmed.goals + interruptedGoal)
                GameStore.saveTasks(confirmed.tasks + interruptedTask)
                val pending = File(directory, "decomposition_commit.json")
                pending.writeText(JSONObject().put("goalId", interruptedGoal.id).put("taskId", interruptedTask.id).toString())
                GameStore.recoverDecomposition()
                verify(GameStore.loadGoals().single { it.id == interruptedGoal.id }.decomposed,
                    "Restart finishes a confirmation after its tasks have been stored")
                verify(!pending.exists() && GameStore.loadTasks().count { it.id == interruptedTask.id } == 1,
                    "Recovery removes the marker without duplicating tasks")

                pending.writeText(JSONObject().put("goalId", interruptedGoal.id).put("taskId", -302).toString())
                GameStore.saveGoals(GameStore.loadGoals().map { if (it.id == interruptedGoal.id) interruptedGoal else it })
                GameStore.recoverDecomposition()
                verify(!pending.exists() && GameStore.loadGoals().single { it.id == interruptedGoal.id } == interruptedGoal,
                    "An interruption before storing tasks leaves the goal untouched")
            } catch (error: Throwable) {
                failure = error
            } finally {
                AppStorage.init(targetContext)
                directory.deleteRecursively()
                targetContext.deleteSharedPreferences(isolatedReminderPrefs)
            }
        }
        instrumentation.finish(if (failure == null) -1 else 0, Bundle().apply {
            putString("stream", failure?.let { "FAIL after $checks checks: ${it.stackTraceToString()}" }
                ?: "PASS: $checks isolated goal interaction checks")
        })
    }
}
