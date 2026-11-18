package com.lifementor.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lifementor.app.MainActivity
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.TaskItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object TaskReminders {
    private const val CHANNEL = "task_reminders"
    private const val WORK_TAG = "task_reminders"
    private const val DAILY_WORK = "daily_task_reminder"
    internal const val TASK_ID = "task_id"
    internal const val DUE_DATE = "due_date"
    private const val COMPLETE = "com.lifementor.app.COMPLETE_TASK"
    private const val SNOOZE = "com.lifementor.app.SNOOZE_TASK"

    private fun prefs(context: Context) = context.getSharedPreferences("task_reminders", Context.MODE_PRIVATE)
    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
    private fun taskTag(taskId: Long) = "task:$taskId"
    private fun snoozeName(taskId: Long) = "snooze_task:$taskId"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)

    fun reminderTime(context: Context): LocalTime =
        LocalTime.of(prefs(context).getInt("hour", 8), prefs(context).getInt("minute", 0))

    fun setReminderTime(context: Context, time: LocalTime) {
        prefs(context).edit().putInt("hour", time.hour).putInt("minute", time.minute).apply()
        scheduleDaily(context, ExistingWorkPolicy.REPLACE)
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager(context).areNotificationsEnabled() &&
            manager(context).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun initialize(context: Context) {
        manager(context).createNotificationChannel(
            NotificationChannel(CHANNEL, "Daily action reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Local reminders for one unfinished action due today or tomorrow" },
        )
        reconcile(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).apply()
        reconcile(context)
    }

    fun reconcile(context: Context) {
        if (isEnabled(context) && canPost(context)) scheduleDaily(context, ExistingWorkPolicy.KEEP)
        else cancelAll(context)
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
        cancelAll(context)
    }

    private fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        manager(context).cancelAll()
    }

    fun cancelTask(context: Context, taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(snoozeName(taskId))
        manager(context).cancel(taskTag(taskId), 0)
    }

    internal fun scheduleDaily(context: Context, policy: ExistingWorkPolicy) {
        if (!isEnabled(context) || !canPost(context)) return
        val work = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(ReminderPolicy.nextDelayMillis(ZonedDateTime.now(), reminderTime(context)), TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG).build()
        WorkManager.getInstance(context).enqueueUniqueWork(DAILY_WORK, policy, work)
    }

    private fun activeGoalIds() = GameStore.loadGoals().filter { it.status == "active" }.map { it.id }.toSet()

    internal fun currentTask(taskId: Long, expectedDate: Long?) =
        ReminderPolicy.currentTask(GameStore.loadTasks(), activeGoalIds(), taskId, expectedDate)

    internal fun showDaily(context: Context) {
        if (!isEnabled(context) || !canPost(context)) return
        ReminderPolicy.candidate(GameStore.loadTasks(), activeGoalIds(), LocalDate.now())?.let { show(context, it) }
        // Compute the next selected local time anew, including timezone/DST changes.
        scheduleDaily(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    fun showTest(context: Context): String {
        if (!canPost(context)) return "Allow system notifications before sending a test reminder."
        if (!isEnabled(context)) return "Turn on daily action reminders first."
        val activeGoals = activeGoalIds()
        val task = GameStore.loadTasks().filter { !it.done && it.level == 3 && it.goalId in activeGoals }.minByOrNull { it.id }
            ?: return "No actions to remind you about. Add an unfinished action to an active goal first."
        return if (show(context, task)) "Test reminder sent. Complete or snooze the action from the notification." else "Notification not sent. Check system notification permissions."
    }

    internal fun show(context: Context, task: TaskItem): Boolean {
        if (!isEnabled(context) || !canPost(context) || currentTask(task.id, task.dueDate) == null) return false
        val contentIntent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("lifementor://task/${task.id}/open"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dateText = task.dueDateText()?.let { "Scheduled date: $it" } ?: "Take one small action toward your goal."
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(task.title).setContentText(dateText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${task.title}\n$dateText"))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .addAction(0, "Complete", actionIntent(context, task, COMPLETE))
            .addAction(0, "Snooze 1 hour", actionIntent(context, task, SNOOZE))
            .build()
        return try {
            manager(context).notify(taskTag(task.id), 0, notification)
            true
        } catch (_: SecurityException) {
            false // Permission may have been revoked after the check.
        }
    }

    private fun actionIntent(context: Context, task: TaskItem, action: String): PendingIntent {
        val intent = Intent(context, TaskActionReceiver::class.java)
            .setAction(action).setData(Uri.parse("lifementor://task/${task.id}/$action"))
            .putExtra(TASK_ID, task.id).putExtra(DUE_DATE, task.dueDate?.toString())
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun handleAction(context: Context, intent: Intent) {
        if (!isEnabled(context) || !canPost(context) || !intent.hasExtra(TASK_ID)) return
        val taskId = intent.getLongExtra(TASK_ID, 0)
        val task = currentTask(taskId, intent.getStringExtra(DUE_DATE)?.toLongOrNull()) ?: return
        when (intent.action) {
            COMPLETE -> {
                GameStore.completeTask(taskId)
                cancelTask(context, taskId)
            }
            SNOOZE -> {
                val work = OneTimeWorkRequestBuilder<SnoozeReminderWorker>()
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .setInputData(workDataOf(TASK_ID to task.id, DUE_DATE to task.dueDate?.toString()))
                    .addTag(WORK_TAG).build()
                WorkManager.getInstance(context).enqueueUniqueWork(snoozeName(taskId), ExistingWorkPolicy.REPLACE, work)
                manager(context).cancel(taskTag(taskId), 0)
            }
        }
    }
}

class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        TaskReminders.showDaily(applicationContext)
        Result.success()
    }
}

class SnoozeReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        val taskId = inputData.getLong(TaskReminders.TASK_ID, 0)
        val expectedDate = inputData.getString(TaskReminders.DUE_DATE)?.toLongOrNull()
        TaskReminders.currentTask(taskId, expectedDate)?.let { TaskReminders.show(applicationContext, it) }
        Result.success()
    }
}

class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = TaskReminders.handleAction(context, intent)
}

class ReminderTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskReminders.scheduleDaily(context, ExistingWorkPolicy.REPLACE)
    }
}
