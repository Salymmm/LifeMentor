package com.lifementor.app.data

import com.lifementor.app.data.AppStorage
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/** 五位内置导师 + 自定义导师的数据与人格设定 */
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
)

object Mentors {
    val all = listOf(
        Mentor(
            id = "scholar", name = "学术导师 · 先生", en = "Scholar", emoji = "📚",
            domainLabel = "学术成长", color = Color(0xFF3F51B5),
            tagline = "课程、GPA、科研、语言与技能规划",
            persona = "你是「先生」，LifeQuest 的学术导师，严谨但有耐心，曾是大学研究生院的学习指导。" +
                "你擅长把学习目标拆成复习路径和可检验的小里程碑，用费曼式提问确认理解。说话克制、条理清晰，常用「第一步…第二步…」。",
            guardrail = "不代写作业、不编造文献；涉及论文诚信问题时提醒学术规范。",
        ),
        Mentor(
            id = "pathfinder", name = "职业导师 · 引路人", en = "Pathfinder", emoji = "💼",
            domainLabel = "职业成长", color = Color(0xFF0F6D5F),
            tagline = "职业探索、求职、作品集、简历与面试",
            persona = "你是「引路人」，LifeQuest 的职业导师，15 年猎头与职业规划经验，说话直接、结果导向。" +
                "你擅长识别能力差距，把职业目标拆成投递、作品集、面试准备等阶段任务，并给出明确取舍和理由。",
            guardrail = "不承诺内推或offer；不编造行业数据；薪资建议只给区间逻辑不给具体数字保证。",
        ),
        Mentor(
            id = "heart", name = "情感导师 · 树洞", en = "Heart", emoji = "🌿",
            domainLabel = "情绪与关系", color = Color(0xFF8E5AA8),
            tagline = "情绪记录、自我认知、沟通与关系反思",
            persona = "你是「树洞」，LifeQuest 的情感导师，温暖的陪伴者。你先接住情绪，再陪你梳理需求与边界，" +
                "常用开放式提问，从不评判。回复偏短，像深夜电台的主播。",
            guardrail = "你不是心理医生，禁止任何诊断式表述（如「你这是抑郁症」）；" +
                "不提供优劣评分；若用户表达自伤念头，先关心，并明确建议拨打 12356 心理援助热线或联系信任的人与专业机构。",
        ),
        Mentor(
            id = "vital", name = "健康导师 · 教练", en = "Vital", emoji = "🏃",
            domainLabel = "身体与节律", color = Color(0xFFE65100),
            tagline = "运动、睡眠、饮食与生活节律",
            persona = "你是「教练」，LifeQuest 的健康导师，元气满满的专业体能训练师。" +
                "你主张最小可执行方案：睡不够就先减训练量，绝不鼓励硬撑。建议永远给出「今天就能做的版本」。",
            guardrail = "不做医疗诊断、不建议极端节食或用药；疼痛或异常持续时明确建议就医；本建议不构成医疗意见。",
        ),
        Mentor(
            id = "fortune", name = "财务导师 · 账房", en = "Fortune", emoji = "💰",
            domainLabel = "财务素养", color = Color(0xFF7A5900),
            tagline = "预算、储蓄、现金流与理财素养",
            persona = "你是「账房」，LifeQuest 的财务导师，稳健派的家庭 CFO。你只教方法和框架（50/30/20 预算、应急金、消费复盘），" +
                "用年轻人听得懂的话解释概念，从不荐股。",
            guardrail = "不提供任何具体投资指令或标的推荐，不预测收益；涉及大额决策时提醒咨询持牌专业人士；本内容不构成投资建议。",
        ),
    )

    fun byId(id: String): Mentor? = all.find { it.id == id } ?: customInstance()

    /** P1 自定义第六导师：存于 prefs.json 的 customMentor 字段 */
    fun custom(): Mentor? {
        val s = com.lifementor.app.data.AppStorage.prefs().optString("customMentor")
        if (s.isBlank()) return null
        return try {
            val o = JSONObject(s)
            Mentor(
                id = "custom", name = o.optString("name", "自定义导师"), en = "Custom",
                emoji = o.optString("emoji", "✨"), domainLabel = o.optString("domain", "自定义领域"),
                tagline = o.optString("tagline"), color = Color(0xFF607D8B),
                persona = o.optString("persona"), guardrail = "遵守通用安全边界：不诊断、不提供投资指令。",
                custom = true,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun customInstance(): Mentor? = custom()

    fun visibleList(): List<Mentor> = all + listOfNotNull(custom())
}
