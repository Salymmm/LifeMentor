package com.lifementor.app.notify

import com.lifementor.app.data.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReminderPolicyTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val activeGoals = setOf(10L)

    @Test
    fun todayTakesPriorityOverTomorrowAndUsesStableTaskIdOrder() {
        val tasks = listOf(task(1, today.plusDays(1)), task(4, today), task(3, today))

        assertEquals(3L, ReminderPolicy.candidate(tasks, activeGoals, today)?.id)
    }

    @Test
    fun tomorrowIsUsedWhenTodayHasNoEligibleTask() {
        val tasks = listOf(task(1, today).copy(done = true), task(2, today.plusDays(1)))

        assertEquals(2L, ReminderPolicy.candidate(tasks, activeGoals, today)?.id)
    }

    @Test
    fun ignoresCompletedOrInactiveOrNonActionOrUndatedOrOldOrLaterTasks() {
        val tasks = listOf(
            task(1, today).copy(done = true),
            task(2, today).copy(goalId = 99),
            task(3, today).copy(level = 2),
            task(4, null),
            task(5, today.minusDays(1)),
            task(6, today.plusDays(2)),
        )

        assertNull(ReminderPolicy.candidate(tasks, activeGoals, today))
    }

    @Test
    fun snoozedTaskMustStillExistBeActiveIncompleteAndHaveUnchangedDate() {
        val original = task(Long.MAX_VALUE, today)

        assertEquals(original, current(listOf(original), original))
        assertNull(current(emptyList(), original))
        assertNull(current(listOf(original.copy(done = true)), original))
        assertNull(current(listOf(original.copy(goalId = 99)), original))
        assertNull(current(listOf(original.copy(level = 2)), original))
        assertNull(current(listOf(original.copy(dueDate = null)), original))
        assertNull(current(listOf(task(Long.MAX_VALUE, today.plusDays(1))), original))
    }

    @Test
    fun testNotificationMayUseAnUnscheduledTaskWithoutLosingStaleDateCheck() {
        val task = task(7, null)

        assertEquals(task, current(listOf(task), task))
        assertNull(current(listOf(task(7, today)), task))
    }

    @Test
    fun nextEightUsesLocalCalendarTimeIncludingDstAndExactBoundary() {
        val cases = listOf(
            "2026-11-15T07:30:00+08:00[Asia/Shanghai]" to 30L * 60 * 1000,
            "2026-11-15T08:00:00+08:00[Asia/Shanghai]" to 24L * 60 * 60 * 1000,
            "2026-03-07T09:00:00-05:00[America/New_York]" to 22L * 60 * 60 * 1000,
            "2026-10-31T09:00:00-04:00[America/New_York]" to 24L * 60 * 60 * 1000,
            "2026-11-15T23:00:00-10:00[Pacific/Honolulu]" to 9L * 60 * 60 * 1000,
        )

        for ((now, expected) in cases) {
            assertEquals(now, expected, ReminderPolicy.nextDelayMillis(ZonedDateTime.parse(now)))
        }
    }

    @Test fun customTimeHonorsMinutesMidnightAndPassedOrExactTimes() {
        assertEquals(15L * 60000, ReminderPolicy.nextDelayMillis(ZonedDateTime.parse("2026-11-16T18:30:00+08:00[Asia/Shanghai]"), LocalTime.of(18, 45)))
        assertEquals(1439L * 60000, ReminderPolicy.nextDelayMillis(ZonedDateTime.parse("2026-11-16T18:46:00+08:00[Asia/Shanghai]"), LocalTime.of(18, 45)))
        assertEquals(1440L * 60000, ReminderPolicy.nextDelayMillis(ZonedDateTime.parse("2026-11-16T18:45:00+08:00[Asia/Shanghai]"), LocalTime.of(18, 45)))
        assertEquals(20L * 60000, ReminderPolicy.nextDelayMillis(ZonedDateTime.parse("2026-11-16T23:50:00+08:00[Asia/Shanghai]"), LocalTime.of(0, 10)))
    }

    @Test fun dayAfterDstGapReturnsToTheChosenNominalTime() {
        assertEquals(1350L * 60000, ReminderPolicy.nextDelayMillis(
            ZonedDateTime.parse("2026-03-08T04:00:00-04:00[America/New_York]"), LocalTime.of(2, 30)))
    }

    private fun current(tasks: List<TaskItem>, expected: TaskItem) =
        ReminderPolicy.currentTask(tasks, activeGoals, expected.id, expected.dueDate)

    private fun task(id: Long, date: LocalDate?) = TaskItem(
        id = id, goalId = 10, parentId = null, level = 3, title = "行动 $id",
        dueDate = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
}
