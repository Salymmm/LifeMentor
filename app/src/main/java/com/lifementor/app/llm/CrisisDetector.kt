package com.lifementor.app.llm

import java.util.Locale

/** Deterministic first-line safety check; model guardrails remain active afterwards. */
object CrisisDetector {
    private val chinesePhrases = listOf(
        "自残",
        "不想活",
        "活不下去",
        "轻生",
        "自杀",
        "结束生命",
        "伤害自己",
    )

    private val englishPhrases = listOf(
        "suicide",
        "suicidal",
        "kill myself",
        "killing myself",
        "want to die",
        "wanna die",
        "end my life",
        "ending my life",
        "take my own life",
        "taking my own life",
        "hurt myself",
        "hurting myself",
        "self-harm",
        "self harm",
        "do not want to live",
        "don't want to live",
        "cannot go on living",
        "can't go on living",
        "no reason to live",
    )

    fun containsCrisis(text: String): Boolean {
        val normalized = text
            .lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace(Regex("\\s+"), " ")
            .trim()
        return chinesePhrases.any(normalized::contains) || englishPhrases.any(normalized::contains)
    }
}
