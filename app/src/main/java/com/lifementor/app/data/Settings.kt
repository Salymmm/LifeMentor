package com.lifementor.app.data

import org.json.JSONObject

/**
 * LLM endpoint config. Uses the OpenAI-compatible /chat/completions protocol,
 * so the same app can talk to GLM, SiliconFlow, DeepSeek, Ollama and friends.
 */
data class Settings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
 val apiKey: String = "",
) {
    fun toJson(): String = JSONObject()
        .put("baseUrl", baseUrl).put("model", model).put("apiKey", apiKey)
        .toString()

    companion object {
        const val DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
        const val DEFAULT_MODEL = "glm-4-flash"

        fun fromJson(s: String): Settings = try {
            val o = JSONObject(s)
            Settings(
                baseUrl = o.optString("baseUrl", DEFAULT_BASE_URL).ifBlank { DEFAULT_BASE_URL },
                model = o.optString("model", DEFAULT_MODEL).ifBlank { DEFAULT_MODEL },
                apiKey = o.optString("apiKey", ""),
            )
        } catch (e: Exception) {
            Settings()
        }
    }
}
