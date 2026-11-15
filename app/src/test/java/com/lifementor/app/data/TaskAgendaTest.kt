package com.lifementor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

class TaskAgendaTest {
    private val today = LocalDate.of(2026, 11, 15)
    private fun task(id: Long, day: LocalDate? = today, goalId: Long = 1, level: Int = 3, done: Boolean = false) =
        TaskItem(id, goalId, null, level, "Action $id", done = done,
            dueDate = day?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())

    @Test fun partitionsDatesAndFiltersCompletedInactiveAndNonDailyTasks() {
        val groups = TaskAgenda.groups(listOf(
            task(1), task(2, today.minusDays(1)), task(3, today.plusDays(1)), task(4, null),
            task(5, done = true), task(6, goalId = 2), task(7, level = 2),
        ), setOf(1), today)
        assertEquals(listOf(1L), groups.today.map { it.id })
        assertEquals(listOf(2L), groups.overdue.map { it.id })
        assertEquals(listOf(3L), groups.future.map { it.id })
        assertEquals(listOf(4L), groups.unscheduled.map { it.id })
    }

    @Test fun keepsAllTasksAndSortsFutureByDate() {
        val groups = TaskAgenda.groups((1L..12L).map { task(it) } +
            listOf(task(20, today.plusDays(3)), task(21, today.plusDays(1))), setOf(1), today)
        assertEquals(12, groups.today.size)
        assertEquals(listOf(21L, 20L), groups.future.map { it.id })
    }

    @Test fun groupsCalendarDatesWithoutUsingTheDefaultTimeZoneToDecodeThem() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("America/Los_Angeles", "Pacific/Kiritimati")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals(listOf(1L), TaskAgenda.groups(listOf(task(1)), setOf(1), today).today.map { it.id })
            }
        } finally { TimeZone.setDefault(original) }
    }

    @Test fun completionIsIdempotentAndPreservesDateAndIdentity() {
        val original = task(1)
        val completed = original.completed(1000)
        assertTrue(completed.done)
        assertEquals(1000L, completed.doneAt)
        assertEquals(original.dueDate, completed.dueDate)
        assertEquals(original.id, completed.id)
        assertEquals(completed, completed.completed(2000))
        assertFalse(original.done)
    }
}
