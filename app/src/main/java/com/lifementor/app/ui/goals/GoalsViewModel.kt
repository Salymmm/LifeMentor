package com.lifementor.app.ui.goals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Goal
import com.lifementor.app.data.Mentors
import com.lifementor.app.data.TaskItem
import com.lifementor.app.llm.ChatMessage
import com.lifementor.app.llm.ContextBuilder
import com.lifementor.app.llm.JsonExtract
import com.lifementor.app.llm.LlmClient
import com.lifementor.app.llm.LlmException
import com.lifementor.app.llm.Prompts
import kotlinx.coroutines.launch
import org.json.JSONObject

class GoalsViewModel : ViewModel() {
    var goals by mutableStateOf(GameStore.loadGoals())
        private set
    var tasks by mutableStateOf(GameStore.loadTasks())
        private set
    val busy = mutableStateOf(false)
    val decomposingId = mutableStateOf<Long?>(null)
    val error = mutableStateOf<String?>(null)
    val xpToast = mutableStateOf<String?>(null)
    val expanded = mutableStateMapOf<Long, Boolean>()

    fun refresh() {
        goals = GameStore.loadGoals()
        tasks = GameStore.loadTasks()
    }

    fun addGoal(title: String, domain: String) {
        GameStore.addGoal(title, domain)
        refresh()
    }

    fun toggle(id: Long) {
        val got = GameStore.toggleTask(id)
        if (got > 0) xpToast.value = "⚡ +$got XP"
        refresh()
    }

    fun quickAction(goalId: Long, title: String) {
        if (title.isBlank()) return
        GameStore.addQuickAction(goalId, title)
        expanded[goalId] = true
        refresh()
    }

    fun completeGoal(id: Long) {
        GameStore.setGoalDone(id)
        refresh()
    }

    fun deleteGoal(id: Long) {
        GameStore.deleteGoalTree(id)
        expanded.remove(id)
        refresh()
    }

    fun decompose(goalId: Long) {
        if (busy.value) return
        val goal = goals.find { it.id == goalId } ?: return
        val settings = AppStorage.loadSettings()
        if (settings.apiKey.isBlank()) {
            error.value = "请先在「设置」页填入 API Key"
            return
        }
        busy.value = true
        decomposingId.value = goalId
        error.value = null
        viewModelScope.launch {
            try {
                val role = GameStore.loadRole()
                val profile = AppStorage.loadProfile()
                val domainLabel = Mentors.byId(goal.domain)?.domainLabel ?: goal.domain
                val msgs = mutableListOf(
                    ChatMessage("system", Prompts.DECOMPOSE_SYSTEM),
                    ChatMessage(
                        "user",
                        Prompts.decomposeMessage(
                            goal.title, domainLabel, role, profile.toPromptJson(),
                            ContextBuilder.sharedSummary(),
                        ),
                    ),
                )
                val parsed: JSONObject = try {
                    JsonExtract.obj(LlmClient.chat(settings, msgs))
                } catch (e: Exception) {
                    msgs.add(ChatMessage("user", Prompts.retryDecomposeMessage(e.message ?: "格式错误")))
                    JsonExtract.obj(LlmClient.chat(settings, msgs))
                }
                val newTasks = parseTree(goal.id, parsed)
                val all = GameStore.loadTasks()
                all.addAll(newTasks)
                GameStore.saveTasks(all)
                GameStore.markDecomposed(goal.id)
                expanded[goal.id] = true
                refresh()
            } catch (e: Exception) {
                error.value = e.message ?: "拆解失败，请重试"
            } finally {
                busy.value = false
                decomposingId.value = null
            }
        }
    }

    private fun parseTree(goalId: Long, root: JSONObject): List<TaskItem> {
        val out = ArrayList<TaskItem>()
        val base = System.currentTimeMillis() * 10
        var seq = 0L
        val ms = root.optJSONArray("milestones") ?: throw LlmException("返回中没有 milestones 字段")
        for (i in 0 until ms.length()) {
            val m = ms.getJSONObject(i)
            seq++
            val mId = base + seq
            out.add(TaskItem(mId, goalId, null, 1, m.optString("title")))
            val weeks = m.optJSONArray("weeks") ?: continue
            for (j in 0 until weeks.length()) {
                val w = weeks.getJSONObject(j)
                seq++
                val wId = base + seq
                out.add(TaskItem(wId, goalId, mId, 2, w.optString("title")))
                val days = w.optJSONArray("days")
                if (days != null) {
                    for (k in 0 until days.length()) {
                        seq++
                        out.add(TaskItem(base + seq, goalId, wId, 3, days.getString(k)))
                    }
                }
            }
        }
        if (out.isEmpty()) throw LlmException("拆解结果为空，请重试")
        return out
    }
}
