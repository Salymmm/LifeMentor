package com.lifementor.app.data

import org.json.JSONObject

/** The user's career profile. Every field is free text, stored as JSON in the app's private directory. */
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

    /** JSON with storage keys, for local persistence */
    fun toStoredJson(): String = JSONObject()
        .put("name", name).put("stage", stage).put("location", location).put("current", current)
        .put("interests", interests).put("academic", academic).put("experience", experience)
        .put("skills", skills).put("personality", personality).put("values", values)
        .put("goals", goals).put("extra", extra)
        .toString()

    /** JSON with human-readable keys for the LLM (empty fields omitted to reduce noise) */
    fun toPromptJson(): String {
        val o = JSONObject()
        fun put(k: String, v: String) { if (v.isNotBlank()) o.put(k, v) }
        put("Name", name)
        put("Life stage", stage)
        put("City", location)
        put("Current status", current)
        put("Interests", interests)
        put("Academic background", academic)
        put("Experience", experience)
        put("Skills", skills)
        put("Personality", personality)
        put("Values & preferences", values)
        put("Goals", goals)
        put("Extra context", extra)
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
