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

/** Minimal OpenAI-compatible client: POST {baseUrl}/chat/completions */
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
                                throw LlmException("Unparsable response: " + text.take(200))
                            }
                            val choices = json.optJSONArray("choices")
                                ?: throw LlmException("No choices field in the response — check the model name and Base URL")
                            if (choices.length() == 0) throw LlmException("The model returned nothing")
                            val content = choices.optJSONObject(0)
                                ?.optJSONObject("message")?.optString("content").orEmpty()
                            if (content.isBlank()) throw LlmException("The model returned empty content. Please retry.")
                            continuation.resume(stripFences(content))
                        } catch (e: LlmException) {
                            continuation.resumeWithException(e)
                        } catch (e: IOException) {
                            continuation.resumeWithException(LlmException(networkError(e)))
                        } catch (e: Exception) {
                            continuation.resumeWithException(
                                LlmException("Unparsable response: ${e.message.orEmpty()}")
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
                            ?: throw LlmException("Empty streamed content. Please retry.")
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
                        close(LlmException("Unparsable streamed response: ${e.message.orEmpty()}"))
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
            throw LlmException("Unparsable streamed response: ${data.take(200)}")
        }
        val choices = json.optJSONArray("choices")
            ?: throw LlmException("No choices field in the response — check the model name and Base URL")
        if (choices.length() == 0) return null
        return choices.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.opt("content") as? String
    }

    private fun networkError(e: IOException): String =
        "Network request failed: ${e.message}\nCheck the device/emulator network and whether the API Base URL is reachable from it"

    private fun httpError(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
                .ifBlank { body.take(200) }
        } catch (e: Exception) {
            body.take(200)
        }
        return when (code) {
            401 -> "Invalid or unauthorized API key (401): $detail"
            403 -> "Access denied (403): $detail"
            404 -> "Wrong endpoint (404): make sure the Base URL ends at /v4 or /v1. $detail"
            429 -> "Rate limited or free quota exhausted (429): $detail"
            else -> "Request failed (HTTP $code): $detail"
        }
    }

    /** Some models wrap the whole output in a ```markdown fence; strip it here */
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
