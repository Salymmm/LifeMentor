package com.lifementor.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import com.lifementor.app.notify.TaskReminders
import androidx.compose.runtime.mutableIntStateOf

/** 目标 / 任务 / XP / 聊天 / 角色 的本地仓库（JSON 文件实现，后续可无痛迁移 Room） */
object GameStore {
    val taskRevision = mutableIntStateOf(0)
    private val ctx get() = AppStorage.appCtx
    private val goalsFile get() = File(ctx.filesDir, "goals.json")
    private val tasksFile get() = File(ctx.filesDir, "tasks.json")
    private val xpFile get() = File(ctx.filesDir, "xp.json")
    private val roleFile get() = File(ctx.filesDir, "role.json")
    private val chatsDir get() = File(ctx.filesDir, "chats").apply { mkdirs() }

    // ---------- 角色 ----------
    fun loadRole(): Role =
        if (roleFile.exists()) Role.fromJson(roleFile.readText()) else Role()

    fun saveRole(r: Role) = roleFile.writeText(r.toJson())

    fun isOnboarded(): Boolean = AppStorage.prefs().optBoolean("onboarded", false)

    // ---------- 目标 ----------
    fun loadGoals(): MutableList<Goal> = try {
        val a = JSONArray(if (goalsFile.exists()) goalsFile.readText() else "[]")
        (0 until a.length()).map { Goal.from(a.getJSONObject(it)) }.toMutableList()
    } catch (e: Exception) {
        mutableListOf()
    }

    fun saveGoals(list: List<Goal>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        goalsFile.writeText(a.toString())
        taskRevision.intValue++
    }

    fun addGoal(title: String, domain: String): Goal {
        val g = Goal(System.currentTimeMillis(), title.trim(), domain)
        val list = loadGoals()
        list.add(g)
        saveGoals(list)
        return g
    }

    fun updateGoal(id: Long, title: String, domain: String) {
        saveGoals(loadGoals().map { if (it.id == id) it.edited(title, domain) else it })
    }

    fun setGoalDone(id: Long) {
        saveGoals(loadGoals().map { if (it.id == id) it.copy(status = "done") else it })
        loadTasks().filter { it.goalId == id }.forEach { TaskReminders.cancelTask(ctx, it.id) }
    }

    fun reopenGoal(id: Long) {
        saveGoals(loadGoals().map { if (it.id == id) it.copy(status = "active") else it })
    }

    fun markDecomposed(id: Long) {
        saveGoals(loadGoals().map { if (it.id == id) it.copy(decomposed = true) else it })
    }

    // ---------- 任务 ----------
    fun loadTasks(): MutableList<TaskItem> = try {
        val a = JSONArray(if (tasksFile.exists()) tasksFile.readText() else "[]")
        (0 until a.length()).map { TaskItem.from(a.getJSONObject(it)) }.toMutableList()
    } catch (e: Exception) {
        mutableListOf()
    }

    fun saveTasks(list: List<TaskItem>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        tasksFile.writeText(a.toString())
        taskRevision.intValue++
    }

    fun setTaskDueDate(taskId: Long, dueDate: Long?) {
        saveTasks(loadTasks().map { if (it.id == taskId) it.copy(dueDate = dueDate) else it })
        TaskReminders.cancelTask(ctx, taskId)
    }

    fun updateTask(taskId: Long, title: String, dueDate: Long?) {
        saveTasks(loadTasks().map { if (it.id == taskId) it.edited(title, dueDate) else it })
        TaskReminders.cancelTask(ctx, taskId)
    }

    fun deleteTaskTree(taskId: Long): Set<Long> {
        val tasks = loadTasks()
        val removed = taskBranchIds(tasks, taskId)
        saveTasks(tasks.filter { it.id !in removed })
        removed.forEach { TaskReminders.cancelTask(ctx, it) }
        return removed
    }

