package com.lifementor.app.notify

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.lifementor.app.data.CalendarEventDraft
import com.lifementor.app.data.CalendarSyncIdentity
import com.lifementor.app.data.CalendarSyncItem
import com.lifementor.app.data.CalendarSyncPlan
import com.lifementor.app.data.CalendarSyncPlanner
import com.lifementor.app.data.CalendarSyncSource
import com.lifementor.app.data.OwnedCalendarEvent
import java.io.IOException
import java.util.UUID

data class CalendarSyncTarget(val id: Long, val name: String, val account: String)

class CalendarSyncProvider(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val prefs get() = context.getSharedPreferences("calendar_sync", Context.MODE_PRIVATE)

    companion object {
        fun hasPermissions(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    fun selectedId(): Long? = if (prefs.contains("calendar_id")) prefs.getLong("calendar_id", -1) else null

    fun select(id: Long) {
        if (!prefs.edit().putLong("calendar_id", id).commit()) throw IOException("无法保存目标日历")
    }

    private fun requirePermissions() { check(hasPermissions(context)) { "请先允许日历读写权限" } }

    fun calendars(): List<CalendarSyncTarget> {
        requirePermissions()
        val cursor = resolver.query(CalendarContract.Calendars.CONTENT_URI,
            arrayOf("_id", CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), "_id ASC")
            ?: error("手机日历暂时不可用，请稍后重试")
        return cursor.use {
            buildList { while (it.moveToNext()) add(CalendarSyncTarget(it.getLong(0), it.getString(1) ?: "日历", it.getString(2).orEmpty())) }
        }
    }

    private fun identity(): CalendarSyncIdentity {
        val owner = prefs.getString("owner", null) ?: UUID.randomUUID().toString().also {
            if (!prefs.edit().putString("owner", it).commit()) throw IOException("无法保存同步标识")
        }
        return CalendarSyncIdentity(owner)
    }

    fun synchronize(calendarId: Long, source: CalendarSyncSource): CalendarSyncPlan {
        requirePermissions()
        val desired = CalendarSyncPlanner.desired(source)
        check(calendars().any { it.id == calendarId }) { "目标日历已不可写，请重新选择" }
        val identity = identity()
        val events = readOwned(calendarId, identity)
        val plan = CalendarSyncPlanner.plan(desired, events)
        val operations = operations(calendarId, identity, plan)
        if (operations.isNotEmpty()) resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        return plan
    }

    private fun readOwned(calendarId: Long, identity: CalendarSyncIdentity): List<OwnedCalendarEvent> {
        val cursor = resolver.query(CalendarContract.Events.CONTENT_URI, arrayOf("_id",
            CalendarContract.Events.CUSTOM_APP_URI, CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND, CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE, CalendarContract.Events.RRULE, CalendarContract.Events.RDATE,
            CalendarContract.Events.EXRULE, CalendarContract.Events.EXDATE, CalendarContract.Events.DURATION),
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.CUSTOM_APP_PACKAGE}=? AND ${CalendarContract.Events.DELETED}=0",
            arrayOf(calendarId.toString(), context.packageName), "_id ASC")
            ?: error("无法读取已同步日程，未执行同步")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val taskId = identity.taskId(it.getString(1)) ?: continue
                    add(OwnedCalendarEvent(it.getLong(0), taskId,
                        CalendarEventDraft(it.getString(2).orEmpty(), it.getString(3).orEmpty(), it.getLong(4), it.getLong(5)),
                        it.getInt(6) != 0, it.getString(7).orEmpty(), (8..12).any { index -> !it.getString(index).isNullOrBlank() }))
                }
            }
        }
    }

    internal fun values(calendarId: Long, identity: CalendarSyncIdentity, item: CalendarSyncItem) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, item.draft.title)
        put(CalendarContract.Events.DESCRIPTION, item.draft.description)
        put(CalendarContract.Events.DTSTART, item.draft.begin)
        put(CalendarContract.Events.DTEND, item.draft.end)
        put(CalendarContract.Events.ALL_DAY, 1)
        put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        put(CalendarContract.Events.EVENT_END_TIMEZONE, "UTC")
        putNull(CalendarContract.Events.DURATION)
        putNull(CalendarContract.Events.RRULE)
        putNull(CalendarContract.Events.RDATE)
        putNull(CalendarContract.Events.EXRULE)
        putNull(CalendarContract.Events.EXDATE)
        put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
        put(CalendarContract.Events.CUSTOM_APP_URI, identity.uri(item.taskId))
    }

    internal fun guard(calendarId: Long, identity: CalendarSyncIdentity, event: OwnedCalendarEvent): Pair<String, Array<String>> =
        "_id=? AND ${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.CUSTOM_APP_PACKAGE}=? AND ${CalendarContract.Events.CUSTOM_APP_URI}=?" to
            arrayOf(event.id.toString(), calendarId.toString(), context.packageName, identity.uri(event.taskId))

    internal fun operations(calendarId: Long, identity: CalendarSyncIdentity, plan: CalendarSyncPlan): ArrayList<ContentProviderOperation> {
        val operations = arrayListOf<ContentProviderOperation>()
        plan.inserts.forEach { item ->
            operations.add(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValues(values(calendarId, identity, item)).build())
        }
        plan.updates.forEach { update ->
            val (selection, args) = guard(calendarId, identity, update.event)
            operations.add(ContentProviderOperation.newUpdate(CalendarContract.Events.CONTENT_URI)
                .withSelection(selection, args).withValues(values(calendarId, identity, update.item)).withExpectedCount(1).build())
        }
        plan.deletes.forEach { event ->
            val (selection, args) = guard(calendarId, identity, event)
            operations.add(ContentProviderOperation.newDelete(CalendarContract.Events.CONTENT_URI)
                .withSelection(selection, args).withExpectedCount(1).build())
        }
        return operations
    }
}
