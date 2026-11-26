package com.lifementor.app.ui.mentors

import com.lifementor.app.data.Goal
import com.lifementor.app.data.Mentor
import com.lifementor.app.data.Mentors
import com.lifementor.app.data.Msg
import com.lifementor.app.data.Settings
import com.lifementor.app.data.TaskAgenda
import com.lifementor.app.data.TaskItem
import com.lifementor.app.llm.ChatMessage
import com.lifementor.app.llm.MentorHandoffSuggestion
import com.lifementor.app.llm.TaskQueryRequest
import com.lifementor.app.llm.TaskQueryScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
    fun taskQuestionsReadTheCurrentRecordsWithoutIncludingThemInOrdinaryChat() = runTest {
        val actions = FakeMentorChatActions().apply {
            stream = flowOf("Read chapter three is due today.")
            goalRecords = listOf(Goal(1, "Study", "learning"))
            taskRecords = listOf(
                TaskItem(1, 1, null, 3, "Read chapter three", dueDate = TaskAgenda.dateMillis(LocalDate.now())),
                TaskItem(2, 1, null, 3, "Unscheduled action"),
            )
        }
        val vm = ChatViewModel(actions, backgroundScope)
        vm.open("heart")
        vm.draft.value = "What tasks are due today?"
        vm.send()
        runCurrent()

        var payload = actions.streamMessages.orEmpty().joinToString("\n") { it.content }
        assertTrue(payload.contains("Read chapter three"))
        assertFalse(payload.contains("Unscheduled action"))
        assertFalse(actions.streamMessages.orEmpty().first().content.contains("Read chapter three"))

        actions.taskRecords = listOf(actions.taskRecords.first().copy(title = "Read chapter four"))
        actions.stream = flowOf("Read chapter four is due today.")
        vm.draft.value = "What tasks are due today?"
        vm.send()
        runCurrent()

        payload = actions.streamMessages.orEmpty().joinToString("\n") { it.content }
        assertTrue(payload.contains("Read chapter four"))
        assertFalse(payload.contains("Read chapter three"))

        vm.open("scholar")
        vm.open("heart")
        vm.draft.value = "I feel tired"
        vm.send()
        runCurrent()

        payload = actions.streamMessages.orEmpty().joinToString("\n") { it.content }
        assertFalse(payload.contains("Read chapter four"))
        assertFalse(payload.contains("Read chapter three"))
        assertTrue(actions.streamMessages.orEmpty().first().content.contains("Task scope"))
    }

    @Test
    fun anExplicitOptOutOverridesTheTaskScopePicker() = runTest {
        val actions = FakeMentorChatActions().apply {
            stream = flowOf("Here is some general advice")
            goalRecords = listOf(Goal(1, "Study", "learning"))
            taskRecords = listOf(TaskItem(1, 1, null, 3, "Private task title"))
        }
        val vm = ChatViewModel(actions, backgroundScope)
        vm.open("heart")
        vm.taskScopeSelection.value = ChatTaskScope("All tasks", TaskQueryRequest(setOf(TaskQueryScope.ALL)))
        vm.draft.value = "Don't read my tasks. I just want advice."
        vm.send()
        runCurrent()

        assertFalse(actions.streamMessages.orEmpty().any { "Private task title" in it.content })
        assertNull(vm.taskQueryPage.value)
    }

    @Test
    fun requestingClearPreservesTheCurrentConversation() = runTest {
        val original = listOf(Msg(2, "assistant", "A useful reply"))
        val actions = FakeMentorChatActions().apply { savedChats["heart"] = original }
        val vm = ChatViewModel(actions, backgroundScope)
        vm.open("heart")

        vm.requestClearHistory()
        assertEquals("Heart", vm.clearChatMentor.value?.name)
        vm.dismissClearHistory()

        assertEquals(original, vm.messages.value)
        assertEquals(original, actions.savedChats["heart"])
    }

    @Test
    fun clearConfirmationOnlyAffectsTheMentorThatRequestedIt() = runTest {
        val original = listOf(Msg(2, "assistant", "A useful reply"))
        val actions = FakeMentorChatActions().apply {
            savedChats["heart"] = original
            savedChats["scholar"] = original
        }
        val vm = ChatViewModel(actions, backgroundScope)
        vm.open("heart")
        vm.requestClearHistory()
        vm.open("scholar")
        vm.confirmClearHistory()
        assertEquals(original, actions.savedChats["scholar"])

        vm.requestClearHistory()
        vm.confirmClearHistory()

        assertTrue(actions.savedChats.getValue("scholar").isEmpty())
        assertEquals(original, actions.savedChats["heart"])
    }

    @Test
    fun explicitTaskScopePagesThroughTheResults() = runTest {
        val actions = FakeMentorChatActions().apply {
            stream = flowOf("Here are matching tasks")
            goalRecords = listOf(Goal(1, "Study", "learning", status = "done"))
            taskRecords = (1L..21L).map { TaskItem(it, 1, null, 3, "Action #$it", done = true) }
        }
        val vm = ChatViewModel(actions, backgroundScope)
        vm.open("heart")
        vm.taskScopeSelection.value = ChatTaskScope("All tasks", TaskQueryRequest(setOf(TaskQueryScope.ALL)))
        vm.send()
        runCurrent()

        assertEquals(21, vm.taskQueryPage.value?.total)
        assertEquals(20, vm.taskQueryPage.value?.shown)
        assertFalse(actions.streamMessages.orEmpty().last().content.contains("Action #21"))

        actions.taskRecords = actions.taskRecords.drop(1)
        vm.nextTaskPage()
        runCurrent()

        assertEquals(1, vm.taskQueryPage.value?.shown)
        assertNull(vm.taskQueryPage.value?.nextOffset)
        assertTrue(actions.streamMessages.orEmpty().last().content.contains("Action #21"))
    }

    @Test
    fun aSuggestedTaskUsesOnlyTheEditedFieldsWhenConfirmed() = runTest {
        val reply = Msg(2, "assistant", "Read the next chapter")
        val parent = TaskItem(3, 1, null, 3, "Course notes")
        val actions = FakeMentorChatActions().apply {
            savedChats["heart"] = listOf(reply)
            goalRecords = listOf(Goal(1, "Study", "learning"))
            taskRecords = listOf(parent)
            suggestionResponse = { """{"title":"  Read the next chapter  ","goalId":99}""" }
        }
        val vm = ChatViewModel(actions, backgroundScope)
        vm.open("heart")

        vm.requestTaskSuggestion(reply.time)
        runCurrent()
        assertEquals("Read the next chapter", vm.taskSuggestionTitle.value)
        assertTrue(actions.suggestionMessages.orEmpty().last().content.contains(reply.content))
        assertTrue(actions.addedTasks.isEmpty())

        val date = TaskAgenda.dateMillis(LocalDate.of(2026, 12, 1))
        vm.confirmTaskSuggestion("Annotate chapter three", 1, parent.id, date)
        vm.confirmTaskSuggestion("Annotate chapter three", 1, parent.id, date)

        assertEquals(listOf(TaskItem.manual(10, 1, "Annotate chapter three", parent, date)), actions.addedTasks)
        assertEquals(listOf(reply), actions.savedChats["heart"])
    }

    @Test
    fun taskSuggestionRequiresAnActiveGoalAtConfirmation() = runTest {
        val reply = Msg(2, "assistant", "Read the next chapter")
        val goal = Goal(1, "Study", "learning")
        val actions = FakeMentorChatActions().apply {
            savedChats["heart"] = listOf(reply)
            goalRecords = listOf(goal)
            suggestionResponse = { """{"title":"Read the next chapter"}""" }
        }
        val vm = ChatViewModel(actions, backgroundScope)
        vm.open("heart")
        vm.requestTaskSuggestion(reply.time)
        runCurrent()
        actions.goalRecords = listOf(goal.copy(status = "done"))

        vm.confirmTaskSuggestion("Read the next chapter", goal.id, null, null)

        assertTrue(actions.addedTasks.isEmpty())
        assertEquals("Read the next chapter", vm.taskSuggestionTitle.value)
    }

    @Test
    fun dismissingTheTaskFormDiscardsTheSuggestion() = runTest {
        val reply = Msg(2, "assistant", "Read the next chapter")
        val actions = FakeMentorChatActions().apply {
            savedChats["heart"] = listOf(reply)
            goalRecords = listOf(Goal(1, "Study", "learning"))
            suggestionResponse = { """{"title":"Read the next chapter"}""" }
        }
        val vm = ChatViewModel(actions, backgroundScope)
        vm.open("heart")
        vm.requestTaskSuggestion(reply.time)
        runCurrent()

        vm.dismissTaskSuggestion()
        vm.confirmTaskSuggestion("Read the next chapter", 1, null, null)

        assertNull(vm.taskSuggestionTitle.value)
        assertTrue(actions.addedTasks.isEmpty())
    }

    @Test
    fun blankTaskSuggestionKeepsTheConversation() = runTest {
        val reply = Msg(2, "assistant", "That sounds frustrating")
        val actions = FakeMentorChatActions().apply {
            savedChats["heart"] = listOf(reply)
            suggestionResponse = { """{"title":"  "}""" }
        }
        val vm = ChatViewModel(actions, backgroundScope)
        vm.open("heart")
        vm.requestTaskSuggestion(reply.time)
        runCurrent()

        assertNull(vm.taskSuggestionTitle.value)
        assertTrue(vm.taskSuggestionStatus.value.orEmpty().contains("No concrete action"))
        assertEquals(listOf(reply), vm.messages.value)
        assertTrue(actions.addedTasks.isEmpty())
    }

    @Test
    fun lateSuggestionFromPreviousMentorCannotOpenTheTaskForm() = runTest {
        val reply = Msg(2, "assistant", "Read the next chapter")
        val release = CompletableDeferred<Unit>()
        val actions = FakeMentorChatActions().apply {
            savedChats["heart"] = listOf(reply)
            suggestionResponse = {
                withContext(NonCancellable) { release.await() }
                """{"title":"Read the next chapter"}"""
            }
        }
        val vm = ChatViewModel(actions, backgroundScope)
        vm.open("heart")
        vm.requestTaskSuggestion(reply.time)
        runCurrent()

        vm.open("scholar")
        release.complete(Unit)
        runCurrent()

        assertNull(vm.taskSuggestionTitle.value)
        assertNull(vm.taskSuggestionReplyTime.value)
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
            val persisted = Msg.from(actions.savedChats.getValue("heart").last().toJson())
            assertFalse(persisted.complete)
            vm.requestTaskSuggestion(persisted.time)
            assertNull(vm.taskSuggestionReplyTime.value)
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
            assertTrue(vm.handoffStatus.value.orEmpty().contains("Scholar"))
            assertTrue(vm.handoffStatus.value.orEmpty().contains("during this app session"))
            assertTrue(vm.handoffStatus.value.orEmpty().contains("restart"))
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
    var goalRecords: List<Goal> = emptyList()
    var taskRecords: List<TaskItem> = emptyList()
    var suggestionResponse: suspend () -> String = { """{"title":null}""" }
    var suggestionMessages: List<ChatMessage>? = null
    val addedTasks = mutableListOf<TaskItem>()
    val savedChats = mutableMapOf<String, List<Msg>>()

    override fun settings() = Settings(apiKey = "test-key")
    override fun sharedContext() = "测试画像"
    override fun goals() = goalRecords
    override fun tasks() = taskRecords
    override fun addTask(goalId: Long, title: String, parentId: Long?, dueDate: Long?) {
        addedTasks.add(TaskItem.manual(10, goalId, title, taskRecords.find { it.id == parentId }, dueDate))
    }
    override fun loadChat(mentorId: String) = savedChats[mentorId].orEmpty()
    override fun saveChat(mentorId: String, messages: List<Msg>) {
        savedChats[mentorId] = messages.toList()
    }
    override fun mentor(mentorId: String): Mentor? = Mentors.all.find { it.id == mentorId }
    override fun mentors(): List<Mentor> = Mentors.all
    override suspend fun chat(settings: Settings, messages: List<ChatMessage>): String {
        suggestionMessages = messages
        return suggestionResponse()
    }
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
