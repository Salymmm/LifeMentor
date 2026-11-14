package com.lifementor.app.llm

import com.lifementor.app.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LlmException(message: String) : Exception(message)

data class ChatMessage(val role: String, val content: String)

/** 极简 OpenAI 兼容客户端：POST {baseUrl}/chat/completions */
object LlmClient {
    private const val STREAM_BUFFER_CAPACITY = 64

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .build()

    suspend fun chat(settings: Settings, messages: List<ChatMessage>): String {
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

        return suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isActive) return
                    continuation.resumeWithException(LlmException(networkError(e)))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!continuation.isActive) return
                        try {
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
                            continuation.resume(stripFences(content))
                        } catch (e: LlmException) {
                            continuation.resumeWithException(e)
                        } catch (e: IOException) {
                            continuation.resumeWithException(LlmException(networkError(e)))
                        } catch (e: Exception) {
                            continuation.resumeWithException(
                                LlmException("返回内容无法解析：${e.message.orEmpty()}")
                            )
                        }
                    }
                }
            })
        }
    }

    /** OpenAI-compatible SSE stream. Emits the content from each choices[0].delta.content. */
    fun chatStream(settings: Settings, messages: List<ChatMessage>): Flow<String> =
        chatStream(settings, messages, http)

    /** Internal overload lets tests observe call cancellation with an isolated OkHttp client. */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun chatStream(
        settings: Settings,
        messages: List<ChatMessage>,
        client: OkHttpClient,
    ): Flow<String> {
        val body = JSONObject()
            .put("model", settings.model)
            .put("temperature", 0.7)
            .put("stream", true)
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

        return callbackFlow {
            val call = client.newCall(request)
            val reader = launch(Dispatchers.IO) {
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            throw LlmException(
                                httpError(response.code, response.body?.string().orEmpty())
                            )
                        }
                        val responseBody = response.body
                            ?: throw LlmException("返回内容为空，请重试")
                        readSse(responseBody) { data ->
                            if (data == "[DONE]") return@readSse false
                            val content = streamDelta(data)
                            if (!content.isNullOrEmpty()) send(content)
                            true
                        }
                    }
                    close()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LlmException) {
                    close(e)
                } catch (e: IOException) {
                    if (isActive) close(LlmException(networkError(e)))
                } catch (e: Exception) {
                    if (isActive) {
                        close(LlmException("流式返回内容无法解析：${e.message.orEmpty()}"))
                    }
                }
            }
            awaitClose {
                call.cancel()
                reader.cancel()
            }
        }.buffer(STREAM_BUFFER_CAPACITY)
    }

    private suspend fun readSse(
        body: okhttp3.ResponseBody,
        onData: suspend (String) -> Boolean,
    ) {
        val source = body.source()
        val eventData = StringBuilder()
        var stopped = false

        suspend fun dispatchEvent() {
            if (eventData.isEmpty()) return
            val data = eventData.toString()
            eventData.setLength(0)
            if (!onData(data)) stopped = true
        }

        while (!stopped) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                dispatchEvent()
                continue
            }
            val colon = line.indexOf(':')
            val field = if (colon < 0) line else line.substring(0, colon)
            if (field != "data") continue
            var value = if (colon < 0) "" else line.substring(colon + 1)
            if (value.startsWith(' ')) value = value.substring(1)
            if (eventData.isNotEmpty()) eventData.append('\n')
            eventData.append(value)
        }
        if (!stopped) dispatchEvent()
    }

    private fun streamDelta(data: String): String? {
        val json = try {
            JSONObject(data)
        } catch (e: Exception) {
            throw LlmException("流式返回内容无法解析：${data.take(200)}")
        }
        val choices = json.optJSONArray("choices")
            ?: throw LlmException("返回中没有 choices 字段，请检查模型名与 API 地址是否正确")
        if (choices.length() == 0) return null
        return choices.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.opt("content") as? String
    }

    private fun networkError(e: IOException): String =
        "网络请求失败：${e.message}\n请检查手机/模拟器网络，以及 API 地址是否可从该设备访问"

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
