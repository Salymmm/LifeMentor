package com.lifementor.app.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarSyncPlanTest {
    private val goal = Goal(10, "目标", "scholar")
    private val task = TaskItem(1, goal.id, null, 3, "修改简历", dueDate = TaskAgenda.dateMillis(LocalDate.of(2026, 11, 16)))
    private fun desired(tasks: List<TaskItem>) = CalendarSyncPlanner.desired(CalendarSyncSource(listOf(goal), tasks))
    private fun owned(item: CalendarSyncItem, id: Long = 100) = OwnedCalendarEvent(id, item.taskId, item.draft)

    @Test fun includesAllMonthsTiersAndCompletedGoalsButSkipsUndatedTasks() {
        val source = CalendarSyncSource(listOf(goal.copy(status = "done")), listOf(task,
            task.copy(id = 2, level = 1, done = true, dueDate = TaskAgenda.dateMillis(LocalDate.of(2027, 1, 1))),
            task.copy(id = 3, dueDate = null)))
        val items = CalendarSyncPlanner.desired(source)
        assertEquals(listOf(1L, 2L), items.map { it.taskId })
        assertTrue(items.last().draft.description.contains("已完成"))
    }

    @Test fun initialSyncCreatesAndRepeatedSyncHasNoWrites() {
        val items = desired(listOf(task))
        val first = CalendarSyncPlanner.plan(items, emptyList())
        assertEquals(items, first.inserts)
        val second = CalendarSyncPlanner.plan(items, first.inserts.map { owned(it) })
        assertTrue(second.inserts.isEmpty())
        assertTrue(second.updates.isEmpty())
        assertTrue(second.deletes.isEmpty())
        assertEquals(1, second.unchanged)
    }

    @Test fun titleDateAndCompletionChangesUpdateTheSameEvent() {
        val old = owned(desired(listOf(task)).single())
        val current = desired(listOf(task.copy(title = "新版简历", done = true, dueDate = task.dueDate!! + 86400000)))
        val plan = CalendarSyncPlanner.plan(current, listOf(old))
        assertTrue(plan.inserts.isEmpty())
        assertEquals(old.id, plan.updates.single().event.id)
        assertEquals(current.single(), plan.updates.single().item)
    }

    @Test fun deletionAndDateClearingRemoveOnlyCorrespondingManagedEvents() {
        val previous = desired(listOf(task, task.copy(id = 2), task.copy(id = 3)))
        val current = desired(listOf(task, task.copy(id = 2, dueDate = null)))
        val plan = CalendarSyncPlanner.plan(current, previous.map { owned(it, it.taskId + 100) })
        assertEquals(setOf(102L, 103L), plan.deletes.map { it.id }.toSet())
        assertEquals(1, plan.unchanged)
    }

    @Test fun duplicateManagedEventsAreRepairedWithoutReinserting() {
        val item = desired(listOf(task)).single()
        val plan = CalendarSyncPlanner.plan(listOf(item), listOf(owned(item, 101), owned(item, 100)))
        assertEquals(listOf(101L), plan.deletes.map { it.id })
        assertTrue(plan.inserts.isEmpty())
        assertEquals(1, plan.unchanged)
    }

    @Test fun externalTimeChangesAreReplacedByAppAllDayDates() {
        val item = desired(listOf(task)).single()
        val plan = CalendarSyncPlanner.plan(listOf(item), listOf(owned(item).copy(allDay = false, timezone = "Asia/Shanghai")))
        assertEquals(1, plan.updates.size)
    }

    @Test fun missingGoalCannotBecomeASilentDeletionSnapshot() {
        assertThrows(IllegalArgumentException::class.java) {
            CalendarSyncPlanner.desired(CalendarSyncSource(emptyList(), listOf(task)))
        }
    }

    @Test fun identityRejectsAnotherInstallationAndUnmanagedOrMalformedEvents() {
        val identity = CalendarSyncIdentity("one")
        assertEquals(123L, identity.taskId(identity.uri(123)))
        assertNull(identity.taskId(CalendarSyncIdentity("two").uri(123)))
        assertNull(identity.taskId("lifementor://task/123"))
        assertNull(identity.taskId(identity.uri(123) + "/other"))
        assertNull(identity.taskId("lifementor://calendar-sync/one/+123"))
        assertNull(identity.taskId(null))
    }

    @Test fun duplicateTaskIdsAbortBeforeAnyCalendarPlanIsApplied() {
        assertThrows(IllegalArgumentException::class.java) { desired(listOf(task, task.copy(title = "Duplicate ID"))) }
    }

    @Test fun calendarSideRecurrenceMustBeRestoredToASingleTaskEvent() {
        val item = desired(listOf(task)).single()
        val plan = CalendarSyncPlanner.plan(listOf(item), listOf(owned(item).copy(recurring = true)))
        assertEquals(1, plan.updates.size)
        assertEquals(0, plan.unchanged)
    }
}