    /** 通知和今日任务使用只完成操作；重复点击不会撤销或重复奖励。 */
    fun completeTask(taskId: Long): Int {
        val tasks = loadTasks()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index < 0 || tasks[index].done) return 0
        val task = tasks[index]
        val goal = loadGoals().find { it.id == task.goalId && it.status == "active" } ?: return 0
        val awarded = if (task.xpGiven) 0 else Leveling.xpForLevel(task.level)
        tasks[index] = task.completed(System.currentTimeMillis()).copy(xpGiven = true)
        if (awarded > 0) addXP(awarded, task.title, goal.domain)
        saveTasks(tasks)
        TaskReminders.cancelTask(ctx, taskId)
        return awarded
    }

    /** 勾选/取消勾选任务；首次完成时按档位发放 XP（只奖不罚，反悔不回收） */
    fun toggleTask(taskId: Long): Int {
        val tasks = loadTasks()
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx < 0) return 0
        val t = tasks[idx]
        val goal = loadGoals().find { it.id == t.goalId }
        val now = !t.done
        var awarded = 0
        tasks[idx] = t.copy(
            done = now,
            doneAt = if (now) System.currentTimeMillis() else null,
        )
        if (now && !t.xpGiven) {
            awarded = Leveling.xpForLevel(t.level)
            tasks[idx] = tasks[idx].copy(xpGiven = true)
            addXP(awarded, t.title, goal?.domain ?: "pathfinder")
        }
        saveTasks(tasks)
        TaskReminders.cancelTask(ctx, taskId)
        return awarded
    }

    fun addQuickAction(goalId: Long, title: String) {
        val anchor = loadTasks().lastOrNull { it.goalId == goalId && it.level == 2 }?.id
        addTask(goalId, title, anchor)
    }

    fun addTask(goalId: Long, title: String, parentId: Long? = null, dueDate: Long? = null): TaskItem {
        require(loadGoals().any { it.id == goalId && it.status == "active" }) { "请在进行中的目标下添加任务" }
        val tasks = loadTasks()
        val parent = parentId?.let { id ->
            tasks.find { it.id == id } ?: throw IllegalArgumentException("上一级任务已不存在，请重新选择")
        }
        val id = maxOf(System.currentTimeMillis(), (tasks.maxOfOrNull { it.id } ?: 0L) + 1)
        val task = TaskItem.manual(id, goalId, title, parent, dueDate)
        tasks.add(task)
        saveTasks(tasks)
        return task
    }

    fun deleteGoalTree(goalId: Long) {
        loadTasks().filter { it.goalId == goalId }.forEach { TaskReminders.cancelTask(ctx, it.id) }
        saveTasks(loadTasks().filter { it.goalId != goalId })
        saveGoals(loadGoals().filter { it.id != goalId })
    }

    // ---------- XP ----------
    fun loadXP(): List<XPEntry> = try {
        XPEntry.listFrom(JSONArray(if (xpFile.exists()) xpFile.readText() else "[]"))
    } catch (e: Exception) {
        emptyList()
    }

    fun addXP(amount: Int, reason: String, domain: String) {
        val list = loadXP().toMutableList()
        list.add(XPEntry(System.currentTimeMillis(), amount, reason, domain))
        xpFile.writeText(XPEntry.listToJson(list))
    }

    // ---------- 聊天 ----------
    fun loadChat(mentorId: String): List<Msg> {
        return try {
            val f = File(chatsDir, "$mentorId.json")
            if (!f.exists()) return emptyList()
            val a = JSONArray(f.readText())
            (0 until a.length()).map { Msg.from(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveChat(mentorId: String, list: List<Msg>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        File(chatsDir, "$mentorId.json").writeText(a.toString())
    }

    fun clearAll() {
        TaskReminders.reset(ctx)
        listOf(goalsFile, tasksFile, xpFile, roleFile).forEach { it.delete() }
        chatsDir.listFiles()?.forEach { it.delete() }
        taskRevision.intValue++
    }

    /** 创建向导完成：写角色 + 勾选的主线自动生成目标 */
    fun completeOnboarding(role: Role, focusDomains: List<Pair<String, String>>) {
        saveRole(role)
        val goals = loadGoals()
        focusDomains.forEach { (title, domain) ->
            if (goals.none { it.title == title }) goals.add(Goal(System.currentTimeMillis() + goals.size, title, domain))
        }
        saveGoals(goals)
        AppStorage.putPref("onboarded", true)
    }
}
