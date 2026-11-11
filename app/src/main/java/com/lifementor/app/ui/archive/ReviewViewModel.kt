package com.lifementor.app.ui.archive

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Leveling
import com.lifementor.app.data.PlanMeta
import com.lifementor.app.llm.ChatMessage
import com.lifementor.app.llm.LlmClient
import com.lifementor.app.llm.Prompts
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

    init {
        versions.value.firstOrNull()?.let { select(it.id) }
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
        viewModelScope.launch {
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
                val meta = AppStorage.saveDoc("reviews", md, settings.model, "")
                versions.value = AppStorage.versions("reviews").reversed()
                select(meta.id)
            } catch (e: Exception) {
                error.value = e.message ?: "生成失败"
            } finally {
                running.value = false
            }
        }
    }
}
