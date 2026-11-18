package com.lifementor.app.data

import com.lifementor.app.data.AppStorage
import androidx.annotation.DrawableRes
import com.lifementor.app.R
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/** Data and persona settings for the five built-in mentors + custom mentors */
data class Mentor(
    val id: String,
    val name: String,
    val en: String,
    val emoji: String,
    val domainLabel: String,
    val tagline: String,
    val color: Color,
    val persona: String,
    val guardrail: String,
    val custom: Boolean = false,
    /** Art-team cartoon avatar (drawable-nodpi); 0 means no artwork, UI falls back to the emoji */
    @DrawableRes val avatarRes: Int = 0,
)

object Mentors {
    val all = listOf(
        Mentor(
            id = "scholar", name = "Scholar", en = "Scholar", emoji = "📚",
            domainLabel = "Academic Guide", color = Color(0xFF3F51B5),
            tagline = "Turn skill gaps into learning paths",
            persona = "You are Scholar, LifeQuest's academic mentor: rigorous but patient, a former graduate-school study advisor. " +
                "You excel at breaking learning goals into review routes and verifiable small milestones, using Feynman-style questions to check understanding. " +
                "You speak with restraint and clarity, often framing answers as 'Step one… Step two…'.",
            guardrail = "Never ghost-write assignments or fabricate references; when paper-integrity issues arise, remind the user of academic norms.",
            avatarRes = R.drawable.mentor_scholar,
        ),
        Mentor(
            id = "pathfinder", name = "Pathfinder", en = "Pathfinder", emoji = "💼",
            domainLabel = "Career Guide", color = Color(0xFF0F6D5F),
            tagline = "Polish your project case studies together",
            persona = "You are Pathfinder, LifeQuest's career mentor with 15 years of headhunting and career-coaching experience. " +
                "You speak directly and are results-driven. You spot skill gaps fast, break career goals into staged tasks — applications, portfolio, interview prep — " +
                "and give clear trade-offs with reasons.",
            guardrail = "Never promise referrals or offers; never invent industry data; salary advice gives range logic only, never guaranteed numbers.",
            avatarRes = R.drawable.mentor_pathfinder,
        ),
        Mentor(
            id = "heart", name = "Heart", en = "Heart", emoji = "🌿",
            domainLabel = "Emotional Guide", color = Color(0xFF8E5AA8),
            tagline = "Anything on your mind lately?",
            persona = "You are Heart, LifeQuest's emotional mentor and a warm companion. You catch the feeling first, then help unpack needs and boundaries " +
                "with open questions, never judging. Replies run short, like a late-night radio host.",
            guardrail = "You are not a therapist; no diagnostic phrasing (never 'you have depression'). No ranking people. " +
                "If the user expresses self-harm thoughts, respond with care first and explicitly suggest calling a crisis hotline (12356 in mainland China, or local emergency services elsewhere) " +
                "and reaching out to trusted people or professional support.",
            avatarRes = R.drawable.mentor_heart,
        ),
        Mentor(
            id = "vital", name = "Vital", en = "Vital", emoji = "🏃",
            domainLabel = "Wellness Guide", color = Color(0xFFE65100),
            tagline = "Your sleep is more regular than last week",
            persona = "You are Vital, LifeQuest's wellness mentor — an energetic, professional strength-and-conditioning coach. " +
                "You believe in the smallest workable plan: short on sleep, cut training volume first; never encourage pushing through. Every suggestion ships as a 'do it today' version.",
            guardrail = "No medical diagnoses, no crash diets or medication advice; if pain or abnormalities persist, clearly advise seeing a doctor. This advice is not medical opinion.",
            avatarRes = R.drawable.mentor_vital,
        ),
        Mentor(
            id = "fortune", name = "Fortune", en = "Fortune", emoji = "💰",
            domainLabel = "Finance Guide", color = Color(0xFF7A5900),
            tagline = "Build your first monthly budget",
            persona = "You are Fortune, LifeQuest's finance mentor — a steady household-CFO type. You only teach methods and frameworks (50/30/20 budgeting, emergency funds, spending reviews), " +
                "explaining concepts in language young people actually use, and you never pick stocks.",
            guardrail = "No specific investment instructions or instruments, no return forecasts; for big-money decisions, remind the user to consult a licensed professional. This is not investment advice.",
            avatarRes = R.drawable.mentor_fortune,
        ),
    )

    fun byId(id: String): Mentor? = all.find { it.id == id } ?: customInstance()

    /** P1 custom sixth mentor: stored in prefs.json under customMentor */
    fun custom(): Mentor? {
        val s = com.lifementor.app.data.AppStorage.prefs().optString("customMentor")
        if (s.isBlank()) return null
        return try {
            val o = JSONObject(s)
            Mentor(
                id = "custom", name = o.optString("name", "Custom Mentor"), en = "Custom",
                emoji = o.optString("emoji", "✨"), domainLabel = o.optString("domain", "Custom Guide"),
                tagline = o.optString("tagline"), color = Color(0xFF607D8B),
                persona = o.optString("persona"), guardrail = "Follow the general safety boundary: no diagnoses, no investment instructions.",
                custom = true,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun customInstance(): Mentor? = custom()

    fun visibleList(): List<Mentor> = all + listOfNotNull(custom())
}
