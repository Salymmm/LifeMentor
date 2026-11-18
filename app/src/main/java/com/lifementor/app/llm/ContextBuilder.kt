package com.lifementor.app.llm

import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Leveling

/** Context shared by all mentors: role + profile + active goals + this week's status (simplified "authorized shared context" from the PRD) */
object ContextBuilder {
    fun sharedSummary(): String {
        val role = GameStore.loadRole()
        val profile = AppStorage.loadProfile()
        val goals = GameStore.loadGoals().filter { it.status == "active" }.take(6)
        val tasks = GameStore.loadTasks()
        val stat = Leveling.weeklyStat(tasks, GameStore.loadXP())
        val level = Leveling.levelOf(GameStore.loadXP().sumOf { it.amount })
        return buildString {
            appendLine("[Role]${role.avatar} ${role.nickname.ifBlank { "(unnamed)" } }, Lv$level, stage: ${role.stage.orUnknown()}")
            if (role.vision.isNotBlank()) appendLine("[Vision]${role.vision}")
            if (!profile.isEmpty()) appendLine("[Profile]${profile.toPromptJson()}")
            if (goals.isNotEmpty()) {
                appendLine("[Active goals]")
                goals.forEach { g ->
                    val sub = tasks.filter { it.goalId == g.id }
                    appendLine("- ${g.title} (${g.domain}${if (g.decomposed) ", decomposed ${sub.count { t -> t.done }}/${sub.size}" else ", not decomposed"})")
                }
            }
            appendLine("[This week]completed ${stat.doneCount} / open ${stat.openCount}, earned ${stat.xpGained} XP")
        }.take(2600)
    }

    private fun String.orUnknown(): String = ifBlank { "unknown" }
}
