package com.lifementor.app.data

import com.lifementor.app.notify.ReminderPolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class ManualPlanningTest {
    @Test fun goalEditingPreservesIdentityCompletionAndAiState() {
        val original = Goal(10, "Old goal", "pathfinder", "done", 1234, true)
        val updated = original.edited("  找到实习  ", "scholar")
        assertEquals(original.copy(title = "找到实习", domain = "scholar"), updated)
        assertEquals(updated, Goal.from(JSONObject(updated.toJson().toString())))
    }

    @Test fun blankGoalNameIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Goal(10, "Goal", "scholar").edited(" \n ", "scholar") }
    }

    @Test fun rootTasksStartUnscheduledAndUseTheActionRewardTier() {
        val task = TaskItem.manual(1, 10, "  准备求职材料  ", null)
        val action = TaskItem.manual(2, 10, "修改中文简历", null)
        assertEquals(3, task.level)
        assertEquals(10, Leveling.xpForLevel(task.level))
        assertEquals("准备求职材料", task.title)
        assertEquals(3, action.level)
        assertNull(task.parentId)
        assertNull(action.parentId)
        assertNull(task.dueDate)
        assertNull(action.dueDate)
        assertFalse(action.done)
        assertFalse(action.xpGiven)
    }

    @Test fun subtasksCanContinuePastThreeLayersAndKeepDatesAndChineseText() {
        var parent: TaskItem? = null
        val tasks = (1L..8L).map { id ->
            TaskItem.manual(id, 10, "子任务 $id", parent, if (id == 8L) 1794873600000L else null)
                .also { parent = it }
        }
        assertEquals(listOf(null, 1L, 2L, 3L, 4L, 5L, 6L, 7L), tasks.map { it.parentId })
        assertEquals(List(8) { 3 }, tasks.map { it.level })
        assertEquals(1794873600000L, tasks.last().dueDate)
        assertEquals(tasks, tasks.map { TaskItem.from(JSONObject(it.toJson().toString())) })
    }

    @Test fun manualChildCanBeAddedUnderAnyExistingAiTier() {
        for (tier in 1..3) {
            val parent = TaskItem(1, 10, null, tier, "Existing AI task", done = true, xpGiven = true)
            val child = TaskItem.manual(2, 10, "手动子任务", parent)
            assertEquals(parent.id, child.parentId)
            assertEquals(3, child.level)
            assertFalse(child.done)
            assertFalse(child.xpGiven)
            assertEquals(parent, TaskItem.from(JSONObject(parent.toJson().toString())))
        }
    }

    @Test fun parentFromAnotherGoalIsRejected() {
        val otherParent = TaskItem(1, 99, null, 1, "Other goal's milestone")
        assertThrows(IllegalArgumentException::class.java) { TaskItem.manual(2, 10, "子任务", otherParent) }
    }

    @Test fun blankTaskTitleIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { TaskItem.manual(2, 10, "  ", null) }
    }

    @Test fun deepManualSubtaskParticipatesInAgendaAndReminders() {
        val today = LocalDate.of(2026, 11, 16)
        val date = TaskAgenda.dateMillis(today)
        var parent: TaskItem? = null
        val tasks = (1L..8L).map { id ->
            TaskItem.manual(id, 10, "Task $id", parent, if (id == 8L) date else null)
                .also { parent = it }
        }
        assertEquals(listOf(tasks.last()), TaskAgenda.groups(tasks, setOf(10), today).today)
        assertEquals(tasks.last(), ReminderPolicy.candidate(tasks, setOf(10), today))
        assertEquals(tasks.last(), ReminderPolicy.currentTask(tasks, setOf(10), 8, date))
    }
}
