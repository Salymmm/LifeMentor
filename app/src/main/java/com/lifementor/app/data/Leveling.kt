package com.lifementor.app.data

import java.util.Calendar
import kotlin.math.pow

/** 成长体系：XP 规则、等级曲线、周统计、成就。只奖励，不惩罚（PRD 温和成长机制）。 */
object Leveling {
    fun xpForLevel(level: Int): Int = when (level) {
        1 -> 100 // 季度里程碑
        2 -> 30  // 周任务
        else -> 10 // 每日行动
    }

    /** 升到 level+1 所需增量 */
    fun needFor(level: Int): Int = (80 * level * 1.15.pow(level - 1)).toInt()

    fun levelOf(totalXp: Int): Int {
        var level = 1
        var remain = totalXp
        while (remain >= needFor(level)) {
            remain -= needFor(level)
            level++
            if (level > 99) break
        }
        return level
    }

    fun progressOf(totalXp: Int): Pair<Int, Int> { // (当前等级内已有, 升级需要)
        var level = 1
        var remain = totalXp
        while (remain >= needFor(level)) {
            remain -= needFor(level)
            level++
            if (level > 99) break
        }
        return remain to needFor(level)
    }

    fun domainTotals(xp: List<XPEntry>): Map<String, Int> =
        xp.groupBy { it.domain }.mapValues { e -> e.value.sumOf { it.amount } }

    /** 周一 00:00 起 */
    fun weekStart(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    data class WeekStat(
        val doneCount: Int,
        val openCount: Int,
        val xpGained: Int,
        val byDomain: Map<String, Int>,
        val doneTitles: List<String>,
        val openTitles: List<String>,
    )

    fun weeklyStat(tasks: List<TaskItem>, xp: List<XPEntry>): WeekStat {
        val ws = weekStart()
        val done = tasks.filter { it.done && (it.doneAt ?: 0) >= ws }
        val open = tasks.filter { !it.done && it.level <= 2 }
        val weekXp = xp.filter { it.time >= ws }
        val domainByGoal = tasks.associate { t ->
            t.id to (GameStore.loadGoals().find { g -> g.id == t.goalId }?.domain ?: "pathfinder")
        }
        return WeekStat(
            doneCount = done.size,
            openCount = tasks.count { !it.done },
            xpGained = weekXp.sumOf { it.amount },
            byDomain = done.groupingBy { domainByGoal[it.id] ?: "pathfinder" }.eachCountTo(mutableMapOf()).mapValues { it.value },
            doneTitles = done.sortedByDescending { it.doneAt }.take(10).map { it.title },
            openTitles = open.take(10).map { it.title },
        )
    }

    fun achievements(): List<Achievement> {
        val tasks = GameStore.loadTasks()
        val goals = GameStore.loadGoals()
        val xp = GameStore.loadXP()
        val total = xp.sumOf { it.amount }
        val doneCount = tasks.count { it.done }
        val decomposed = goals.count { it.decomposed }
        val reviewed = AppStorage.versions("reviews").size
        val chatted = Mentors.all.count { GameStore.loadChat(it.id).isNotEmpty() }
        return listOf(
            Achievement("🌱", "启程", "完成角色创建", GameStore.isOnboarded()),
            Achievement("✅", "第一次兑现", "完成 1 个任务", doneCount >= 1),
            Achievement("⚒️", "行动派", "完成 10 个任务", doneCount >= 10),
            Achievement("🗺️", "有了地图", "拆解 1 个目标", decomposed >= 1),
            Achievement("🧭", "Lv5 旅人", "达到 5 级", levelOf(total) >= 5),
            Achievement("🪞", "照镜子", "生成 1 份周复盘", reviewed >= 1),
            Achievement("🫂", "认识导师", "和 3 位导师聊过天", chatted >= 3),
        )
    }
}
