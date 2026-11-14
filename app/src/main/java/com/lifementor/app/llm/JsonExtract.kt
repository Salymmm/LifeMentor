package com.lifementor.app.llm

import org.json.JSONObject

object JsonExtract {
    /** Extracts the first JSON object from model text, tolerating fences or surrounding prose. */
    fun extractObject(raw: String): JSONObject {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.substringAfter("```").let {
                if (it.startsWith("json")) it.removePrefix("json") else it
            }
            s = s.substringBefore("```")
        }
        val start = s.indexOf('{')
        if (start < 0) throw LlmException("模型没有按要求返回 JSON，请重试")
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until s.length) {
            val c = s[i]
            if (esc) { esc = false; continue }
            when (c) {
                '\\' -> if (inStr) esc = true
                '"' -> inStr = !inStr
                '{' -> if (!inStr) depth++
                '}' -> if (!inStr) {
                    depth--
                    if (depth == 0) return JSONObject(s.substring(start, i + 1))
                }
            }
        }
        throw LlmException("模型返回的 JSON 不完整，请重试")
    }

    /** Extracts and validates the strict three-level goal-decomposition contract. */
    fun obj(raw: String): JSONObject = extractObject(raw).also(::validateDecomposition)

    private fun validateDecomposition(root: JSONObject) {
        requireKeys(root, setOf("milestones"), "拆解结果")
        val milestones = root.optJSONArray("milestones")
            ?: throw LlmException("拆解结果缺少 milestones 数组，请重试")
        if (milestones.length() !in 2..4) {
            throw LlmException("拆解结果的里程碑数量应为 2～4 个，请重试")
        }

        for (milestoneIndex in 0 until milestones.length()) {
            val milestone = milestones.optJSONObject(milestoneIndex)
                ?: throw LlmException("第 ${milestoneIndex + 1} 个里程碑格式不正确，请重试")
            requireKeys(milestone, setOf("title", "weeks"), "里程碑")
            val milestoneTitle = requireText(milestone, "title", "里程碑标题")
            if (!MILESTONE_TITLE.matches(milestoneTitle)) {
                throw LlmException("里程碑标题必须以 Q1～Q4、竖线 | 和非空标题开头，请重试")
            }
            val weeks = milestone.optJSONArray("weeks")
                ?: throw LlmException("第 ${milestoneIndex + 1} 个里程碑缺少 weeks 数组，请重试")
            if (weeks.length() !in 2..4) {
                throw LlmException("每个里程碑应包含 2～4 个周任务，请重试")
            }

            for (weekIndex in 0 until weeks.length()) {
                val week = weeks.optJSONObject(weekIndex)
                    ?: throw LlmException("周任务格式不正确，请重试")
                requireKeys(week, setOf("title", "days"), "周任务")
                val weekTitle = requireText(week, "title", "周任务标题")
                if (!WEEK_TITLE.matches(weekTitle)) {
                    throw LlmException("周任务标题必须以“Week N |”开头，请重试")
                }
                val days = week.optJSONArray("days")
                    ?: throw LlmException("周任务缺少 days 数组，请重试")
                if (days.length() !in 1..3) {
                    throw LlmException("每个周任务应包含 1～3 条每日行动，请重试")
                }
                for (dayIndex in 0 until days.length()) {
                    val action = days.opt(dayIndex) as? String
                    if (action.isNullOrBlank()) {
                        throw LlmException("每日行动必须是非空文本，请重试")
                    }
                    if (!isEnglishText(action)) {
                        throw LlmException("每日行动必须使用英文，仅允许英文字母、数字和标准标点，请重试")
                    }
                    if (englishWordCount(action) > MAX_DAILY_ACTION_WORDS) {
                        throw LlmException("每日行动不能超过 $MAX_DAILY_ACTION_WORDS 个英文单词，请重试")
                    }
                }
            }
        }
    }

    private fun requireKeys(value: JSONObject, expected: Set<String>, label: String) {
        val actual = value.keys().asSequence().toSet()
        if (actual != expected) throw LlmException("${label}字段不符合拆解 JSON 结构，请重试")
    }

    private fun requireText(value: JSONObject, key: String, label: String): String {
        val text = value.opt(key) as? String
        if (text.isNullOrBlank()) throw LlmException("${label}不能为空，请重试")
        val trimmed = text.trim()
        if (!isEnglishText(trimmed)) {
            throw LlmException("${label}必须使用英文，仅允许英文字母、数字和标准标点，请重试")
        }
        return trimmed
    }

    private fun englishWordCount(text: String): Int =
        Regex("[A-Za-z]+").findAll(text).count()

    private fun isEnglishText(text: String): Boolean =
        text.any { it in 'A'..'Z' || it in 'a'..'z' } &&
            text.all { it.code in 0x20..0x7E }

    private const val MAX_DAILY_ACTION_WORDS = 15
    private val MILESTONE_TITLE = Regex("^Q[1-4]\\s*\\|\\s*\\S.*$")
    private val WEEK_TITLE = Regex("^Week\\s+[1-9]\\d*\\s*\\|\\s*\\S.*$")
}
