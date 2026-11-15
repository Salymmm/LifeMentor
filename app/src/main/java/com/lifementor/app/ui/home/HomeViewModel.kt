package com.lifementor.app.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Leveling
import com.lifementor.app.data.Mentors
import com.lifementor.app.data.Role
import com.lifementor.app.data.TaskItem
import com.lifementor.app.data.TaskAgenda
import java.time.LocalDate

class HomeViewModel : ViewModel() {
    data class Snap(
        val role: Role,
        val level: Int,
        val into: Int,
        val need: Int,
        val totalXp: Int,
        val agenda: TaskAgenda.Groups,
        val goalTitles: Map<Long, String>,
        val radar: List<Pair<String, Float>>,
        val radarXp: List<Int>,
        val achievementsDone: Int,
        val achievementsTotal: Int,
    )

    val snap = mutableStateOf(load())
    val error = mutableStateOf<String?>(null)

    fun refresh() {
        snap.value = load()
    }

    private fun load(): Snap {
        val tasks = GameStore.loadTasks()
        val goals = GameStore.loadGoals().filter { it.status == "active" }
        val xp = GameStore.loadXP()
        val total = xp.sumOf { it.amount }
        val (into, need) = Leveling.progressOf(total)
        val domainXp = Leveling.domainTotals(xp)
        val maxV = (domainXp.values.maxOrNull() ?: 0).coerceAtLeast(60)
        val doms = listOf("scholar" to "学术", "pathfinder" to "职业", "heart" to "情绪", "vital" to "健康", "fortune" to "财务")
        val ach = Leveling.achievements()
        return Snap(
            role = GameStore.loadRole(),
            level = Leveling.levelOf(total),
            into = into,
            need = need,
            totalXp = total,
            agenda = TaskAgenda.groups(tasks, goals.map { it.id }.toSet(), LocalDate.now()),
            goalTitles = goals.associate { it.id to it.title },
            radar = doms.map { it.second to (domainXp[it.first] ?: 0) / maxV.toFloat() },
            radarXp = doms.map { domainXp[it.first] ?: 0 },
            achievementsDone = ach.count { it.earned },
            achievementsTotal = ach.size,
        )
    }

    fun complete(task: TaskItem) {
        try {
            GameStore.completeTask(task.id)
            error.value = null
            refresh()
        } catch (e: Exception) { error.value = "保存任务失败，请重试" }
    }

    fun schedule(task: TaskItem, day: LocalDate) {
        try {
            GameStore.setTaskDueDate(task.id, TaskAgenda.dateMillis(day))
            error.value = null
            refresh()
        } catch (e: Exception) { error.value = "保存任务日期失败，请重试" }
    }
}
