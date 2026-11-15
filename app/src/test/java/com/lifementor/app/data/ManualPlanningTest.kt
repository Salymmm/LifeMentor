package com.lifementor.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

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

    @Test fun rootMilestoneAndIndependentActionStartUnscheduled() {
        val milestone = TaskItem.manual(1, 10, "  准备求职材料  ", 1, null)
        val action = TaskItem.manual(2, 10, "修改中文简历", 3, null)
        assertEquals(1, milestone.level)
        assertEquals("准备求职材料", milestone.title)
        assertEquals(3, action.level)
        assertNull(milestone.parentId)
        assertNull(action.parentId)
        assertNull(milestone.dueDate)
        assertNull(action.dueDate)
        assertFalse(action.done)
        assertFalse(action.xpGiven)
    }

    @Test fun weeklyTaskAndDailyActionKeepTheirImmediateParentsAndChosenDate() {
        val milestone = TaskItem(1, 10, null, 1, "Milestone")
        val week = TaskItem.manual(2, 10, "第一周：完善简历", 2, milestone)
        val action = TaskItem.manual(3, 10, "修改第一版简历", 3, week, 1794873600000L)
        assertEquals(1L, week.parentId)
        assertEquals(2, week.level)
        assertEquals(2L, action.parentId)
        assertEquals(1794873600000L, action.dueDate)
        assertEquals(action, TaskItem.from(JSONObject(action.toJson().toString())))
    }

    @Test fun weeklyTaskRequiresAMilestoneAndCannotSkipLevels() {
        assertThrows(IllegalArgumentException::class.java) { TaskItem.manual(2, 10, "Week", 2, null) }
        val milestone = TaskItem(1, 10, null, 1, "Milestone")
        assertThrows(IllegalArgumentException::class.java) { TaskItem.manual(2, 10, "Action", 3, milestone) }
        assertThrows(IllegalArgumentException::class.java) { TaskItem.manual(2, 10, "Milestone", 1, milestone) }
    }

    @Test fun parentFromAnotherGoalIsRejected() {
        val otherParent = TaskItem(1, 99, null, 1, "Other goal's milestone")
        assertThrows(IllegalArgumentException::class.java) { TaskItem.manual(2, 10, "Week", 2, otherParent) }
    }

    @Test fun blankTaskTitleAndInvalidLevelAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { TaskItem.manual(2, 10, "  ", 3, null) }
        assertThrows(IllegalArgumentException::class.java) { TaskItem.manual(2, 10, "Task", 4, null) }
    }
}
