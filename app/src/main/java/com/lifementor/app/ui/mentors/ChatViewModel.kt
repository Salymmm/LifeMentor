package com.lifementor.app.ui.mentors

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Mentor
import com.lifementor.app.data.Mentors
import com.lifementor.app.data.Msg
import com.lifementor.app.data.Settings
import com.lifementor.app.llm.ChatMessage
import com.lifementor.app.llm.ContextBuilder
import com.lifementor.app.llm.AiOperationRegistry
import com.lifementor.app.llm.CrisisDetector
import com.lifementor.app.llm.LlmClient
import com.lifementor.app.llm.LlmException
import com.lifementor.app.llm.LlmWorkflows
import com.lifementor.app.llm.MentorHandoffSuggestion
import com.lifementor.app.llm.Prompts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class ChatViewModel internal constructor(
    private val actions: MentorChatActions = DefaultMentorChatActions,
    private val scopeOverride: CoroutineScope? = null,
) : ViewModel() {
    val mentorId = mutableStateOf<String?>(null)
    val messages = mutableStateOf<List<Msg>>(emptyList())
    val draft = mutableStateOf("")
    val sending = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val crisis = mutableStateOf(false)
    val simulationMode = mutableStateOf(false)
    val handoffBusy = mutableStateOf(false)
    val handoffSuggestion = mutableStateOf<MentorHandoffSuggestion?>(null)
    val handoffStatus = mutableStateOf<String?>(null)

    private val taskScope: CoroutineScope
        get() = scopeOverride ?: viewModelScope
    private var sendJob: Job? = null
    private var handoffJob: Job? = null
    private var sendToken = 0L
    private var handoffToken = 0L
    private var activeAssistantTime: Long? = null

    init {
        taskScope.launch {
            AiOperationRegistry.resetVersion.drop(1).collect {
                sendToken++
                handoffToken++
                sendJob?.cancel()
                handoffJob?.cancel()
                sendJob = null
                handoffJob = null
                messages.value = emptyList()
                draft.value = ""
                sending.value = false
                error.value = null
                crisis.value = false
                simulationMode.value = false
                handoffBusy.value = false
                handoffSuggestion.value = null
                handoffStatus.value = null
                activeAssistantTime = null
            }
        }
    }

    fun open(id: String) {
        if (mentorId.value == id) return
        cancelSending()
        cancelHandoff()
        mentorId.value = id
        messages.value = actions.loadChat(id)
        error.value = null
        crisis.value = false
        simulationMode.value = false
        handoffSuggestion.value = null
        handoffStatus.value = null
    }

    fun toggleSimulationMode() {
        if (!sending.value && !handoffBusy.value) {
            simulationMode.value = !simulationMode.value
            handoffSuggestion.value = null
            handoffStatus.value = null
        }
    }

    fun send() {
        val text = draft.value.trim()
        val id = mentorId.value ?: return
        if (text.isBlank() || sending.value || handoffBusy.value) return
        if (CrisisDetector.containsCrisis(text)) crisis.value = true

        val history = messages.value
        val now = maxOf(System.currentTimeMillis(), (history.lastOrNull()?.time ?: 0L) + 2)
        val userMessage = Msg(now, "user", text)
        val assistantTime = now + 1
        val simulation = simulationMode.value
        messages.value = history + userMessage + Msg(assistantTime, "assistant", "")
        draft.value = ""
        sending.value = true
        error.value = null
        handoffSuggestion.value = null
        handoffStatus.value = null
        activeAssistantTime = assistantTime
        val token = ++sendToken

        val operation = AiOperationRegistry.createJob(taskScope) { operationToken ->
            var reply = ""
            try {
                val settings = actions.settings()
                if (settings.apiKey.isBlank()) throw LlmException("Add your API key on the Settings page first")

                if (simulation) {
                    actions.simulateStream(
                        settings = settings,
                        question = text,
                        sharedContext = actions.sharedContext(),
                        recentConversation = formatTranscript(history),
                    ).collect { chunk ->
                        reply += chunk
                        renderReply(id, token, operationToken, history, userMessage, assistantTime, reply)
                    }
                } else {
                    val mentor = actions.mentor(id)
                        ?: throw LlmException("Mentor unavailable — go back and retry")
                    val payload = ArrayList<ChatMessage>()
                    payload.add(ChatMessage("system", Prompts.mentorSystem(mentor, actions.sharedContext())))
                    (history + userMessage).takeLast(14).forEach {
                        payload.add(ChatMessage(if (it.role == "user") "user" else "assistant", it.content))
                    }
                    actions.chatStream(settings, payload).collect { chunk ->
                        reply += chunk
                        renderReply(id, token, operationToken, history, userMessage, assistantTime, reply)
                    }
                }

                if (reply.isBlank()) throw LlmException("The model returned empty content. Please retry.")
                if (isCurrentSend(id, token, operationToken)) actions.saveChat(id, messages.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (isCurrentSend(id, token, operationToken)) {
                    error.value = e.message ?: "Chat failed, please retry"
                    if (reply.isBlank()) messages.value = history + userMessage
                    actions.saveChat(id, messages.value)
                }
            } finally {
                if (isCurrentSend(id, token, operationToken)) {
                    sending.value = false
                    sendJob = null
                    activeAssistantTime = null
                }
            }
        }
        sendJob = operation.job
        operation.start()
    }

    private fun renderReply(
        id: String,
        token: Long,
        operationToken: Long,
        history: List<Msg>,
        userMessage: Msg,
        assistantTime: Long,
        reply: String,
    ) {
        if (isCurrentSend(id, token, operationToken)) {
            messages.value = history + userMessage + Msg(assistantTime, "assistant", reply)
        }
    }

    private fun isCurrentSend(id: String, token: Long, operationToken: Long): Boolean =
        mentorId.value == id && sendToken == token && AiOperationRegistry.isCurrent(operationToken)

    private fun formatTranscript(history: List<Msg>): String = history.takeLast(14).joinToString("\n") {
        "${if (it.role == "user") "User" else "Mentor"}: ${it.content}"
    }.takeLast(6000)

    fun cancelSending() {
        val job = sendJob
        if (job == null && !sending.value) return
        val id = mentorId.value
        sendToken++
        sendJob = null
        sending.value = false

        activeAssistantTime?.let { assistantTime ->
            val updated = messages.value.filterNot {
                it.role == "assistant" && it.time == assistantTime && it.content.isBlank()
            }
            messages.value = updated
            if (id != null) actions.saveChat(id, updated)
        }
        activeAssistantTime = null
        job?.cancel()
    }

    fun requestHandoff() {
        val id = mentorId.value ?: return
        if (sending.value || handoffBusy.value) return
        val transcript = messages.value.takeLast(14).joinToString("\n") {
            "${if (it.role == "user") "User" else "Mentor"}: ${it.content}"
        }.takeLast(6000)
        if (transcript.isBlank() || messages.value.none { it.role == "assistant" }) return

        handoffBusy.value = true
        handoffSuggestion.value = null
        handoffStatus.value = null
        error.value = null
        val token = ++handoffToken
        val operation = AiOperationRegistry.createJob(taskScope) { operationToken ->
            try {
                val settings = actions.settings()
                if (settings.apiKey.isBlank()) throw LlmException("Add your API key on the Settings page first")
                val source = actions.mentor(id) ?: throw LlmException("Mentor unavailable — go back and retry")
                val suggestion = actions.proposeHandoff(
                    settings = settings,
                    source = source,
                    candidates = actions.mentors().filter { it.id != source.id },
                    conversation = transcript,
                    sharedContext = actions.sharedContext(),
                )
                if (isCurrentHandoff(id, token, operationToken)) {
                    if (suggestion == null) {
                        handoffStatus.value = "No useful cross-mentor suggestion for this conversation right now"
                    } else {
                        handoffSuggestion.value = suggestion
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (isCurrentHandoff(id, token, operationToken)) error.value = e.message ?: "Handoff suggestion failed, please retry"
            } finally {
                if (isCurrentHandoff(id, token, operationToken)) {
                    handoffBusy.value = false
                    handoffJob = null
                }
            }
        }
        handoffJob = operation.job
        operation.start()
    }

    private fun isCurrentHandoff(id: String, token: Long, operationToken: Long): Boolean =
        mentorId.value == id && handoffToken == token && AiOperationRegistry.isCurrent(operationToken)

    fun acceptHandoff() {
        val suggestion = handoffSuggestion.value ?: return
        if (!actions.acceptHandoff(suggestion)) {
            error.value = "Could not save the handoff suggestion, please retry"
            return
        }
        val targetName = actions.mentor(suggestion.targetMentorId)?.name ?: suggestion.targetMentorId
        handoffSuggestion.value = null
        handoffStatus.value = "Confirmed. It carries over when you open $targetName during this app session; after a restart it needs to be regenerated."
    }

    fun dismissHandoff() {
        handoffSuggestion.value = null
        handoffStatus.value = null
    }

    private fun cancelHandoff() {
        handoffToken++
        handoffJob?.cancel()
        handoffJob = null
        handoffBusy.value = false
    }

    fun clearHistory() {
        val id = mentorId.value ?: return
        cancelSending()
        cancelHandoff()
        messages.value = emptyList()
        handoffSuggestion.value = null
        handoffStatus.value = null
        actions.saveChat(id, emptyList())
    }
}

internal interface MentorChatActions {
    fun settings(): Settings
    fun sharedContext(): String
    fun loadChat(mentorId: String): List<Msg>
    fun saveChat(mentorId: String, messages: List<Msg>)
    fun mentor(mentorId: String): Mentor?
    fun mentors(): List<Mentor>
    fun chatStream(settings: Settings, messages: List<ChatMessage>): Flow<String>
    fun simulateStream(
        settings: Settings,
        question: String,
        sharedContext: String,
        recentConversation: String,
    ): Flow<String>
    suspend fun proposeHandoff(
        settings: Settings,
        source: Mentor,
        candidates: List<Mentor>,
        conversation: String,
        sharedContext: String,
    ): MentorHandoffSuggestion?
    fun acceptHandoff(suggestion: MentorHandoffSuggestion): Boolean
}

private object DefaultMentorChatActions : MentorChatActions {
    override fun settings() = AppStorage.loadSettings()
    override fun sharedContext() = ContextBuilder.sharedSummary()
    override fun loadChat(mentorId: String) = GameStore.loadChat(mentorId)
    override fun saveChat(mentorId: String, messages: List<Msg>) = GameStore.saveChat(mentorId, messages)
    override fun mentor(mentorId: String) = Mentors.byId(mentorId)
    override fun mentors() = Mentors.visibleList()
    override fun chatStream(settings: Settings, messages: List<ChatMessage>) = LlmClient.chatStream(settings, messages)
    override fun simulateStream(
        settings: Settings,
        question: String,
        sharedContext: String,
        recentConversation: String,
    ) = LlmWorkflows.simulateStream(
        settings,
        question,
        sharedContext,
        recentConversation = recentConversation,
    )
    override suspend fun proposeHandoff(
        settings: Settings,
        source: Mentor,
        candidates: List<Mentor>,
        conversation: String,
        sharedContext: String,
    ) = LlmWorkflows.proposeHandoff(settings, source, candidates, conversation, sharedContext)
    override fun acceptHandoff(suggestion: MentorHandoffSuggestion) = LlmWorkflows.acceptHandoff(suggestion)
}
