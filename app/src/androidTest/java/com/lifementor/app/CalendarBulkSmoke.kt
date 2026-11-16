package com.lifementor.app

import android.app.Instrumentation
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import com.lifementor.app.data.CalendarEventDraft
import com.lifementor.app.data.CalendarSyncIdentity
import com.lifementor.app.data.CalendarSyncItem
import com.lifementor.app.data.CalendarSyncPlan
import com.lifementor.app.data.CalendarSyncUpdate
import com.lifementor.app.data.OwnedCalendarEvent
import com.lifementor.app.notify.CalendarSyncProvider

/** Builds real Android values/operations; never grants permissions or applies any provider write. */
internal object CalendarBulkSmoke {
    fun run(instrumentation: Instrumentation) {
        val context = instrumentation.targetContext
        var checks = 0
        var failure: Throwable? = null
        fun verify(value: Boolean, description: String) { check(value) { description }; checks++ }
        try {
            val identity = CalendarSyncIdentity("test-owner")
            val draft = CalendarEventDraft("中文任务", "测试目标", 1794787200000L, 1794873600000L)
            val item = CalendarSyncItem(123, draft)
            val provider = CalendarSyncProvider(context)
            val values = provider.values(7, identity, item)
            verify(values.getAsLong(CalendarContract.Events.CALENDAR_ID) == 7L, "Uses selected calendar")
            verify(values.getAsString(CalendarContract.Events.TITLE) == draft.title, "Preserves task title")
            verify(values.getAsLong(CalendarContract.Events.DTSTART) == draft.begin && values.getAsLong(CalendarContract.Events.DTEND) == draft.end,
                "Writes UTC all-day interval")
            verify(values.getAsInteger(CalendarContract.Events.ALL_DAY) == 1 && values.getAsString(CalendarContract.Events.EVENT_TIMEZONE) == "UTC",
                "Declares all-day UTC event")
            verify(listOf(CalendarContract.Events.DURATION, CalendarContract.Events.RRULE, CalendarContract.Events.RDATE,
                CalendarContract.Events.EXRULE, CalendarContract.Events.EXDATE).all { values.containsKey(it) && values.get(it) == null },
                "Clears calendar-side recurrence before supplying a single event end time")
            verify(values.getAsString(CalendarContract.Events.CUSTOM_APP_PACKAGE) == context.packageName, "Marks app ownership")
            verify(identity.taskId(values.getAsString(CalendarContract.Events.CUSTOM_APP_URI)) == item.taskId, "Marks installation and task identity")
            val event = OwnedCalendarEvent(20, 123, draft)
            val (selection, args) = provider.guard(7, identity, event)
            verify(selection.contains(CalendarContract.Events.CALENDAR_ID) && selection.contains(CalendarContract.Events.CUSTOM_APP_PACKAGE) &&
                selection.contains(CalendarContract.Events.CUSTOM_APP_URI), "Updates/deletes guard calendar, app and identity")
            verify(args.toList() == listOf("20", "7", context.packageName, identity.uri(123)), "Guard targets the exact owned row")
            val plan = CalendarSyncPlan(listOf(item), listOf(CalendarSyncUpdate(event, item)), listOf(event.copy(id = 30)), 0)
            val operations = provider.operations(7, identity, plan)
            verify(operations.size == 3 && operations[0].isInsert && operations[1].isUpdate && operations[2].isDelete,
                "Creates one batched insert/update/delete sequence")
            verify(operations.all { it.uri == CalendarContract.Events.CONTENT_URI }, "Batch uses Calendar Provider events")
            @Suppress("DEPRECATION")
            val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            verify(permissions.contains(android.Manifest.permission.READ_CALENDAR) && permissions.contains(android.Manifest.permission.WRITE_CALENDAR),
                "Declares required runtime permissions")
            if (!CalendarSyncProvider.hasPermissions(context)) {
                verify(runCatching { provider.calendars() }.isFailure, "Permission denial blocks provider access")
            }
        } catch (error: Throwable) { failure = error }
        instrumentation.finish(if (failure == null) -1 else 0, Bundle().apply {
            putString("stream", failure?.let { "FAIL after $checks checks: ${it.stackTraceToString()}" }
                ?: "PASS: $checks bulk calendar contract checks; no calendar permission granted or data changed")
        })
    }
}
