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
        "请在手机日历中确认保存。返回本应用不会自动同步日历里的修改。"
    } catch (_: ActivityNotFoundException) {
        "未找到可添加事件的日历应用，请安装或启用手机日历后重试。"
    }
}
