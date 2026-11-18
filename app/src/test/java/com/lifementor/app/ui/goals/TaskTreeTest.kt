package com.lifementor.app.ui.goals

import com.lifementor.app.data.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskTreeTest {
    private val tasks = listOf(
        TaskItem(1, 10, null, 1, "First milestone"),
        TaskItem(2, 10, 1, 2, "First week"),
        TaskItem(3, 10, 2, 3, "First action"),
        TaskItem(4, 10, 1, 2, "Second week"),
        TaskItem(5, 10, 4, 3, "Second action"),
        TaskItem(6, 10, null, 1, "Next milestone"),
        TaskItem(7, 10, null, 3, "Independent manual action"),
        TaskItem(8, 10, 2, 3, "Later manual action in first week"),
    )

    @Test fun collapsedTreeShowsMilestonesAndIndependentManualActions() {
        assertEquals(listOf(1L, 6L, 7L), visibleTaskRows(tasks, emptySet()).map { it.task.id })
    }

    @Test fun expandingMilestoneKeepsWeeklyActionsCollapsed() {
        assertEquals(listOf(1L, 2L, 4L, 6L, 7L), visibleTaskRows(tasks, setOf(1)).map { it.task.id })
    }

    @Test fun expandedWeekPlacesAppendedManualActionWithItsParent() {
        assertEquals(listOf(1L, 2L, 3L, 8L, 4L, 6L, 7L),
            visibleTaskRows(tasks, setOf(1, 2)).map { it.task.id })
    }

    @Test fun collapsedParentHidesDescendantsEvenIfChildWasExpanded() {
        assertEquals(listOf(1L, 6L, 7L), visibleTaskRows(tasks, setOf(2)).map { it.task.id })
    }

    @Test fun deeplyNestedSubtasksDoNotOverflowTheCallStack() {
        val deepTasks = (1L..10000L).map { id ->
            TaskItem(id, 10, if (id == 1L) null else id - 1, 3, "Subtask $id")
        }
        val visible = visibleTaskRows(deepTasks, deepTasks.map { it.id }.toSet())
        assertEquals(deepTasks.map { it.id }, visible.map { it.task.id })
        assertEquals(9999, visible.last().depth)
    }

    @Test fun depthComesFromParentRelationshipsRegardlessOfLegacyRewardTier() {
        val manualTasks = listOf(
            TaskItem(1, 10, null, 3, "Root task"),
            TaskItem(2, 10, 1, 3, "Subtask"),
            TaskItem(3, 10, 2, 3, "Nested subtask"),
            TaskItem(4, 10, null, 3, "Another root"),
        )
        val rows = visibleTaskRows(manualTasks, setOf(1, 2))
        assertEquals(listOf(1L, 2L, 3L, 4L), rows.map { it.task.id })
        assertEquals(listOf(0, 1, 2, 0), rows.map { it.depth })

        val legacyRows = visibleTaskRows(tasks, setOf(1, 2, 4))
        assertEquals(listOf(0, 1, 2, 2, 1, 2, 0, 0), legacyRows.map { it.depth })
    }
}
