package com.lifementor.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset

/** 目标（年度/长期愿景下的主线目标） */
data class Goal(
    val id: Long,
    val title: String,
    val domain: String,
    val status: String = "active", // active | done
    val createdAt: Long = System.currentTimeMillis(),
    val decomposed: Boolean = false,
) {
    fun edited(title: String, domain: String): Goal {
        require(title.isNotBlank()) { "目标名称不能为空" }
        return copy(title = title.trim(), domain = domain)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("domain", domain)
        .put("status", status).put("createdAt", createdAt).put("decomposed", decomposed)

    companion object {
        fun from(o: JSONObject): Goal = Goal(
            o.getLong("id"), o.optString("title"), o.optString("domain"),
            o.optString("status", "active"), o.optLong("createdAt"), o.optBoolean("decomposed"),
        )
    }
}

/** 任务树节点：parentId 表示层级；level 保留原有奖励档位，手动任务统一使用行动档位 3。 */
data class TaskItem(
    val id: Long,
    val goalId: Long,
    val parentId: Long?,
    val level: Int,
    val title: String,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val xpGiven: Boolean = false,
    val dueDate: Long? = null, // 日历日期，以 UTC 零点毫秒值保存；不是提醒触发时刻
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("goalId", goalId)
        .put("parentId", if (parentId == null) JSONObject.NULL else parentId)
        .put("level", level).put("title", title)
        .put("done", done).put("doneAt", doneAt ?: JSONObject.NULL).put("xpGiven", xpGiven)
        .put("dueDate", dueDate ?: JSONObject.NULL)

    fun dueDateText(): String? = dueDate?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
    }

    fun completed(at: Long): TaskItem = if (done) this else copy(done = true, doneAt = at)

    companion object {
        fun manual(id: Long, goalId: Long, title: String, parent: TaskItem?, dueDate: Long? = null): TaskItem {
            require(title.isNotBlank()) { "任务名称不能为空" }
            require(parent == null || parent.goalId == goalId) { "请选择同一目标下的上一级任务" }
            return TaskItem(id, goalId, parent?.id, 3, title.trim(), dueDate = dueDate)
        }

        fun from(o: JSONObject): TaskItem = TaskItem(
            o.getLong("id"), o.getLong("goalId"),
            if (o.isNull("parentId")) null else o.getLong("parentId"),
            o.getInt("level"), o.optString("title"),
            o.optBoolean("done"), if (o.isNull("doneAt")) null else o.getLong("doneAt"),
            o.optBoolean("xpGiven"),
            if (o.isNull("dueDate")) null else o.getLong("dueDate"),
        )
    }
}

/** XP 流水 */
data class XPEntry(
    val time: Long,
    val amount: Int,
    val reason: String,
    val domain: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("time", time).put("amount", amount).put("reason", reason).put("domain", domain)

    companion object {
        fun from(o: JSONObject): XPEntry =
            XPEntry(o.getLong("time"), o.getInt("amount"), o.optString("reason"), o.optString("domain"))

        fun listFrom(arr: JSONArray): List<XPEntry> =
            (0 until arr.length()).map { from(arr.getJSONObject(it)) }

        fun listToJson(list: List<XPEntry>): String {
            val a = JSONArray()
            list.forEach { a.put(it.toJson()) }
            return a.toString()
        }
    }
}

/** 聊天记录消息 */
data class Msg(
    val time: Long,
    val role: String, // user | assistant
    val content: String,
) {
    fun toJson(): JSONObject = JSONObject().put("time", time).put("role", role).put("content", content)

    companion object {
        fun from(o: JSONObject): Msg = Msg(o.getLong("time"), o.optString("role"), o.optString("content"))
    }
}

/** 成就 */
data class Achievement(
    val emoji: String,
    val name: String,
    val desc: String,
    val earned: Boolean,
)
