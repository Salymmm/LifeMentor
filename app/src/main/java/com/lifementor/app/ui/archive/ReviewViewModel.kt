package com.lifementor.app.ui.archive

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Leveling
import com.lifementor.app.data.PlanMeta
import com.lifementor.app.llm.AiOperationRegistry
import com.lifementor.app.llm.ChatMessage
import com.lifementor.app.llm.LlmClient
import com.lifementor.app.llm.Prompts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewViewModel : ViewModel() {
    val versions = mutableStateOf<List<PlanMeta>>(AppStorage.versions("reviews").reversed())
    val content = mutableStateOf("")
    val selectedId = mutableStateOf<Long?>(null)
    val running = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    private var reviewJob: Job? = null

    init {
        versions.value.firstOrNull()?.let { select(it.id) }
        viewModelScope.launch {
            AiOperationRegistry.resetVersion.drop(1).collect {
                versions.value = emptyList()
                content.value = ""
                selectedId.value = null
                running.value = false
                error.value = null
            }
        }
    }

    fun select(id: Long) {
        selectedId.value = id
        versions.value.find { it.id == id }?.let { content.value = AppStorage.readDoc("reviews", it) }
    }

    fun generate() {
        if (running.value) return
        val settings = AppStorage.loadSettings()
        if (settings.apiKey.isBlank()) {
            error.value = "请先在「设置」页填入 API Key"
            return
        }
        val tasks = GameStore.loadTasks()
        if (tasks.isEmpty()) {
            error.value = "还没有任务数据——先在「目标」页拆解一个目标，有行动记录后复盘才有料"
            return
        }
        running.value = true
        error.value = null
        val operation = AiOperationRegistry.createJob(viewModelScope) { token ->
            try {
                val stat = Leveling.weeklyStat(tasks, GameStore.loadXP())
                val goalLines = GameStore.loadGoals().filter { it.status == "active" }.joinToString("\n") { g ->
                    val sub = tasks.filter { it.goalId == g.id }
                    "- ${g.title}（完成 ${sub.count { t -> t.done }}/${sub.size}）"
                }.ifBlank { "（暂无活跃目标）" }
                val fmt = SimpleDateFormat("MM-dd", Locale.getDefault())
                val label = "${fmt.format(Date(Leveling.weekStart()))} ~ ${fmt.format(Date())}"
                val md = LlmClient.chat(
                    settings,
                    listOf(
                        ChatMessage("system", Prompts.REVIEW_SYSTEM),
                        ChatMessage("user", Prompts.reviewMessage(label, stat, goalLines)),
                    ),
                )
                if (!AiOperationRegistry.isCurrent(token)) return@createJob
                val meta = AppStorage.saveDoc("reviews", md, settings.model, "")
                versions.value = AppStorage.versions("reviews").reversed()
                select(meta.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (AiOperationRegistry.isCurrent(token)) error.value = e.message ?: "生成失败"
            } finally {
                if (reviewJob === coroutineContext.job) {
                    reviewJob = null
                    running.value = false
                    if (!AiOperationRegistry.isCurrent(token)) {
                        versions.value = emptyList()
                        content.value = ""
                        selectedId.value = null
                        error.value = null
                    }
                }
            }
        }
        reviewJob = operation.job
        operation.start()
    }
}
