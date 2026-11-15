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
        assertEquals(listOf(1L, 6L, 7L), visibleTaskRows(tasks, emptySet()).map { it.id })
    }

    @Test fun expandingMilestoneKeepsWeeklyActionsCollapsed() {
        assertEquals(listOf(1L, 2L, 4L, 6L, 7L), visibleTaskRows(tasks, setOf(1)).map { it.id })
    }

    @Test fun expandedWeekPlacesAppendedManualActionWithItsParent() {
        assertEquals(listOf(1L, 2L, 3L, 8L, 4L, 6L, 7L),
            visibleTaskRows(tasks, setOf(1, 2)).map { it.id })
    }

    @Test fun collapsedParentHidesDescendantsEvenIfChildWasExpanded() {
        assertEquals(listOf(1L, 6L, 7L), visibleTaskRows(tasks, setOf(2)).map { it.id })
    }
}
