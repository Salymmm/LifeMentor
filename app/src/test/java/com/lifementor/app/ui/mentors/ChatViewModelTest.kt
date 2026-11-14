package com.lifementor.app.ui.mentors

import com.lifementor.app.data.Mentor
import com.lifementor.app.data.Mentors
import com.lifementor.app.data.Msg
import com.lifementor.app.data.Settings
import com.lifementor.app.llm.ChatMessage
import com.lifementor.app.llm.MentorHandoffSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @Test
    fun englishSelfHarmMessageTriggersTheCrisisSupportCard() {
        Dispatchers.setMain(StandardTestDispatcher())
        try {
            val vm = ChatViewModel(FakeMentorChatActions())
            vm.open("heart")
            vm.draft.value = "I don't want to live anymore."

            vm.send()

            assertTrue(vm.crisis.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun regularChatStreamsIntoTheAssistantMessageAndSavesIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actions = FakeMentorChatActions().apply { stream = flowOf("你好", "，世界") }
        try {
            val vm = ChatViewModel(actions)
            vm.open("heart")
            vm.draft.value = "我最近有点焦虑"

            vm.send()
            runCurrent()

            assertEquals(listOf("user", "assistant"), vm.messages.value.map { it.role })
            assertEquals("你好，世界", vm.messages.value.last().content)
            assertEquals("你好，世界", actions.savedChats.getValue("heart").last().content)
            assertEquals("我最近有点焦虑", actions.streamMessages?.last()?.content)
            assertFalse(vm.sending.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stopKeepsThePartialReplyAndCancelsTheStream() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actions = FakeMentorChatActions().apply {
            stream = flow {
                try {
                    emit("已经收到的部分")
                    awaitCancellation()
                } finally {
                    streamCancelled = true
                }
            }
        }
        try {
            val vm = ChatViewModel(actions)
            vm.open("heart")
            vm.draft.value = "继续说"

            vm.send()
            runCurrent()
            assertTrue(vm.sending.value)
            assertEquals("已经收到的部分", vm.messages.value.last().content)

            vm.cancelSending()
            runCurrent()

            assertTrue(actions.streamCancelled)
            assertFalse(vm.sending.value)
            assertNull(vm.error.value)
            assertEquals("已经收到的部分", actions.savedChats.getValue("heart").last().content)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun simulationModeCallsTheSimulationWorkflowWithSharedContext() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actions = FakeMentorChatActions().apply { simulationStream = flowOf("# 推演结果") }
        try {
            val vm = ChatViewModel(actions)
            vm.open("pathfinder")
            vm.messages.value = listOf(
                Msg(1, "user", "我收到了研究生录取通知"),
                Msg(2, "assistant", "也可以考虑直接工作"),
            )
            vm.simulationMode.value = true
            vm.draft.value = "那另一个选择呢？"

            vm.send()
            runCurrent()

            assertEquals("那另一个选择呢？", actions.simulationQuestion)
            assertEquals("测试画像", actions.simulationContext)
            assertTrue(actions.simulationRecentConversation.orEmpty().contains("研究生录取通知"))
            assertEquals("# 推演结果", vm.messages.value.last().content)
            assertNull(actions.streamMessages)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stoppingSimulationCancelsItsStreamAndKeepsPartialResult() {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val actions = FakeMentorChatActions().apply {
            simulationStream = flow {
                try {
                    emit("已经输出的推演路径")
                    awaitCancellation()
                } finally {
                    streamCancelled = true
                }
            }
        }
        try {
            val vm = ChatViewModel(actions, scope)
            vm.open("pathfinder")
            vm.simulationMode.value = true
            vm.draft.value = "读研还是工作？"

            vm.send()
            assertEquals("已经输出的推演路径", vm.messages.value.last().content)
            vm.cancelSending()

            assertTrue(actions.streamCancelled)
            assertFalse(vm.sending.value)
            assertEquals("已经输出的推演路径", actions.savedChats.getValue("pathfinder").last().content)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun handoffIsOnlyAcceptedAfterExplicitConfirmation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val suggestion = MentorHandoffSuggestion(
            sourceMentorId = "heart",
            targetMentorId = "scholar",
            taskTitle = "制定备考节奏",
            rationale = "需要兼顾情绪和学习安排",
        )
        val actions = FakeMentorChatActions().apply { handoffReply = suggestion }
        try {
            val vm = ChatViewModel(actions)
            vm.open("heart")
            vm.messages.value = listOf(
                Msg(1, "user", "我准备考研"),
                Msg(2, "assistant", "我们可以先梳理压力来源"),
            )

            vm.requestHandoff()
            runCurrent()

            assertEquals(suggestion, vm.handoffSuggestion.value)
            assertNull(actions.acceptedHandoff)
            assertTrue(actions.handoffConversation.contains("我准备考研"))

            vm.acceptHandoff()

            assertEquals(suggestion, actions.acceptedHandoff)
            assertNull(vm.handoffSuggestion.value)
            assertTrue(vm.handoffStatus.value.orEmpty().contains("学术导师"))
            assertTrue(vm.handoffStatus.value.orEmpty().contains("本次应用运行期间"))
            assertTrue(vm.handoffStatus.value.orEmpty().contains("退出重启"))
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FakeMentorChatActions : MentorChatActions {
    var stream: Flow<String> = flowOf()
    var streamMessages: List<ChatMessage>? = null
    var streamCancelled = false
    var simulationStream: Flow<String> = flowOf()
    var simulationQuestion: String? = null
    var simulationContext: String? = null
    var simulationRecentConversation: String? = null
    var handoffReply: MentorHandoffSuggestion? = null
    var handoffConversation: String = ""
    var acceptedHandoff: MentorHandoffSuggestion? = null
    val savedChats = mutableMapOf<String, List<Msg>>()

    override fun settings() = Settings(apiKey = "test-key")
    override fun sharedContext() = "测试画像"
    override fun loadChat(mentorId: String) = savedChats[mentorId].orEmpty()
    override fun saveChat(mentorId: String, messages: List<Msg>) {
        savedChats[mentorId] = messages.toList()
    }
    override fun mentor(mentorId: String): Mentor? = Mentors.all.find { it.id == mentorId }
    override fun mentors(): List<Mentor> = Mentors.all
    override fun chatStream(settings: Settings, messages: List<ChatMessage>): Flow<String> {
        streamMessages = messages
        return stream
    }
    override fun simulateStream(
        settings: Settings,
        question: String,
        sharedContext: String,
        recentConversation: String,
    ): Flow<String> {
        simulationQuestion = question
        simulationContext = sharedContext
        simulationRecentConversation = recentConversation
        streamMessages = null
        return simulationStream
    }
    override suspend fun proposeHandoff(
        settings: Settings,
        source: Mentor,
        candidates: List<Mentor>,
        conversation: String,
        sharedContext: String,
    ): MentorHandoffSuggestion? {
        handoffConversation = conversation
        return handoffReply
    }
    override fun acceptHandoff(suggestion: MentorHandoffSuggestion): Boolean {
        acceptedHandoff = suggestion
        return true
    }
}
