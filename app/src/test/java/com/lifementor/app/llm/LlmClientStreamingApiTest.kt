package com.lifementor.app.llm

import com.lifementor.app.data.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmClientStreamingApiTest {
    @Test
    fun exposesStreamingChatEntryPoint() {
        assertNotNull("LlmClient must expose a streaming chat entry point", chatStreamMethod())
    }

    @Test
    fun emitsContentDeltasFromChunkedSseEvents() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setChunkedBody(
                    """
                    data: {"choices":[{"delta":{"content":"Hello"}}]}

                    data: {"choices":[{"delta":{"content":" world"}}]}

                    data: [DONE]

                    """.trimIndent(),
                    7,
                )
            )

            val received = mutableListOf<String>()
            streamFrom(server).collect { received += it }

            assertEquals(listOf("Hello", " world"), received)
        }
    }

    @Test
    fun sendsStreamingFlagInOpenAiCompatibleRequest() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("data: [DONE]\n\n"))

            streamFrom(server).collect()

            val request = server.takeRequest(1, TimeUnit.SECONDS)
            assertNotNull("Expected a chat completion request", request)
            assertEquals("POST", request!!.method)
            assertEquals("/v1/chat/completions", request.path)
            assertTrue(
                "The request must opt in to streaming",
                JSONObject(request.body.readUtf8()).getBoolean("stream"),
            )
        }
    }

    @Test
    fun reportsLocalizedHttpErrorForFailedStreamRequest() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setBody("""{"error":{"message":"rate limit reached"}}"""),
            )

            val failure = runCatching { streamFrom(server).collect() }.exceptionOrNull()

            assertTrue("Expected an LlmException, got $failure", failure is LlmException)
            assertTrue(failure!!.message.orEmpty().contains("触发限流"))
            assertTrue(failure.message.orEmpty().contains("rate limit reached"))
        }
    }

    @Test
    fun reportsLocalizedNetworkErrorWhenConnectionIsRefused() = runBlocking {
        val server = MockWebServer()
        server.start()
        val settings = settingsFor(server)
        server.shutdown()

        val failure = runCatching { streamFrom(settings).collect() }.exceptionOrNull()

        assertTrue("Expected an LlmException, got $failure", failure is LlmException)
        assertTrue(failure!!.message.orEmpty().contains("网络请求失败"))
        assertTrue(failure.message.orEmpty().contains("请检查手机/模拟器网络"))
    }

    @Test
    fun cancellingCollectorCancelsTheOkHttpCall() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val firstEvent = "data: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\n"
            server.enqueue(
                MockResponse()
                    .setChunkedBody(firstEvent + " ".repeat(32_768), 64)
                    .throttleBody(64, 100, TimeUnit.MILLISECONDS),
            )

            val cancelledCall = CompletableDeferred<Boolean>()
            val client = OkHttpClient.Builder()
                .eventListener(object : EventListener() {
                    override fun callFailed(call: Call, ioe: IOException) {
                        if (call.isCanceled()) cancelledCall.complete(true)
                    }
                })
                .build()
            val firstToken = CompletableDeferred<String>()
            val collector = launch {
                streamFrom(server, client).take(1).collect { firstToken.complete(it) }
            }

            try {
                assertEquals("first", withTimeout(5_000) { firstToken.await() })
                withTimeout(2_000) { collector.join() }
                assertTrue("Collector cancellation must cancel its OkHttp call", withTimeout(2_000) {
                    cancelledCall.await()
                })
            } finally {
                collector.cancelAndJoin()
            }
        }
    }

    @Test
    fun slowCollectorAppliesBackpressureToTheResponseBody() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val events = (0 until 256).joinToString("\n\n") { index ->
                "data: {\"choices\":[{\"delta\":{\"content\":\"$index\"}}]}"
            } + "\n\ndata: [DONE]\n\n"
            server.enqueue(MockResponse().setChunkedBody(events, 64))

            val responseFinished = CompletableDeferred<Unit>()
            val client = OkHttpClient.Builder()
                .eventListener(object : EventListener() {
                    override fun responseBodyEnd(call: Call, byteCount: Long) {
                        responseFinished.complete(Unit)
                    }
                })
                .build()
            val firstToken = CompletableDeferred<Unit>()
            val releaseCollector = CompletableDeferred<Unit>()
            val received = mutableListOf<String>()
            val collector = launch {
                streamFrom(server, client).collect { token ->
                    received += token
                    if (received.size == 1) {
                        firstToken.complete(Unit)
                        releaseCollector.await()
                    }
                }
            }

            try {
                withTimeout(5_000) { firstToken.await() }
                assertTrue(
                    "The response body must wait when the collector is slow",
                    withTimeoutOrNull(400) { responseFinished.await() } == null,
                )

                releaseCollector.complete(Unit)
                withTimeout(5_000) { collector.join() }

                assertEquals((0 until 256).map { it.toString() }, received)
                withTimeout(2_000) { responseFinished.await() }
            } finally {
                releaseCollector.complete(Unit)
                collector.cancelAndJoin()
            }
        }
    }

    private fun streamFrom(server: MockWebServer): Flow<String> {
        val method = chatStreamMethod()
            ?: throw AssertionError("LlmClient must expose a streaming chat entry point")
        return streamFromMethod(method, settingsFor(server))
    }

    private fun streamFrom(server: MockWebServer, client: OkHttpClient): Flow<String> =
        LlmClient.chatStream(settingsFor(server), listOf(ChatMessage("user", "Say hello")), client)

    private fun streamFrom(settings: Settings): Flow<String> {
        val method = chatStreamMethod()
            ?: throw AssertionError("LlmClient must expose a streaming chat entry point")
        return streamFromMethod(method, settings)
    }

    private fun settingsFor(server: MockWebServer) = Settings(
        baseUrl = server.url("/v1/").toString(),
        model = "stream-test-model",
    )

    private fun streamFromMethod(method: java.lang.reflect.Method, settings: Settings): Flow<String> {
        val messages = listOf(ChatMessage("user", "Say hello"))
        @Suppress("UNCHECKED_CAST")
        return method.invoke(LlmClient, settings, messages) as Flow<String>
    }

    private fun chatStreamMethod() = LlmClient::class.java.methods.firstOrNull {
        it.name == "chatStream" && it.parameterTypes.size == 2
    }
}
