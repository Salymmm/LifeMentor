package com.lifementor.app.llm

import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Leveling

/** 所有导师共享的上下文：角色 + 画像 + 活跃目标 + 本周状态（PRD「导师共享授权信息」的简化实现） */
object ContextBuilder {
    fun sharedSummary(): String {
        val role = GameStore.loadRole()
        val profile = AppStorage.loadProfile()
        val goals = GameStore.loadGoals().filter { it.status == "active" }.take(6)
        val tasks = GameStore.loadTasks()
        val stat = Leveling.weeklyStat(tasks, GameStore.loadXP())
        val level = Leveling.levelOf(GameStore.loadXP().sumOf { it.amount })
        return buildString {
            appendLine("【角色】${role.avatar} ${role.nickname.ifBlank { "（未命名）" } }，Lv$level，阶段：${role.stage.orUnknown()}")
            if (role.vision.isNotBlank()) appendLine("【愿景】${role.vision}")
            if (!profile.isEmpty()) appendLine("【画像】${profile.toPromptJson()}")
            if (goals.isNotEmpty()) {
                appendLine("【活跃目标】")
                goals.forEach { g ->
                    val sub = tasks.filter { it.goalId == g.id }
                    appendLine("- ${g.title}（${g.domain}${if (g.decomposed) "，已拆解 ${sub.count { t -> t.done }}/${sub.size} 项" else "，未拆解"}）")
                }
            }
            appendLine("【本周】完成 ${stat.doneCount} 项 / 欠 ${stat.openCount} 项，获得 ${stat.xpGained} XP")
        }.take(2600)
    }

    private fun String.orUnknown(): String = ifBlank { "未知" }
}
