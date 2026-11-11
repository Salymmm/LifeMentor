package com.lifementor.app.llm

import com.lifementor.app.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmException(message: String) : Exception(message)

data class ChatMessage(val role: String, val content: String)

/** 极简 OpenAI 兼容客户端：POST {baseUrl}/chat/completions */
object LlmClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .build()

    suspend fun chat(settings: Settings, messages: List<ChatMessage>): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", settings.model)
                .put("temperature", 0.7)
                .put(
                    "messages",
                    JSONArray().apply {
                        messages.forEach {
                            put(JSONObject().put("role", it.role).put("content", it.content))
                        }
                    }
                )
            val url = settings.baseUrl.trimEnd('/') + "/chat/completions"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${settings.apiKey}")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            try {
                http.newCall(request).execute().use { resp ->
                    val text = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) throw LlmException(httpError(resp.code, text))
                    val json = try {
                        JSONObject(text)
                    } catch (e: Exception) {
                        throw LlmException("返回内容无法解析：" + text.take(200))
                    }
                    val choices = json.optJSONArray("choices")
                        ?: throw LlmException("返回中没有 choices 字段，请检查模型名与 API 地址是否正确")
                    if (choices.length() == 0) throw LlmException("模型返回为空")
                    val content = choices.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content").orEmpty()
                    if (content.isBlank()) throw LlmException("模型返回了空内容，请重试")
                    stripFences(content)
                }
            } catch (e: IOException) {
                throw LlmException(
                    "网络请求失败：${e.message}\n请检查手机/模拟器网络，以及 API 地址是否可从该设备访问"
                )
            }
        }

    private fun httpError(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
                .ifBlank { body.take(200) }
        } catch (e: Exception) {
            body.take(200)
        }
        return when (code) {
            401 -> "API Key 无效或权限不足（401）：$detail"
            403 -> "访问被拒绝（403）：$detail"
            404 -> "接口地址不对（404）：请确认 Base URL 填到 /v4 或 /v1 层级。$detail"
            429 -> "触发限流或免费额度用尽（429）：$detail"
            else -> "请求失败（HTTP $code）：$detail"
        }
    }

    /** 有些模型会把整篇输出包在 ```markdown 围栏里，这里剥掉 */
    private fun stripFences(raw: String): String {
        val t = raw.trim()
        if (!t.startsWith("```") || !t.endsWith("```")) return t
        var inner = t.removePrefix("```").removeSuffix("```").trim()
        if (inner.startsWith("markdown") || inner.startsWith("md")) {
            inner = inner.drop(2).trim()
            if (inner.startsWith("\n")) inner = inner.drop(1)
        }
        return inner.trim()
    }
}
