package com.lifementor.app.data

import org.json.JSONObject

/** Life character card: created in the M2 onboarding flow */
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
        val stages = listOf(
            "Junior / Senior year", "Fresh graduate", "Grad student",
            "Job hunting", "0–3 years working", "Switching / gap year", "Other",
        )

        val focuses = listOf(
            Triple("Land a job I actually want", "pathfinder", "💼"),
            Triple("Grad school · research · GPA", "scholar", "📚"),
            Triple("Get income & savings in order", "fortune", "💰"),
            Triple("Steady sleep & training", "vital", "🏃"),
            Triple("Feelings & key relationships", "heart", "🌿"),
            Triple("Grow a real passion", "scholar", "🎨"),
        )

        fun fromJson(s: String): Role = try {
            val o = JSONObject(s)
            Role(o.optString("nickname"), o.optString("avatar", "🧭"), o.optString("stage"), o.optString("vision"))
        } catch (e: Exception) {
            Role()
        }
    }
}
