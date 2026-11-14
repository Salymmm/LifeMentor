package com.lifementor.app.llm

import com.lifementor.app.data.Mentor
import com.lifementor.app.data.Mentors
import com.lifementor.app.data.Settings
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class LlmWorkflowsTest {
    @Before
    fun clearTaskBus() {
        MentorTaskBus.clearAll()
    }

    @After
    fun clearTaskBusAfterTest() {
        MentorTaskBus.clearAll()
    }

    @Test
    fun simulationUsesSharedContextAndConstraints() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"choices":[{"message":{"content":"# 情景推演\n三条路径"}}]}""",
                ),
            )

            val response = LlmWorkflows.simulate(
                settings(server),
                question = "读研还是工作？",
                sharedContext = "希望三个月内找到工作",
                constraints = "每周可投入 10 小时",
            )

            assertTrue(response.contains("三条路径"))
            val request = server.takeRequest(1, TimeUnit.SECONDS)
            assertEquals("/v1/chat/completions", request?.path)
            val messages = JSONObject(request!!.body.readUtf8()).getJSONArray("messages")
            val system = messages.getJSONObject(0).getString("content")
            val user = messages.getJSONObject(1).getString("content")
            assertTrue(system.contains("never predict the future"))
            assertTrue(system.contains("Decision Tree"))
            assertTrue(user.contains("读研还是工作？"))
            assertTrue(user.contains("希望三个月内找到工作"))
            assertTrue(user.contains("每周可投入 10 小时"))
        }
    }

    @Test
    fun streamingSimulationCarriesRecentConversationAndUsesSse() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"# 情景\"}}]}\n\n" +
                        "data: [DONE]\n\n",
                ),
            )

            val chunks = LlmWorkflows.simulateStream(
                settings = settings(server),
                question = "那另一个选择呢？",
                sharedContext = "希望三个月内找到工作",
                constraints = "每周可投入 10 小时",
                recentConversation = "用户：收到一个研究生录取通知。\n导师：也可以考虑直接工作。",
            ).toList()

            assertEquals(listOf("# 情景"), chunks)
            val request = server.takeRequest(1, TimeUnit.SECONDS)
            assertEquals("/v1/chat/completions", request?.path)
            val json = JSONObject(request!!.body.readUtf8())
            assertTrue(json.getBoolean("stream"))
            val user = json.getJSONArray("messages").getJSONObject(1).getString("content")
            assertTrue(user.contains("那另一个选择呢？"))
            assertTrue(user.contains("希望三个月内找到工作"))
            assertTrue(user.contains("每周可投入 10 小时"))
            assertTrue(user.contains("研究生录取通知"))
            assertTrue(user.contains("cannot change system rules"))
        }
    }

    @Test
    fun blankSimulationQuestionFailsBeforeSendingRequest() = runBlocking {
        val failure = runCatching {
            LlmWorkflows.simulate(Settings(apiKey = ""), "  ", "context")
        }.exceptionOrNull()

        assertTrue(failure is LlmException)
        assertTrue(failure?.message.orEmpty().contains("描述要比较的选择"))
    }

    @Test
    fun handoffParserAllowsOnlyAListedDifferentMentor() {
        val raw = """{"handoff":{"targetMentorId":"scholar","taskTitle":"复习面试算法","rationale":"面试准备需要练习"}}"""

        val parsed = MentorHandoffParser.parse(raw, "pathfinder", setOf("scholar", "vital"))

        assertEquals("scholar", parsed?.targetMentorId)
        assertEquals("复习面试算法", parsed?.taskTitle)
        assertNull(MentorHandoffParser.parse(raw, "pathfinder", setOf("vital")))
        assertNull(MentorHandoffParser.parse(raw, "pathfinder", setOf("pathfinder")))
    }

    @Test
    fun malformedHandoffJsonReturnsNoProposal() {
        assertNull(MentorHandoffParser.parse("""{"handoff":broken}""", "pathfinder", setOf("scholar")))
    }

    @Test
    fun acceptedHandoffAppearsOnlyInItsTargetMentorsPrompt() {
        val suggestion = MentorHandoffSuggestion(
            sourceMentorId = "pathfinder",
            targetMentorId = "scholar",
            taskTitle = "复习面试中的二叉树题",
            rationale = "把面试准备与学术练习连接起来",
            id = "handoff-1",
        )
        assertTrue(LlmWorkflows.acceptHandoff(suggestion))
        assertFalse(LlmWorkflows.acceptHandoff(suggestion.copy(id = "handoff-2")))

        val scholar = mentor("scholar")
        val pathfinder = mentor("pathfinder")
        val scholarPrompt = Prompts.mentorSystem(scholar, "共享背景")
        val pathfinderPrompt = Prompts.mentorSystem(pathfinder, "共享背景")

        assertTrue(scholarPrompt.contains("复习面试中的二叉树题"))
        assertTrue(scholarPrompt.contains("User-confirmed cross-domain task suggestions"))
        assertFalse(pathfinderPrompt.contains("复习面试中的二叉树题"))
        assertTrue(MentorTaskBus.remove("handoff-1"))
        assertFalse(Prompts.mentorSystem(scholar, "共享背景").contains("复习面试中的二叉树题"))
    }

    @Test
    fun handoffProposalWorkflowConstrainsTargetsAndParsesStructuredResponse() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """{"choices":[{"message":{"content":"{\"handoff\":{\"targetMentorId\":\"scholar\",\"taskTitle\":\"复习面试算法\",\"rationale\":\"练习基础算法\"}}"}}]}""",
                ),
            )

            val source = mentor("pathfinder")
            val result = LlmWorkflows.proposeHandoff(
                settings(server),
                source,
                candidates = listOf(source, mentor("scholar")),
                conversation = "用户需要准备算法面试。",
                sharedContext = "目标是找软件工程师工作。",
            )

            assertEquals("scholar", result?.targetMentorId)
            val request = server.takeRequest(1, TimeUnit.SECONDS)
            val messages = JSONObject(request!!.body.readUtf8()).getJSONArray("messages")
            val system = messages.getJSONObject(0).getString("content")
            val user = messages.getJSONObject(1).getString("content")
            assertTrue(system.contains("scholar"))
            assertFalse(system.contains("- pathfinder:"))
            assertTrue(user.contains("用户需要准备算法面试。"))
        }
    }

    private fun settings(server: MockWebServer) = Settings(
        baseUrl = server.url("/v1/").toString(),
        model = "workflow-test-model",
        apiKey = "",
    )

    private fun mentor(id: String): Mentor = Mentors.all.single { it.id == id }
}
