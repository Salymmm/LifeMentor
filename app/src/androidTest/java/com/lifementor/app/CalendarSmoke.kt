package com.lifementor.app

import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import com.lifementor.app.data.TaskAgenda
import com.lifementor.app.data.TaskItem
import com.lifementor.app.notify.CalendarBridge
import java.time.LocalDate

/** Inspects real Android Intent extras using fixture text; never opens or writes to a calendar. */
internal object CalendarSmoke {
    fun run(instrumentation: Instrumentation) {
        var checks = 0
        var failure: Throwable? = null
        fun verify(value: Boolean, description: String) { check(value) { description }; checks++ }
        try {
            val date = LocalDate.of(2026, 12, 31)
            val task = TaskItem(-300, -100, null, 3, "日历测试任务", dueDate = TaskAgenda.dateMillis(date))
            val intent = CalendarBridge.insertIntent(task, "测试目标")
            verify(intent.action == Intent.ACTION_INSERT, "Opens an event insertion draft")
            verify(intent.data == CalendarContract.Events.CONTENT_URI, "Targets system event URI")
            verify(intent.type == "vnd.android.cursor.dir/event", "Declares the event MIME type")
            verify(intent.getStringExtra(CalendarContract.Events.TITLE) == task.title, "Keeps Chinese title")
            verify(intent.getStringExtra(CalendarContract.Events.DESCRIPTION)?.contains("测试目标") == true, "Includes goal context")
            verify(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false), "Exports date-only tasks as all day")
            verify(intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1) == task.dueDate, "UTC begin date")
            verify(intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1) == TaskAgenda.dateMillis(date.plusDays(1)), "Exclusive UTC next-day end")
            var launched: Intent? = null
            val recordingContext = object : ContextWrapper(instrumentation.targetContext) {
                override fun startActivity(intent: Intent) { launched = intent }
            }
            val guidance = CalendarBridge.open(recordingContext, task, "测试目标")
            verify(launched?.action == Intent.ACTION_INSERT, "Open hands the draft to the app context")
            verify(guidance.contains("确认保存"), "Does not claim the event was saved")
            val missingContext = object : ContextWrapper(instrumentation.targetContext) {
                override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
            }
            verify(CalendarBridge.open(missingContext, task, "测试目标").contains("未找到"), "Missing calendar has a user-facing fallback")
        } catch (error: Throwable) { failure = error }
        instrumentation.finish(if (failure == null) -1 else 0, Bundle().apply {
            putString("stream", failure?.let { "FAIL after $checks checks: ${it.stackTraceToString()}" }
                ?: "PASS: $checks calendar intent checks; no user calendar was opened or changed")
        })
    }
}
