package com.lifementor.app.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.PlanMeta
import com.lifementor.app.llm.AiOperationRegistry
import com.lifementor.app.llm.ChatMessage
import com.lifementor.app.llm.LlmClient
import com.lifementor.app.llm.LlmException
import com.lifementor.app.llm.Prompts
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.job
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
    private var generationJob: Job? = null

    init {
        selectLatest()
        viewModelScope.launch {
            AiOperationRegistry.resetVersion.drop(1).collect {
                _versions.value = emptyList()
                selectedId = null
                content.value = ""
                state = GenState.Idle
                requestText.value = ""
            }
        }
    }

    fun selectLatest() {
        _versions.value.firstOrNull()?.let { select(it.id) }
    }

    fun select(id: Long) {
        selectedId = id
        _versions.value.find { it.id == id }?.let { content.value = AppStorage.readDoc("plans", it) }
    }

    fun generate() {
        if (generationJob?.isActive == true) return
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
        content.value = ""
        val operation = AiOperationRegistry.createJob(viewModelScope) { token ->
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
                val streamed = StringBuilder()
                LlmClient.chatStream(settings, messages).collect { chunk ->
                    streamed.append(chunk)
                    if (AiOperationRegistry.isCurrent(token)) content.value = streamed.toString()
                }
                val md = streamed.toString().trim()
                if (md.isBlank()) throw LlmException("模型返回了空内容，请重试")
                if (!AiOperationRegistry.isCurrent(token)) return@createJob
                val meta = AppStorage.saveDoc("plans", md, settings.model, requestText.value.trim())
                _versions.value = AppStorage.versions("plans").reversed()
                select(meta.id)
                requestText.value = ""
                state = GenState.Done
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (AiOperationRegistry.isCurrent(token)) {
                    state = GenState.Error(e.message ?: "生成失败（${e.javaClass.simpleName}）")
                }
            } finally {
                if (generationJob === coroutineContext.job) {
                    generationJob = null
                    if (!AiOperationRegistry.isCurrent(token)) {
                        state = GenState.Idle
                        content.value = ""
                        requestText.value = ""
                    }
                }
            }
        }
        generationJob = operation.job
        operation.start()
    }
}
