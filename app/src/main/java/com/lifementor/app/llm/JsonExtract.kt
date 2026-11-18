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
        if (start < 0) throw LlmException("The model did not return JSON as required. Please retry.")
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
        throw LlmException("The model returned incomplete JSON. Please retry.")
    }

    /** Extracts and validates the strict three-level goal-decomposition contract. */
    fun obj(raw: String): JSONObject = extractObject(raw).also(::validateDecomposition)

    private fun validateDecomposition(root: JSONObject) {
        requireKeys(root, setOf("milestones"), "decomposition result")
        val milestones = root.optJSONArray("milestones")
            ?: throw LlmException("Decomposition result is missing the milestones array. Please retry.")
        if (milestones.length() !in 2..4) {
            throw LlmException("The decomposition should contain 2–4 milestones. Please retry.")
        }

        for (milestoneIndex in 0 until milestones.length()) {
            val milestone = milestones.optJSONObject(milestoneIndex)
                ?: throw LlmException("Milestone ${milestoneIndex + 1} is malformed. Please retry.")
            requireKeys(milestone, setOf("title", "weeks"), "milestone")
            val milestoneTitle = requireText(milestone, "title", "milestone title")
            if (!MILESTONE_TITLE.matches(milestoneTitle)) {
                throw LlmException("Milestone titles must start with Q1–Q4, a pipe |, and a non-empty title. Please retry.")
            }
            val weeks = milestone.optJSONArray("weeks")
                ?: throw LlmException("Milestone ${milestoneIndex + 1} is missing the weeks array. Please retry.")
            if (weeks.length() !in 2..4) {
                throw LlmException("Each milestone should contain 2–4 weekly tasks. Please retry.")
            }

            for (weekIndex in 0 until weeks.length()) {
                val week = weeks.optJSONObject(weekIndex)
                    ?: throw LlmException("A weekly task is malformed. Please retry.")
                requireKeys(week, setOf("title", "days"), "weekly task")
                val weekTitle = requireText(week, "title", "weekly task title")
                if (!WEEK_TITLE.matches(weekTitle)) {
                    throw LlmException("Weekly task titles must start with \"Week N |\". Please retry.")
                }
                val days = week.optJSONArray("days")
                    ?: throw LlmException("A weekly task is missing the days array. Please retry.")
                if (days.length() !in 1..3) {
                    throw LlmException("Each weekly task should contain 1–3 daily actions. Please retry.")
                }
                for (dayIndex in 0 until days.length()) {
                    val action = days.opt(dayIndex) as? String
                    if (action.isNullOrBlank()) {
                        throw LlmException("Daily actions must be non-empty text. Please retry.")
                    }
                    if (!isEnglishText(action)) {
                        throw LlmException("Daily actions must be English only: letters, digits and standard punctuation. Please retry.")
                    }
                    if (englishWordCount(action) > MAX_DAILY_ACTION_WORDS) {
                        throw LlmException("Daily actions must stay within $MAX_DAILY_ACTION_WORDS English words. Please retry.")
                    }
                }
            }
        }
    }

    private fun requireKeys(value: JSONObject, expected: Set<String>, label: String) {
        val actual = value.keys().asSequence().toSet()
        if (actual != expected) throw LlmException("The ${label} field does not match the decomposition JSON schema. Please retry.")
    }

    private fun requireText(value: JSONObject, key: String, label: String): String {
        val text = value.opt(key) as? String
        if (text.isNullOrBlank()) throw LlmException("${label} must not be empty. Please retry.")
        val trimmed = text.trim()
        if (!isEnglishText(trimmed)) {
            throw LlmException("${label} must be English only: letters, digits and standard punctuation. Please retry.")
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
