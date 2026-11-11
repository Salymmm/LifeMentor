package com.lifementor.app.data

import org.json.JSONObject

/**
 * 大模型接口配置。采用 OpenAI 兼容的 /chat/completions 协议，
 * 因此同一个 App 可以直接对接智谱、SiliconFlow、DeepSeek、Ollama 等服务。
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
