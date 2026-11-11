package com.lifementor.app.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.PlanMeta
import com.lifementor.app.llm.ChatMessage
import com.lifementor.app.llm.LlmClient
import com.lifementor.app.llm.Prompts
import kotlinx.coroutines.launch

sealed interface GenState {
    object Idle : GenState
    object Running : GenState
    object Done : GenState
    data class Error(val msg: String) : GenState
}

class PlanViewModel : ViewModel() {
    private val _versions = mutableStateOf(AppStorage.versions("plans").reversed())
    val versions: List<PlanMeta> get() = _versions.value

    var selectedId by mutableStateOf<Long?>(null)
        private set

    val content = mutableStateOf("")
    var state by mutableStateOf<GenState>(GenState.Idle)
        private set
    val requestText = mutableStateOf("")

    init {
        selectLatest()
    }

    fun selectLatest() {
        _versions.value.firstOrNull()?.let { select(it.id) }
    }

    fun select(id: Long) {
        selectedId = id
        _versions.value.find { it.id == id }?.let { content.value = AppStorage.readDoc("plans", it) }
    }

    fun generate() {
        val settings = AppStorage.loadSettings()
        val profile = AppStorage.loadProfile()
        if (settings.apiKey.isBlank()) {
            state = GenState.Error("还没有配置 API Key：请到底部「设置」页填入免费的大模型 Key（推荐智谱 GLM，注册约 3 分钟）")
            return
        }
        if (profile.isEmpty()) {
            state = GenState.Error("画像还是空的：可到「设置 → 职业画像」补全，或直接让导师基于对话完善")
            return
        }
        state = GenState.Running
        viewModelScope.launch {
            try {
                val messages = if (_versions.value.isEmpty()) {
                    listOf(
                        ChatMessage("system", Prompts.SYSTEM),
                        ChatMessage("user", Prompts.initialMessage(profile)),
                    )
                } else {
                    val previous = AppStorage.readDoc("plans", _versions.value.first())
                    listOf(
                        ChatMessage("system", Prompts.SYSTEM),
                        ChatMessage("user", Prompts.updateMessage(profile, previous, requestText.value)),
                    )
                }
                val md = LlmClient.chat(settings, messages)
                val meta = AppStorage.saveDoc("plans", md, settings.model, requestText.value.trim())
                _versions.value = AppStorage.versions("plans").reversed()
                select(meta.id)
                requestText.value = ""
                state = GenState.Done
            } catch (e: Exception) {
                state = GenState.Error(e.message ?: "生成失败（${e.javaClass.simpleName}）")
            }
        }
    }
}
