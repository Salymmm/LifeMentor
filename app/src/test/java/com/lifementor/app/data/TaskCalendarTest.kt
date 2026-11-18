package com.lifementor.app.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TaskCalendarTest {
    @Test fun leapFebruaryHasMondayFirstPaddingAndAllDaysExactlyOnce() {
        val cells = TaskCalendar.monthCells(YearMonth.of(2024, 2))
        assertEquals(35, cells.size)
        assertEquals(listOf(null, null, null), cells.take(3))
        assertEquals((1..29).map { LocalDate.of(2024, 2, it) }, cells.filterNotNull())
        assertEquals(listOf(null, null, null), cells.takeLast(3))
    }

    @Test fun mondayStartingFourWeekMonthNeedsNoExtraRow() {
        val cells = TaskCalendar.monthCells(YearMonth.of(2021, 2))
        assertEquals(28, cells.size)
        assertEquals(LocalDate.of(2021, 2, 1), cells.first())
        assertEquals(LocalDate.of(2021, 2, 28), cells.last())
    }

    @Test fun monthStartingSundayPlacesFirstDayInLastColumn() {
        val cells = TaskCalendar.monthCells(YearMonth.of(2026, 11))
        assertEquals(42, cells.size)
        assertEquals(List<LocalDate?>(6) { null }, cells.take(6))
        assertEquals(LocalDate.of(2026, 11, 1), cells[6])
    }

    @Test fun datedActiveTasksKeepAllTiersAndCompletedHistoryWithOpenTasksFirst() {
        val date = LocalDate.of(2026, 11, 16)
        val task = TaskItem(1, 10, null, 3, "Task", dueDate = TaskAgenda.dateMillis(date))
        val tasks = listOf(task.copy(id = 3, done = true), task.copy(id = 2, level = 1),
            task, task.copy(id = 4, goalId = 99), task.copy(id = 5, dueDate = null),
            task.copy(id = 6, dueDate = TaskAgenda.dateMillis(date.plusDays(1))))
        val grouped = TaskCalendar.byDate(tasks, setOf(10))
        assertEquals(listOf(1L, 2L, 3L), grouped[date]?.map { it.id })
        assertEquals(listOf(6L), grouped[date.plusDays(1)]?.map { it.id })
        assertEquals(2, grouped.size)
        assertTrue(TaskCalendar.byDate(tasks, emptySet()).isEmpty())
    }

    @Test fun allDayDraftPreservesChineseAndUtcDateAcrossDstAndYearBoundary() {
        for (date in listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 12, 31))) {
            val task = TaskItem(1, 10, null, 3, "修改中文简历", dueDate = TaskAgenda.dateMillis(date))
            val event = TaskCalendar.eventDraft(task, "找到实习")
            assertEquals(task.title, event.title)
            assertTrue(event.description.contains("找到实习"))
            assertEquals(TaskAgenda.dateMillis(date), event.begin)
            assertEquals(TaskAgenda.dateMillis(date.plusDays(1)), event.end)
            assertEquals(86400000L, event.end - event.begin)
        }
    }

    @Test fun undatedTaskCannotSilentlyBecomeAnEventToday() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskCalendar.eventDraft(TaskItem(1, 10, null, 3, "Unscheduled"), "Goal")
        }
    }
}
