package com.lifementor.app.data

import java.util.Calendar
import kotlin.math.pow

/** Growth system: XP rules, level curve, weekly stats, achievements. Rewards only, no penalties. */
object Leveling {
    fun xpForLevel(level: Int): Int = when (level) {
        1 -> 100 // quarterly milestone
        2 -> 30  // weekly task
        else -> 10 // daily action
    }

    /** XP needed to reach level+1 */
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

    fun progressOf(totalXp: Int): Pair<Int, Int> { // (progress into current level, amount needed)
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

    /** Since Monday 00:00 */
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
            Achievement("🌱", "First Steps", "Finish character setup", GameStore.isOnboarded()),
            Achievement("✅", "First Win", "Complete 1 task", doneCount >= 1),
            Achievement("⚒️", "Self-Starter", "Complete 10 tasks", doneCount >= 10),
            Achievement("🗺️", "Map Maker", "Decompose 1 goal", decomposed >= 1),
            Achievement("🧭", "Lv5 Traveler", "Reach level 5", levelOf(total) >= 5),
            Achievement("🪞", "Mirror Work", "Generate 1 weekly review", reviewed >= 1),
            Achievement("🫂", "Mentor Mates", "Chat with 3 mentors", chatted >= 3),
        )
    }
}
