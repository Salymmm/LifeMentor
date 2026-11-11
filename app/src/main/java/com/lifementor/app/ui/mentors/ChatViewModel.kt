package com.lifementor.app.ui.mentors

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Mentors
import com.lifementor.app.data.Msg
import com.lifementor.app.llm.ChatMessage
import com.lifementor.app.llm.ContextBuilder
import com.lifementor.app.llm.LlmClient
import com.lifementor.app.llm.Prompts
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    val mentorId = mutableStateOf<String?>(null)
    val messages = mutableStateOf<List<Msg>>(emptyList())
    val draft = mutableStateOf("")
    val sending = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val crisis = mutableStateOf(false)

    fun open(id: String) {
        if (mentorId.value == id) return
        mentorId.value = id
        messages.value = GameStore.loadChat(id)
        error.value = null
        crisis.value = false
    }

    fun send() {
        val text = draft.value.trim()
        val id = mentorId.value ?: return
        if (text.isBlank() || sending.value) return
        if (CRISIS_WORDS.any { text.contains(it) }) crisis.value = true
        val history = messages.value + Msg(System.currentTimeMillis(), "user", text)
        messages.value = history
        draft.value = ""
        sending.value = true
        error.value = null
        viewModelScope.launch {
            try {
                val settings = AppStorage.loadSettings()
                if (settings.apiKey.isBlank()) {
                    error.value = "请先在「设置」页填入 API Key"
                    return@launch
                }
                val mentor = Mentors.byId(id) ?: return@launch
                val payload = ArrayList<ChatMessage>()
                payload.add(ChatMessage("system", Prompts.mentorSystem(mentor, ContextBuilder.sharedSummary())))
                history.takeLast(14).forEach {
                    payload.add(ChatMessage(if (it.role == "user") "user" else "assistant", it.content))
                }
                val reply = LlmClient.chat(settings, payload)
                val finalList = messages.value + Msg(System.currentTimeMillis(), "assistant", reply)
                messages.value = finalList
                GameStore.saveChat(id, finalList)
            } catch (e: Exception) {
                error.value = e.message ?: "对话失败，请重试"
            } finally {
                sending.value = false
            }
        }
    }

    fun clearHistory() {
        val id = mentorId.value ?: return
        messages.value = emptyList()
        GameStore.saveChat(id, emptyList())
    }

    companion object {
        val CRISIS_WORDS = listOf("自残", "不想活", "活不下去", "轻生", "自杀", "结束生命", "伤害自己")
    }
}
