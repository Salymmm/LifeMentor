package com.lifementor.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class JsonExtractTest {
    @Test
    fun acceptsAValidThreeLevelDecomposition() {
        val parsed = JsonExtract.obj(validTree())

        assertEquals(2, parsed.getJSONArray("milestones").length())
    }

    @Test
    fun rejectsADecompositionWithOnlyOneMilestone() {
        val raw = """{"milestones":[${milestone("Q1", 2)}]}"""

        assertThrows(LlmException::class.java) { JsonExtract.obj(raw) }
    }

    @Test
    fun rejectsUnknownKeysAtEveryObjectLevel() {
        val extraRootKey = validTree().replace("{\"milestones\":", "{\"unexpected\":true,\"milestones\":")
        val extraMilestoneKey = validTree().replace("\"title\":\"Q1 | Milestone\"", "\"title\":\"Q1 | Milestone\",\"extra\":true")
        val extraWeekKey = validTree().replace("\"title\":\"Week 1 | Task\"", "\"title\":\"Week 1 | Task\",\"extra\":true")

        assertThrows(LlmException::class.java) { JsonExtract.obj(extraRootKey) }
        assertThrows(LlmException::class.java) { JsonExtract.obj(extraMilestoneKey) }
        assertThrows(LlmException::class.java) { JsonExtract.obj(extraWeekKey) }
    }

    @Test
    fun requiresTheDocumentedQuarterAndWeekTitlePrefixes() {
        val wrongMilestoneTitle = validTree().replace("Q1 | Milestone", "Quarter 1 Milestone")
        val wrongWeekTitle = validTree().replace("Week 1 | Task", "First week task")

        assertThrows(LlmException::class.java) { JsonExtract.obj(wrongMilestoneTitle) }
        assertThrows(LlmException::class.java) { JsonExtract.obj(wrongWeekTitle) }
    }

    @Test
    fun acceptsTheUpperBoundsForAllThreeLevels() {
        val milestones = JSONArray()
        repeat(4) { milestoneIndex ->
            val weeks = JSONArray()
            repeat(4) { weekIndex ->
                val days = JSONArray().put("Complete practice ${weekIndex + 1}")
                    .put("Review this week's progress")
                    .put("Organize relevant materials")
                weeks.put(JSONObject().put("title", "Week ${weekIndex + 1} | Task").put("days", days))
            }
            milestones.put(
                JSONObject()
                    .put("title", "Q${milestoneIndex + 1} | Milestone")
                    .put("weeks", weeks),
            )
        }

        val parsed = JsonExtract.obj(JSONObject().put("milestones", milestones).toString())

        assertEquals(4, parsed.getJSONArray("milestones").length())
    }

    @Test
    fun rejectsAWeekWithoutDailyActions() {
        val raw = """{"milestones":[${milestone("Q1", 2, firstWeekDays = "[]")},${milestone("Q2", 2)}]}"""

        assertThrows(LlmException::class.java) { JsonExtract.obj(raw) }
    }

    @Test
    fun rejectsMoreThanThreeDailyActionsInAWeek() {
        val raw = """{"milestones":[${milestone("Q1", 2, firstWeekDays = "[\"a\",\"b\",\"c\",\"d\"]")},${milestone("Q2", 2)}]}"""

        assertThrows(LlmException::class.java) { JsonExtract.obj(raw) }
    }

    @Test
    fun rejectsDailyActionsLongerThanFifteenEnglishWords() {
        val raw = """{"milestones":[${milestone("Q1", 2, firstWeekDays = "[\"one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen\"]")},${milestone("Q2", 2)}]}"""

        assertThrows(LlmException::class.java) { JsonExtract.obj(raw) }
    }

    @Test
    fun acceptsADailyActionAtFifteenEnglishWords() {
        val raw = """{"milestones":[${milestone("Q1", 2, firstWeekDays = "[\"one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen\"]")},${milestone("Q2", 2)}]}"""

        assertEquals(2, JsonExtract.obj(raw).getJSONArray("milestones").length())
    }

    @Test
    fun rejectsAChineseDailyAction() {
        val raw = """{"milestones":[${milestone("Q1", 2, firstWeekDays = "[\"完成今天的练习\"]")},${milestone("Q2", 2)}]}"""

        assertThrows(LlmException::class.java) { JsonExtract.obj(raw) }
    }

    @Test
    fun rejectsAChineseMilestoneTitle() {
        val raw = """{"milestones":[${milestone("Q1 | 准备求职", 2)},${milestone("Q2", 2)}]}"""

        assertThrows(LlmException::class.java) { JsonExtract.obj(raw) }
    }

    @Test
    fun continuesToAcceptMarkdownFencedJson() {
        val parsed = JsonExtract.obj("```json\n${validTree()}\n```")

        assertEquals(2, parsed.getJSONArray("milestones").length())
    }

    @Test
    fun decompositionPromptMakesEveryNestedFieldMandatory() {
        assertTrue(Prompts.DECOMPOSE_SYSTEM.contains("Every title and action must be a non-empty string."))
    }

    private fun validTree(): String =
        """{"milestones":[${milestone("Q1", 2)},${milestone("Q2", 2)}]}"""

    private fun milestone(
        title: String,
        weeks: Int,
        firstWeekDays: String = "[\"Complete one practice exercise\"]",
    ): String {
        val weekItems = (1..weeks).joinToString(",") { index ->
            val days = if (index == 1) firstWeekDays else "[\"Review this week's results\"]"
            """{"title":"Week $index | Task","days":$days}"""
        }
        return """{"title":"$title | Milestone","weeks":[$weekItems]}"""
    }
}
