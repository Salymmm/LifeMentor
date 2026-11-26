package com.lifementor.app.llm

import com.lifementor.app.data.Goal
import com.lifementor.app.data.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.json.JSONArray
import org.json.JSONObject

internal enum class TaskQueryScope {
    TODAY_DUE, TODAY_COMPLETED, OVERDUE, UNSCHEDULED, GOAL, ALL,
}

internal data class TaskQueryRequest(
    val scopes: Set<TaskQueryScope>,
    val goalId: Long? = null,
    val offset: Int = 0,
    val includeClosedGoals: Boolean = false,
    val afterTaskId: Long? = null,
)

internal data class TaskQueryPage(
    val context: String,
    val total: Int,
    val shown: Int,
    val nextOffset: Int?,
    val lastTaskId: Long? = null,
)

internal object MentorTaskQuery {
    private const val PAGE_SIZE = 20
    private const val PAGE_CHARACTERS = 8_000
    private const val TITLE_CHARACTERS = 500

    private val taskWords = Regex("""\b(tasks?|actions?|to-?dos?|agenda)\b|任务|待办|行动|日程""", RegexOption.IGNORE_CASE)
    private val todayWords = Regex("""\btoday\b|今天|今日""", RegexOption.IGNORE_CASE)
    private val completedWords = Regex("""\b(complete(?:d)?|finish(?:ed)?|done)\b|(?<!未)(?<!没)完成|做完""", RegexOption.IGNORE_CASE)
    private val pendingWords = Regex("""\b(due|pending|unfinished|remaining|left|haven't|not done|not completed|not finished|to (?:complete|finish)|should (?:i )?(?:complete|finish))\b|到期|没做|未完成|还没完成|剩下""", RegexOption.IGNORE_CASE)
    private val overdueWords = Regex("""\b(overdue|past due)\b|逾期|超期""", RegexOption.IGNORE_CASE)
    private val unscheduledWords = Regex("""\b(unscheduled|no (due )?date|without a (due )?date)\b|未排期|没排期|没有日期|没定日期""", RegexOption.IGNORE_CASE)
    private val lookupWords = Regex("""\b(what|which|show|list|how many|check|view|status|progress|are any|do i have)\b|哪些|什么|多少|查看|列出|进度|情况""", RegexOption.IGNORE_CASE)
    private val optOut = Regex("""\b(don['’]t|do not|never) (show|list|share|read|send|include|fetch)\b|(?:不用|不要|别)(?:读取|发送|查看|列出|显示|分享)""", RegexOption.IGNORE_CASE)
    private val creationIntent = Regex("""\b(brainstorm|come up with|should i (?:create|add)|suggest new|generate new)\b|新建什么|想几个|建议新增""", RegexOption.IGNORE_CASE)
    private val fullTaskList = Regex("""\b(?:all|every) (?:of )?my (?:tasks?|actions?|to-?dos?)\b|\bmy (?:full|complete) (?:task|to-?do) list\b|我(?:的)?(?:全部|所有)(?:任务|待办|行动)|(?:全部|所有)我的(?:任务|待办|行动)""", RegexOption.IGNORE_CASE)
    private val shortFullList = Regex("""(?:(?:show|list|view)(?: me)? )?(?:all|every) (?:tasks?|actions?|to-?dos?)|(?:列出|查看)?(?:全部|所有)(?:任务|待办|行动)(?:明细)?""", RegexOption.IGNORE_CASE)
    private val shortOverdueQuestion = Regex("""(?:what(?:['’]s| is)|which (?:ones )?are) (?:overdue|past due)|(?:有哪些|哪些)逾期(?:了)?""", RegexOption.IGNORE_CASE)
    private val shortTodayQuestion = Regex("""(?:what(?:['’]s| is) due|what do i (?:need|have) to do|what (?:have|did) i (?:finish(?:ed)?|complete(?:d)?)) today|(?:我)?(?:今天|今日)(?:做完了?|完成了?|要做)(?:什么|哪些)""", RegexOption.IGNORE_CASE)
    private val allGoals = Regex("""\b(all goals|(?:completed|closed|archived) goals)\b|所有目标|全部目标|已完成(?:的)?目标""", RegexOption.IGNORE_CASE)
    private val goalQualifier = Regex("""\bunder \S+|\b(?:for|in) (?:my |the )?[^,?]{0,80}\bgoal\b|[「“"][^」”"]+[」”"](?:这个)?目标|(?:这个|该)目标""", RegexOption.IGNORE_CASE)
    private val clauseBoundary = Regex("""[，,。；;?!？！]|\b(?:and|or)\b|以及|或者""", RegexOption.IGNORE_CASE)

    fun allowsTaskDetails(question: String): Boolean = !optOut.containsMatchIn(question)

