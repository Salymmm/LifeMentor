package com.lifementor.app.llm

import com.lifementor.app.data.Mentor
import com.lifementor.app.data.Settings
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.util.UUID

/** UI-independent AI workflows; callers decide how and when to present the results. */
object LlmWorkflows {
    suspend fun simulate(
        settings: Settings,
        question: String,
        sharedContext: String,
        constraints: String = "",
        recentConversation: String = "",
    ): String {
        if (question.isBlank()) throw LlmException("请先描述要比较的选择")
        return LlmClient.chat(
            settings,
            listOf(
                ChatMessage("system", Prompts.SIMULATION_SYSTEM),
                ChatMessage(
                    "user",
                    Prompts.simulationMessage(question, sharedContext, constraints, recentConversation),
                ),
            ),
        )
    }

    fun simulateStream(
        settings: Settings,
        question: String,
        sharedContext: String,
        constraints: String = "",
        recentConversation: String = "",
    ): Flow<String> {
        if (question.isBlank()) throw LlmException("请先描述要比较的选择")
        return LlmClient.chatStream(
            settings,
            listOf(
                ChatMessage("system", Prompts.SIMULATION_SYSTEM),
                ChatMessage(
                    "user",
                    Prompts.simulationMessage(question, sharedContext, constraints, recentConversation),
                ),
            ),
        )
    }

    suspend fun proposeHandoff(
        settings: Settings,
        source: Mentor,
        candidates: List<Mentor>,
        conversation: String,
        sharedContext: String,
    ): MentorHandoffSuggestion? {
        val eligible = candidates.filter { it.id.isNotBlank() && it.id != source.id }.distinctBy { it.id }
        if (eligible.isEmpty() || conversation.isBlank()) return null
        val raw = LlmClient.chat(
            settings,
            listOf(
                ChatMessage("system", Prompts.handoffSystem(source, eligible)),
                ChatMessage("user", Prompts.handoffMessage(conversation, sharedContext)),
            ),
        )
        return MentorHandoffParser.parse(raw, source.id, eligible.mapTo(linkedSetOf()) { it.id })
    }

    /** Call only after the user confirms the proposal. */
    fun acceptHandoff(suggestion: MentorHandoffSuggestion): Boolean = MentorTaskBus.accept(suggestion)
}

data class MentorHandoffSuggestion(
    val sourceMentorId: String,
    val targetMentorId: String,
    val taskTitle: String,
    val rationale: String,
    val id: String = UUID.randomUUID().toString(),
)

object MentorHandoffParser {
    fun parse(raw: String, sourceMentorId: String, allowedTargetMentorIds: Set<String>): MentorHandoffSuggestion? {
        val root = try {
            JsonExtract.extractObject(raw)
        } catch (_: Exception) {
            return null
        }
        val value = root.opt("handoff")
        if (value == null || value === JSONObject.NULL) return null
        val handoff = value as? JSONObject ?: return null
        val targetId = handoff.optString("targetMentorId").trim()
        val taskTitle = handoff.optString("taskTitle").trim()
        val rationale = handoff.optString("rationale").trim()
        if (targetId !in allowedTargetMentorIds || targetId == sourceMentorId) return null
        if (taskTitle.isBlank() || taskTitle.length > MAX_TASK_TITLE_LENGTH) return null
        if (rationale.length > MAX_RATIONALE_LENGTH) return null
        return MentorHandoffSuggestion(sourceMentorId, targetId, taskTitle, rationale)
    }

    private const val MAX_TASK_TITLE_LENGTH = 120
    private const val MAX_RATIONALE_LENGTH = 240
}
