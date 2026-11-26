package com.lifementor.app.llm

import com.lifementor.app.data.Goal
import com.lifementor.app.data.TaskAgenda
import com.lifementor.app.data.TaskItem
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MentorTaskQueryTest {
    private val day = LocalDate.of(2026, 11, 26)
    private val zone = ZoneId.of("Asia/Shanghai")
    private val goals = listOf(Goal(1, "Job Search", "pathfinder"), Goal(2, "Previous Goal", "pathfinder", "done"))

    @Test
    fun dateScopesDistinguishDueTodayFromCompletedTodayAndExcludeClosedGoalsFromOverdue() {
        val tasks = listOf(
            task(1, "Due today", date = day),
            task(2, "Finished today", date = day.minusDays(1), done = true),
            task(3, "Past due", date = day.minusDays(1)),
            task(4, "Archived", goal = 2, date = day.minusDays(1)),
            task(5, "No date"),
        )
        val due = query(TaskQueryScope.TODAY_DUE, tasks)
        val completed = query(TaskQueryScope.TODAY_COMPLETED, tasks)
        val overdue = query(TaskQueryScope.OVERDUE, tasks)
        val unscheduled = query(TaskQueryScope.UNSCHEDULED, tasks)
        assertEquals(listOf("Due today"), titles(due))
        assertEquals(listOf("Finished today"), titles(completed))
        assertEquals(listOf("Past due"), titles(overdue))
        assertEquals(listOf("Past due", "Archived"), titles(page(
            MentorTaskQuery.infer("Show overdue tasks across all goals, including completed goals", goals)!!, tasks,
        )))
        assertEquals(listOf("No date"), titles(unscheduled))
        assertEquals(day.atStartOfDay(zone).toOffsetDateTime().toString(), JSONArray(completed.context.substringAfter('\n')).getJSONObject(0).getString("completedAt"))
        assertTrue(query(TaskQueryScope.ALL, tasks).context.contains("Archived"))
        assertEquals(listOf("Archived"), titles(page(TaskQueryRequest(setOf(TaskQueryScope.GOAL), goalId = 2), tasks)))
    }

    @Test
    fun largeQueryReportsMatchesAndAdvancesWithoutRepeatingRows() {
        val tasks = (1L..35L).map { task(it, "Task $it") }
        val first = query(TaskQueryScope.ALL, tasks)
        assertEquals(35, first.total)
        assertEquals(first.shown, titles(first).size)
        assertNotNull(first.nextOffset)
        val second = page(TaskQueryRequest(setOf(TaskQueryScope.ALL), offset = first.nextOffset!!), tasks)
        assertFalse(titles(first).any { it in titles(second) })
        assertEquals(35, first.shown + second.shown)
        assertNull(second.nextOffset)
    }

    @Test
    fun recognizesTaskQuestionsWithoutSharingDetailsForOrdinaryConversation() {
        assertNull(MentorTaskQuery.infer("I feel anxious about the interview today", goals))
        assertNull(MentorTaskQuery.infer("Help me come up with new tasks for a project", goals))
        assertNull(MentorTaskQuery.infer("What does overdue mean on an invoice?", goals))
        assertNull(MentorTaskQuery.infer("How many credit hours have I completed today?", goals))
        assertNull(MentorTaskQuery.infer("What are all the tasks for a road trip?", goals))
        assertNull(MentorTaskQuery.infer("Please don't show all my tasks.", goals))
        assertNull(MentorTaskQuery.infer("How do I create tasks today?", goals))
        assertNull(MentorTaskQuery.infer("How can I break down my Job Search tasks?", goals))
        assertNull(MentorTaskQuery.infer("Can you help me brainstorm tasks for today?", goals))
        assertEquals(setOf(TaskQueryScope.OVERDUE), MentorTaskQuery.infer("What's overdue?", goals)?.scopes)
        assertEquals(setOf(TaskQueryScope.TODAY_COMPLETED, TaskQueryScope.OVERDUE),
            MentorTaskQuery.infer("今天完成了哪些任务，哪些任务逾期了？", goals)?.scopes)
        assertEquals(setOf(TaskQueryScope.TODAY_DUE),
            MentorTaskQuery.infer("What tasks are on my list today?", goals)?.scopes)
        assertEquals(setOf(TaskQueryScope.TODAY_DUE),
            MentorTaskQuery.infer("What tasks should I finish today?", goals)?.scopes)
        assertEquals(setOf(TaskQueryScope.TODAY_COMPLETED),
            MentorTaskQuery.infer("What did I complete today?", goals)?.scopes)
        assertEquals(setOf(TaskQueryScope.TODAY_DUE, TaskQueryScope.TODAY_COMPLETED, TaskQueryScope.OVERDUE),
            MentorTaskQuery.infer("今天有哪些任务，哪些完成了，哪些逾期？", goals)?.scopes)
        assertEquals(1L, MentorTaskQuery.infer("Show all tasks under Job Search", goals)?.goalId)
        assertNull(MentorTaskQuery.infer("Show me workout tasks", listOf(Goal(3, "Work", "pathfinder"))))
        assertEquals(setOf(TaskQueryScope.ALL), MentorTaskQuery.infer("Show me all my tasks", goals)?.scopes)
    }

    private fun task(id: Long, title: String, goal: Long = 1, date: LocalDate? = null, done: Boolean = false) =
        TaskItem(id, goal, null, 3, title, done = done,
            doneAt = if (done) day.atStartOfDay(zone).toInstant().toEpochMilli() else null,
            dueDate = date?.let(TaskAgenda::dateMillis))

    private fun query(scope: TaskQueryScope, tasks: List<TaskItem>) =
        page(TaskQueryRequest(setOf(scope)), tasks)

    private fun page(request: TaskQueryRequest, tasks: List<TaskItem>) =
        MentorTaskQuery.page(request, tasks, goals, day, zone)

    private fun titles(page: TaskQueryPage): List<String> {
        val rows = JSONArray(page.context.substringAfter('\n'))
        return (0 until rows.length()).map { rows.getJSONObject(it).getString("title") }
    }
}
