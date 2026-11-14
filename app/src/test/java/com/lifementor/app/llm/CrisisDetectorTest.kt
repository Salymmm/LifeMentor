package com.lifementor.app.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrisisDetectorTest {
    @Test
    fun detectsCommonEnglishSelfHarmPhrases() {
        listOf(
            "I want to die.",
            "I might kill myself tonight",
            "I do not want to live anymore",
            "I am thinking about suicide",
            "I could hurt myself",
            "I plan to end my life",
        ).forEach { assertTrue(it, CrisisDetector.containsCrisis(it)) }
    }

    @Test
    fun detectsNormalizedEnglishVariants() {
        assertTrue(CrisisDetector.containsCrisis("I DON’T WANT TO LIVE"))
        assertTrue(CrisisDetector.containsCrisis("self-harm"))
        assertTrue(CrisisDetector.containsCrisis("I cannot go on living"))
    }

    @Test
    fun preservesChineseDetection() {
        listOf("我不想活了", "我想自杀", "我在伤害自己").forEach {
            assertTrue(it, CrisisDetector.containsCrisis(it))
        }
    }

    @Test
    fun doesNotFlagOrdinaryWellbeingText() {
        assertFalse(CrisisDetector.containsCrisis("I want to live a healthy life"))
        assertFalse(CrisisDetector.containsCrisis("今天工作很忙，但我想好好生活"))
    }
}
