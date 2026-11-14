package com.lifementor.app.llm

import com.lifementor.app.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LlmClientCancellationTest {
    @Test
    fun cancellingChatCancelsAPendingResponse() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val response = """{"choices":[{"message":{"content":"late response"}}]}""" + " ".repeat(32_768)
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setChunkedBody(response, 64)
                    .throttleBody(64, 100, TimeUnit.MILLISECONDS),
            )
            val settings = Settings(
                baseUrl = server.url("/v1/").toString(),
                model = "cancellation-test-model",
            )
            val request = async(Dispatchers.IO) {
                runCatching {
                    LlmClient.chat(settings, listOf(ChatMessage("user", "Say hello")))
                }
            }

            assertNotNull("Expected a chat completion request", server.takeRequest(2, TimeUnit.SECONDS))
            request.cancel()

            withTimeout(2_000) { request.join() }
            assertTrue("Cancellation must complete without waiting for the delayed response", request.isCancelled)
        }
    }
}
