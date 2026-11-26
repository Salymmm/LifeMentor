package com.lifementor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DecompositionDraftTest {
    private val goal = Goal(10, "Learn to draw", "pathfinder")
    private val milestone = TaskItem(101, goal.id, null, 1, "Practice")
    private val week = TaskItem(102, goal.id, milestone.id, 2, "Draw objects")
    private val day = TaskItem(103, goal.id, week.id, 3, "Sketch a mug")
    private val sibling = TaskItem(104, goal.id, milestone.id, 2, "Draw landscapes")
    private val draft = DecompositionDraft(goal.id, goal.title, listOf(milestone, week, day, sibling))

    @Test fun removingABranchKeepsItsParentAndSibling() {
        assertEquals(listOf(milestone, sibling), draft.withoutBranch(week.id).tasks)
        assertEquals(listOf(milestone, week, day, sibling), draft.tasks)
    }

    @Test fun confirmationAppendsEditedTasksAndPreservesManualTasks() {
        val manual = TaskItem.manual(201, goal.id, "Buy pencils", null)
        val date = 1794873600000L
        val edited = draft.withoutBranch(week.id).editTask(sibling.id, "  Draw a mountain  ", date)

        assertEquals(
            listOf(manual, milestone, sibling.copy(title = "Draw a mountain", dueDate = date)),
            edited.appendTo(goal, listOf(manual)),
        )
    }

    @Test fun aConfirmedOrInactiveGoalRejectsConfirmation() {
        for (stale in listOf(null, goal.copy(decomposed = true), goal.copy(status = "done"))) {
            assertThrows(IllegalArgumentException::class.java) { draft.appendTo(stale, emptyList()) }
        }
    }

    @Test fun confirmationRejectsAnOrphanOrBlankTitle() {
        val orphan = draft.copy(tasks = listOf(milestone, day))
        val blank = draft.copy(tasks = listOf(milestone.copy(title = " ")))
        for (invalid in listOf(orphan, blank)) {
            assertThrows(IllegalArgumentException::class.java) { invalid.appendTo(goal, emptyList()) }
        }
    }

    @Test fun retryDoesNotAppendTasksAlreadyWrittenByTheSameDraft() {
        val existing = listOf(milestone, week, day, sibling)
        assertEquals(existing, draft.appendTo(goal, existing))
        assertThrows(IllegalArgumentException::class.java) {
            draft.appendTo(goal, listOf(milestone.copy(title = "Different task")))
        }
    }
}
