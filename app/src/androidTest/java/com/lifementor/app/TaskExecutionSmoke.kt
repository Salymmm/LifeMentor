package com.lifementor.app

import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.TaskAgenda
import com.lifementor.app.notify.TaskActionReceiver
import com.lifementor.app.notify.TaskReminders
import androidx.work.WorkManager
import androidx.work.WorkInfo
import java.util.concurrent.TimeUnit
import java.io.File
import java.time.LocalDate

/** Device acceptance checks using isolated files and preferences; no test library required. */
class TaskExecutionSmoke : Instrumentation() {
    private var goalInteractionsOnly = false
    private var calendarOnly = false
    private var bulkCalendarOnly = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        goalInteractionsOnly = arguments?.getString("goal_interactions") == "true"
        calendarOnly = arguments?.getString("calendar_view") == "true"
        bulkCalendarOnly = arguments?.getString("calendar_bulk") == "true"
        if (!goalInteractionsOnly && !calendarOnly && !bulkCalendarOnly && arguments?.getString("disposable") != "true") {
            finish(0, Bundle().apply {
                putString("stream", "REFUSED: use -e disposable true only on a throwaway emulator; this test cancels app-wide reminders.")
            })
            return
        }
        start()
    }

    override fun onStart() {
        if (bulkCalendarOnly) { CalendarBulkSmoke.run(this); return }
        if (calendarOnly) { CalendarSmoke.run(this); return }
        if (goalInteractionsOnly) {
            GoalInteractionSmoke.run(this)
            return
        }
        val directory = File(targetContext.cacheDir, "execution-smoke-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("execution-smoke-$name", mode)
        }
        var failure: Throwable? = null
        var checks = 0
        fun verify(condition: Boolean, description: String) {
            check(condition) { description }
            checks++
        }
        runOnMainSync {
            try {
                AppStorage.init(context)
                TaskReminders.initialize(context)
                val goal = GameStore.addGoal("Offline task smoke test", "pathfinder")
                GameStore.addQuickAction(goal.id, "A manual task without any API key")
                val task = GameStore.loadTasks().single()
                verify(task.parentId == null && !goal.decomposed, "Manual task must not need an AI tree")
                verify(task.dueDate == null, "Manual task starts without an automatic date")
                verify(GameStore.loadXP().isEmpty(), "Creation must not award XP")

                val date = TaskAgenda.dateMillis(LocalDate.now().plusDays(1))
                GameStore.setTaskDueDate(task.id, date)
                verify(GameStore.loadTasks().single().dueDate == date, "Date persists in a fresh file read")
                TaskReminders.setEnabled(context, true)
                verify(TaskReminders.canPost(context), "Notification permission required for device test")
                TaskReminders.showTest(context)
                val notifications = context.getSystemService(NotificationManager::class.java)
                fun notificationCountIs(expected: Int): Boolean {
                    val deadline = System.nanoTime() + 2_000_000_000L
                    while (notifications.activeNotifications.size != expected && System.nanoTime() < deadline) {
                        Thread.sleep(25)
                    }
                    return notifications.activeNotifications.size == expected
                }
                verify(notificationCountIs(1), "Test notification is posted")
                val snooze = Intent(context, TaskActionReceiver::class.java)
                    .setAction("com.lifementor.app.SNOOZE_TASK")
                    .putExtra("task_id", task.id).putExtra("due_date", date.toString())
                TaskActionReceiver().onReceive(context, snooze)
                val snoozes = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork("snooze_task:${task.id}").get(5, TimeUnit.SECONDS)
                verify(snoozes.any { it.state == WorkInfo.State.ENQUEUED && it.initialDelayMillis == 3_600_000L },
                    "Snooze schedules one-hour work")
                verify(notificationCountIs(0), "Snooze dismisses current notification")
                TaskReminders.showTest(context)
                verify(notificationCountIs(1), "Wait for the reposted notification before acting on it")
                val revision = GameStore.taskRevision.intValue
                val complete = Intent(context, TaskActionReceiver::class.java)
                    .setAction("com.lifementor.app.COMPLETE_TASK")
                    .putExtra("task_id", task.id).putExtra("due_date", date.toString())
                TaskActionReceiver().onReceive(context, complete)
                TaskActionReceiver().onReceive(context, complete)
                verify(GameStore.loadTasks().single().done, "Notification completes the task")
                verify(GameStore.taskRevision.intValue > revision, "Completion signals visible task screens to refresh")
                verify(GameStore.loadXP().sumOf { it.amount } == 10, "Duplicate actions award XP once")
                verify(notificationCountIs(0), "Completion dismisses notification")

                GameStore.addQuickAction(goal.id, "Reschedule smoke test")
                val second = GameStore.loadTasks().last()
                val nextMessage = TaskReminders.showTest(context)
                verify(notificationCountIs(1), "Next action can be reminded: $nextMessage; active=${notifications.activeNotifications.map { it.tag }}")
                val stale = Intent(context, TaskActionReceiver::class.java)
                    .setAction("com.lifementor.app.COMPLETE_TASK")
                    .putExtra("task_id", second.id).putExtra("due_date", second.dueDate.toString())
                GameStore.setTaskDueDate(second.id, date)
                TaskActionReceiver().onReceive(context, stale)
                verify(!GameStore.loadTasks().last().done, "Stale notification cannot complete a rescheduled task")
                verify(notificationCountIs(0), "Rescheduling dismisses old notification")
                GameStore.setTaskDueDate(second.id, null)
                verify(GameStore.loadTasks().last().dueDate == null, "Clear date persists")
                TaskReminders.showTest(context)
                GameStore.setGoalDone(goal.id)
                verify(notificationCountIs(0), "Completing a goal dismisses its notifications")
                GameStore.clearAll()
                verify(!TaskReminders.isEnabled(context), "Clear all disables reminders")
                verify(GameStore.loadTasks().isEmpty(), "Clear all removes test tasks")
            } catch (error: Throwable) {
                failure = error
            } finally {
                TaskReminders.reset(context)
                AppStorage.init(targetContext)
                directory.deleteRecursively()
            }
        }
        val result = Bundle().apply {
            putString("stream", failure?.let { "FAIL after $checks checks: ${it.stackTraceToString()}" }
                ?: "PASS: $checks Android task and notification checks")
        }
        finish(if (failure == null) -1 else 0, result)
    }
}
