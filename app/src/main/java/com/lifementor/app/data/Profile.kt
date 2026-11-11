package com.lifementor.app.data

import org.json.JSONObject

/** 用户的职业画像。所有字段都是自由文本，存成 JSON 放在应用私有目录。 */
data class Profile(
    val name: String = "",
    val stage: String = "",
    val location: String = "",
    val current: String = "",
    val interests: String = "",
    val academic: String = "",
    val experience: String = "",
    val skills: String = "",
    val personality: String = "",
    val values: String = "",
    val goals: String = "",
    val extra: String = "",
) {
    fun isEmpty(): Boolean =
        listOf(name, stage, location, current, interests, academic, experience, skills, personality, values, goals, extra)
            .all { it.isBlank() }

    /** 给本地存储用的英文 key JSON */
    fun toStoredJson(): String = JSONObject()
        .put("name", name).put("stage", stage).put("location", location).put("current", current)
        .put("interests", interests).put("academic", academic).put("experience", experience)
        .put("skills", skills).put("personality", personality).put("values", values)
        .put("goals", goals).put("extra", extra)
        .toString()

    /** 给大模型看的中文 key JSON（空字段不送入，减少噪音） */
    fun toPromptJson(): String {
        val o = JSONObject()
        fun put(k: String, v: String) { if (v.isNotBlank()) o.put(k, v) }
        put("称呼", name)
        put("人生阶段", stage)
        put("所在城市", location)
        put("当前状态", current)
        put("兴趣", interests)
        put("学术背景", academic)
        put("经历", experience)
        put("技能", skills)
        put("性格特质", personality)
        put("价值观与偏好", values)
        put("目标", goals)
        put("补充信息", extra)
        return o.toString(2)
    }

    companion object {
        fun fromStoredJson(s: String): Profile = try {
            val o = JSONObject(s)
            Profile(
                name = o.optString("name"),
                stage = o.optString("stage"),
                location = o.optString("location"),
                current = o.optString("current"),
                interests = o.optString("interests"),
                academic = o.optString("academic"),
                experience = o.optString("experience"),
                skills = o.optString("skills"),
                personality = o.optString("personality"),
                values = o.optString("values"),
                goals = o.optString("goals"),
                extra = o.optString("extra"),
            )
        } catch (e: Exception) {
            Profile()
        }
    }
}
