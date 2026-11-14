package com.lifementor.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Role
import com.lifementor.app.data.Settings
import com.lifementor.app.llm.AiOperationRegistry
import com.lifementor.app.llm.ChatMessage
import com.lifementor.app.llm.LlmClient
import com.lifementor.app.llm.MentorTaskBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel : ViewModel() {
    private val loaded = AppStorage.loadSettings()
    val baseUrl = mutableStateOf(loaded.baseUrl)
    val model = mutableStateOf(loaded.model)
    val apiKey = mutableStateOf(loaded.apiKey)

    var savedAt by mutableStateOf<String?>(null)
        private set
    var testing by mutableStateOf(false)
        private set
    var testResult by mutableStateOf<String?>(null)
        private set
    private var testJob: Job? = null

    // 人生角色编辑（创建向导的后续修改入口）
    private val loadedRole = GameStore.loadRole()
    val roleNickname = mutableStateOf(loadedRole.nickname)
    val roleAvatar = mutableStateOf(loadedRole.avatar)
    val roleStage = mutableStateOf(loadedRole.stage)
    val roleVision = mutableStateOf(loadedRole.vision)
    var roleSavedAt by mutableStateOf<String?>(null)
        private set

    fun saveRole() {
        GameStore.saveRole(
            Role(roleNickname.value.trim(), roleAvatar.value, roleStage.value.trim(), roleVision.value.trim())
        )
        roleSavedAt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun wipeAll() {
        AiOperationRegistry.invalidateAndCancel()
        MentorTaskBus.clearAll()
        GameStore.clearAll()
        AppStorage.wipeDocsAndProfile()
        File(AppStorage.appCtx.cacheDir, "shared").deleteRecursively()
        baseUrl.value = Settings.DEFAULT_BASE_URL
        model.value = Settings.DEFAULT_MODEL
        apiKey.value = ""
        savedAt = null
        testing = false
        testResult = null
        roleNickname.value = Role().nickname
        roleAvatar.value = Role().avatar
        roleStage.value = Role().stage
        roleVision.value = Role().vision
        roleSavedAt = null
        testJob = null
    }

    fun save() {
        AppStorage.saveSettings(current())
        savedAt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun applyPreset(url: String, m: String) {
        baseUrl.value = url
        model.value = m
        testResult = "已填入预设，记得点「保存」"
    }

    fun test() {
        val s = current()
        if (s.apiKey.isBlank()) {
            testResult = "❌ 请先填写 API Key"
            return
        }
        testing = true
        testResult = null
        val operation = AiOperationRegistry.createJob(viewModelScope) { token ->
            try {
                val reply = LlmClient.chat(
                    s,
                    listOf(
                        ChatMessage(
                            "system",
                            "Reply in English only. Use English letters, digits, and standard punctuation only; do not output Chinese characters. Keep the answer concise.",
                        ),
                        ChatMessage("user", "If you can read this, reply with exactly: Connection successful"),
                    ),
                )
                if (AiOperationRegistry.isCurrent(token)) {
                    testResult = "✓ 连接成功，模型回复：${reply.take(30)}"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (AiOperationRegistry.isCurrent(token)) {
                    testResult = "❌ " + (e.message ?: e.toString())
                }
            } finally {
                if (testJob === coroutineContext.job) {
                    testJob = null
                    testing = false
                }
            }
        }
        testJob = operation.job
        operation.start()
    }

    private fun current() = Settings(
        baseUrl.value.trim(),
        model.value.trim(),
        apiKey.value.trim(),
    )
}