    fun infer(question: String, goals: List<Goal>): TaskQueryRequest? {
        if (!allowsTaskDetails(question) || creationIntent.containsMatchIn(question)) return null
        val mentionsTasks = taskWords.containsMatchIn(question)
        val shortQuestion = question.trim().trimEnd('?', '？', '.', '。')
        val listOnly = shortFullList.matches(shortQuestion)
        val directOverdue = shortOverdueQuestion.matches(shortQuestion)
        val directToday = shortTodayQuestion.matches(shortQuestion)
        if (!lookupWords.containsMatchIn(question) && !listOnly && !directOverdue && !directToday) return null
        if (!mentionsTasks && !directOverdue && !directToday) return null

        val goalId = if (mentionsTasks) goals.asSequence()
            .filter { it.title.isNotBlank() && namesGoal(question, it.title) }
            .maxByOrNull { it.title.length }?.id
        else null
        val includeClosedGoals = allGoals.containsMatchIn(question)
        if (goalId == null && !includeClosedGoals && goalQualifier.containsMatchIn(question)) return null

        val scopes = buildSet {
            if (overdueWords.containsMatchIn(question)) add(TaskQueryScope.OVERDUE)
            if (unscheduledWords.containsMatchIn(question) && mentionsTasks) add(TaskQueryScope.UNSCHEDULED)
            if (todayWords.containsMatchIn(question)) {
                val parts = question.split(clauseBoundary)
                if (parts.any { completedWords.containsMatchIn(it) && !pendingWords.containsMatchIn(it) }) {
                    add(TaskQueryScope.TODAY_COMPLETED)
                }
                if (parts.any {
                        todayWords.containsMatchIn(it) && (mentionsTasks || directToday) &&
                            !overdueWords.containsMatchIn(it) && !unscheduledWords.containsMatchIn(it) &&
                            (pendingWords.containsMatchIn(it) || !completedWords.containsMatchIn(it))
                    }) add(TaskQueryScope.TODAY_DUE)
            }
            if (isEmpty() && goalId != null) add(TaskQueryScope.GOAL)
            if (isEmpty() && (listOnly || fullTaskList.containsMatchIn(question))) add(TaskQueryScope.ALL)
        }
        return scopes.takeIf { it.isNotEmpty() }?.let {
            TaskQueryRequest(it, goalId, includeClosedGoals = includeClosedGoals)
        }
    }

    private fun namesGoal(question: String, title: String): Boolean =
        if (title.any { it in '\u3400'..'\u9FFF' }) question.contains(title, ignoreCase = true)
        else Regex("""(?<![\p{L}\p{N}_])""" + Regex.escape(title) + """(?![\p{L}\p{N}_])""", RegexOption.IGNORE_CASE)
            .containsMatchIn(question)

    fun page(
        request: TaskQueryRequest,
        tasks: List<TaskItem>,
        goals: List<Goal>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): TaskQueryPage {
        val goalsById = goals.associateBy { it.id }
        val tasksById = tasks.associateBy { it.id }
        val matches = tasks.asSequence()
            .filter { request.goalId == null || it.goalId == request.goalId }
            .map { task ->
                val due = task.dueDate?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                val completed = task.doneAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
                val goal = goalsById[task.goalId]
                Row(task, goal, due, completed, !task.done && due != null && due.isBefore(today))
            }
            .filter { row ->
                request.scopes.any { scope ->
                    when (scope) {
                        TaskQueryScope.TODAY_DUE -> row.due == today
                        TaskQueryScope.TODAY_COMPLETED -> row.task.done && row.completed?.toLocalDate() == today
                        TaskQueryScope.OVERDUE -> row.overdue &&
                            (row.goal?.status == "active" || request.goalId != null || request.includeClosedGoals)
                        TaskQueryScope.UNSCHEDULED -> row.due == null
                        TaskQueryScope.GOAL -> request.goalId != null
                        TaskQueryScope.ALL -> true
                    }
                }
            }
            .sortedBy { it.task.id }
            .toList()

        val offset = request.afterTaskId?.let { id ->
            matches.indexOfFirst { it.task.id > id }.takeIf { it >= 0 } ?: matches.size
        } ?: request.offset.coerceIn(0, matches.size)
        val rows = JSONArray()
        var characters = 2
        for (row in matches.drop(offset).take(PAGE_SIZE)) {
            val json = row.toJson(tasksById)
            val length = json.toString().length + 1
            if (rows.length() > 0 && characters + length > PAGE_CHARACTERS) break
            rows.put(json)
            characters += length
        }
        val shown = rows.length()
        val end = offset + shown
        val range = if (shown == 0) "0" else "${offset + 1}–$end"
        return TaskQueryPage(
            context = "Task records for $today ($zone), scopes ${request.scopes.joinToString()}. " +
                "Showing $range of ${matches.size} matching records.\n$rows",
            total = matches.size,
            shown = shown,
            nextOffset = end.takeIf { it < matches.size },
            lastTaskId = matches.getOrNull(end - 1)?.task?.id.takeIf { shown > 0 },
        )
    }

    private data class Row(
        val task: TaskItem,
        val goal: Goal?,
        val due: LocalDate?,
        val completed: ZonedDateTime?,
        val overdue: Boolean,
    ) {
        fun toJson(tasksById: Map<Long, TaskItem>): JSONObject {
            val parent = task.parentId?.let(tasksById::get)
            return JSONObject()
                .put("id", task.id)
                .put("title", task.title.forContext())
                .put("goalId", task.goalId)
                .put("goal", goal?.title?.forContext() ?: JSONObject.NULL)
                .put("goalStatus", goal?.status ?: JSONObject.NULL)
                .put("parentId", task.parentId ?: JSONObject.NULL)
                .put("parent", parent?.title?.forContext() ?: JSONObject.NULL)
                .put("level", task.level)
                .put("dueDate", due?.toString() ?: JSONObject.NULL)
                .put("done", task.done)
                .put("completedAt", completed?.toOffsetDateTime()?.toString() ?: JSONObject.NULL)
                .put("pastDue", overdue)
        }
    }

    private fun String.forContext() =
        if (length <= TITLE_CHARACTERS) this else take(TITLE_CHARACTERS) + "…"
}
