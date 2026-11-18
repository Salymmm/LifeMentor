package com.lifementor.app.notify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.lifementor.app.data.TaskCalendar
import com.lifementor.app.data.TaskItem

object CalendarBridge {
    fun insertIntent(task: TaskItem, goalTitle: String): Intent {
        val draft = TaskCalendar.eventDraft(task, goalTitle)
        return Intent(Intent.ACTION_INSERT)
            .setDataAndType(CalendarContract.Events.CONTENT_URI, "vnd.android.cursor.dir/event")
            .putExtra(CalendarContract.Events.TITLE, draft.title)
            .putExtra(CalendarContract.Events.DESCRIPTION, draft.description)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, draft.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, draft.end)
    }

    fun open(context: Context, task: TaskItem, goalTitle: String): String = try {
        context.startActivity(insertIntent(task, goalTitle))
        "Confirm and save in your calendar app. Returning here does not automatically sync changes made in the calendar."
    } catch (_: ActivityNotFoundException) {
        "No calendar app found that can add events. Install or enable a calendar app, then try again."
    }
}
