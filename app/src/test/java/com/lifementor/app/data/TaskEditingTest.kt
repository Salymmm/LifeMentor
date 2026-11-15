package com.lifementor.app.data

import org.junit.Assert.*
import org.junit.Test

class TaskEditingTest {
    @Test fun editingKeepsProgressParentRewardTierAndIdentity() {
        val task = TaskItem(3, 10, 2, 3, "Old", true, 1234, true, 5678)
        assertEquals(task.copy(title = "新任务", dueDate = null), task.edited(" 新任务 ", null))
        assertEquals(task.copy(title = "New", dueDate = 9000), task.edited("New", 9000))
    }

    @Test fun blankTaskNameIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { TaskItem(1, 10, null, 3, "Task").edited(" \n ", null) }
    }

    @Test fun deletionSelectsOnlyTheRequestedBranch() {
        val tasks = listOf(
            TaskItem(1, 10, null, 3, "Root"), TaskItem(2, 10, 1, 3, "Child"),
            TaskItem(3, 10, 2, 3, "Grandchild"), TaskItem(4, 10, 1, 3, "Sibling"),
            TaskItem(5, 20, null, 3, "Other goal"),
        )
        assertEquals(setOf(2L, 3L), taskBranchIds(tasks, 2))
        assertEquals(setOf(1L, 2L, 3L, 4L), taskBranchIds(tasks, 1))
        assertEquals(setOf(4L), taskBranchIds(tasks, 4))
        assertTrue(taskBranchIds(tasks, 99).isEmpty())
    }

    @Test fun deletingDeepSubtasksDoesNotOverflowTheStack() {
        val tasks = (1L..10000L).map { TaskItem(it, 10, if (it == 1L) null else it - 1, 3, "Task") }
        assertEquals((5000L..10000L).toSet(), taskBranchIds(tasks, 5000))
    }
}
