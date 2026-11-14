package com.lifementor.app.llm

import com.lifementor.app.data.Mentors
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmWorkflowsContractTest {
    @Test
    fun exposesScenarioSimulationService() {
        val workflow = Class.forName("com.lifementor.app.llm.LlmWorkflows")
        val method = workflow.methods.firstOrNull { it.name == "simulate" }

        assertNotNull("LlmWorkflows must expose scenario simulation", method)
    }

    @Test
    fun scenarioPromptComparesThreePathsAndAvoidsPredictions() {
        val field = Prompts::class.java.getDeclaredField("SIMULATION_SYSTEM")
        val prompt = field.get(null) as String

        assertTrue(prompt.contains("Compare at least three paths"))
        assertTrue(prompt.contains("time and stage commitments"))
        assertTrue(prompt.contains("resources"))
        assertTrue(prompt.contains("main risks"))
        assertTrue(prompt.contains("main opportunities"))
        assertTrue(prompt.contains("Decision Tree"))
        assertTrue(prompt.contains("never predict the future"))
    }

    @Test
    fun sensitiveScenariosOverrideTheThreePathFormat() {
        val field = Prompts::class.java.getDeclaredField("SIMULATION_SYSTEM")
        val prompt = field.get(null) as String

        assertTrue(prompt.contains("High-priority safety overrides"))
        assertTrue(prompt.contains("self-harm or suicidal thoughts"))
        assertTrue(prompt.contains("12356"))
        assertTrue(prompt.contains("abnormal health concerns"))
        assertTrue(prompt.contains("prompt medical care"))
        assertTrue(prompt.contains("major financial decisions"))
        assertTrue(prompt.contains("licensed professional"))
    }

    @Test
    fun allModelFacingOutputContractsRequireEnglish() {
        val source = requireNotNull(Mentors.byId("pathfinder"))
        val mentorPrompt = Prompts.mentorSystem(source, "profile")
        val handoffPrompt = Prompts.handoffSystem(
            source,
            listOf(requireNotNull(Mentors.byId("scholar"))),
        )

        listOf(
            Prompts.SYSTEM,
            mentorPrompt,
            Prompts.DECOMPOSE_SYSTEM,
            Prompts.SIMULATION_SYSTEM,
            Prompts.REVIEW_SYSTEM,
        ).forEach { prompt ->
            assertTrue(prompt.contains("English"))
            assertTrue(prompt.contains("Do not output any Chinese characters"))
        }
        assertTrue(handoffPrompt.contains("natural English"))
        assertTrue(handoffPrompt.contains("Do not output any Chinese characters"))
    }

    @Test
    fun exposesStructuredMentorHandoffAndTaskBus() {
        val workflow = Class.forName("com.lifementor.app.llm.LlmWorkflows")
        val propose = workflow.methods.firstOrNull { it.name == "proposeHandoff" }
        val bus = Class.forName("com.lifementor.app.llm.MentorTaskBus")

        assertNotNull("LlmWorkflows must expose mentor handoff proposals", propose)
        assertNotNull("Cross-mentor context must use a shared task bus", bus)
    }

    @Test
    fun handoffPromptCarriesSourceAndCandidateMentorGuardrails() {
        val source = requireNotNull(Mentors.byId("heart"))
        val candidates = listOf(
            requireNotNull(Mentors.byId("vital")),
            requireNotNull(Mentors.byId("fortune")),
        )

        val prompt = Prompts.handoffSystem(source, candidates)

        assertTrue(prompt.contains(source.guardrail))
        candidates.forEach { candidate -> assertTrue(prompt.contains(candidate.guardrail)) }
    }
}
