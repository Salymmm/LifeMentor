package com.lifementor.app.data

import org.json.JSONObject

/** 人生角色卡：M2 创建向导的产物 */
data class Role(
    val nickname: String = "",
    val avatar: String = "🧭",
    val stage: String = "",
    val vision: String = "",
) {
    fun toJson(): String = JSONObject()
        .put("nickname", nickname).put("avatar", avatar)
        .put("stage", stage).put("vision", vision)
        .toString()

    companion object {
        val stages = listOf("大三/大四在读", "应届毕业中", "研究生", "求职中", "职场 1-3 年", "转型/间隔年中", "其他")

        val focuses = listOf(
            Triple("找一份心仪的工作", "pathfinder", "💼"),
            Triple("升学 / 科研 /  GPA", "scholar", "📚"),
            Triple("打理收入与储蓄", "fortune", "💰"),
            Triple("规律作息与运动", "vital", "🏃"),
            Triple("情绪与重要关系", "heart", "🌿"),
            Triple("发展一项热爱特长", "scholar", "🎨"),
        )

        fun fromJson(s: String): Role = try {
            val o = JSONObject(s)
            Role(o.optString("nickname"), o.optString("avatar", "🧭"), o.optString("stage"), o.optString("vision"))
        } catch (e: Exception) {
            Role()
        }
    }
}
