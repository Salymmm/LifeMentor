package com.lifementor.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class TaskItemDateTest {
    @Test
    fun readsLegacyTaskWithoutADate() {
        val task = TaskItem.from(legacyTask())

        assertEquals("Practice interview questions", task.title)
        assertEquals(3, task.level)
        assertTrue(task.done)
        assertTrue(task.xpGiven)
        assertTrue(task.toJson().isNull("dueDate"))
    }

    @Test
    fun retainsTheDateThroughJsonRoundTrip() {
        val task = TaskItem.from(legacyTask().put("dueDate", 1794700800000L))
        val reloaded = TaskItem.from(JSONObject(task.toJson().toString()))

        assertEquals(1794700800000L, reloaded.toJson().getLong("dueDate"))
        assertEquals(legacyTask().getLong("doneAt"), reloaded.doneAt)
        assertTrue(reloaded.xpGiven)
    }

    @Test
    fun retainsTheDateWhenCompletionChanges() {
        val task = TaskItem.from(legacyTask().put("dueDate", 1794700800000L))
        val updated = task.copy(done = false, doneAt = null)

        assertEquals(1794700800000L, updated.toJson().getLong("dueDate"))
        assertEquals(2L, updated.parentId)
    }

    @Test
    fun readsExplicitlyUnscheduledTask() {
        val task = TaskItem.from(legacyTask().put("dueDate", JSONObject.NULL))

        assertTrue(task.toJson().isNull("dueDate"))
    }

    @Test
    fun clearingDatePreservesAllOtherTaskFields() {
        val task = TaskItem.from(legacyTask().put("dueDate", 1794700800000L))
        val cleared = TaskItem.from(JSONObject(task.copy(dueDate = null).toJson().toString()))

        assertEquals(TaskItem.from(legacyTask()), cleared)
        assertTrue(cleared.toJson().isNull("dueDate"))
    }

    @Test
    fun displaysTheSelectedCalendarDateInBothPositiveAndNegativeTimeZones() {
        val task = TaskItem.from(legacyTask().put("dueDate", 1794700800000L))
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("America/Los_Angeles", "Pacific/Kiritimati")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))

                assertEquals("2026-11-15", task.dueDateText())
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun unscheduledTaskHasNoDateLabel() {
        assertNull(TaskItem.from(legacyTask()).dueDateText())
    }

    private fun legacyTask() = JSONObject("""{
        "id":3,"goalId":10,"parentId":2,"level":3,
        "title":"Practice interview questions","done":true,
        "doneAt":1794700900000,"xpGiven":true
    }""")
}
