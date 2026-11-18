package com.lifementor.app.data

import org.json.JSONArray
import org.json.JSONObject

/** A goal (a main-line objective under the yearly / long-term vision) */
data class Goal(
    val id: Long,
    val title: String,
    val domain: String,
    val status: String = "active", // active | done
    val createdAt: Long = System.currentTimeMillis(),
    val decomposed: Boolean = false,
) {
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

/** Task-tree node: level 1 = quarterly milestone, 2 = weekly task, 3 = daily action */
data class TaskItem(
    val id: Long,
    val goalId: Long,
    val parentId: Long?,
    val level: Int,
    val title: String,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val xpGiven: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("goalId", goalId)
        .put("parentId", if (parentId == null) JSONObject.NULL else parentId)
        .put("level", level).put("title", title)
        .put("done", done).put("doneAt", doneAt ?: JSONObject.NULL).put("xpGiven", xpGiven)

    companion object {
        fun from(o: JSONObject): TaskItem = TaskItem(
            o.getLong("id"), o.getLong("goalId"),
            if (o.isNull("parentId")) null else o.getLong("parentId"),
            o.getInt("level"), o.optString("title"),
            o.optBoolean("done"), if (o.isNull("doneAt")) null else o.getLong("doneAt"),
            o.optBoolean("xpGiven"),
        )
    }
}

/** XP ledger entry */
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

/** A chat message */
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

/** An achievement */
data class Achievement(
    val emoji: String,
    val name: String,
    val desc: String,
    val earned: Boolean,
)
