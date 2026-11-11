package com.lifementor.app.ui.onboarding

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.lifementor.app.data.GameStore
import com.lifementor.app.data.Role

class OnboardingViewModel : ViewModel() {
    var step by mutableStateOf(0)
    val avatars = listOf("🧭", "🌟", "🦉", "🐳", "🌱", "🔥", "🗻", "🦊", "🐼", "🚀", "🎧", "🎨")

    val nickname = mutableStateOf("")
    val avatar = mutableStateOf("🧭")
    val stage = mutableStateOf<String?>(null)
    val vision = mutableStateOf("")
    val focus = mutableStateListOf<String>()

    fun finish() {
        val role = Role(
            nickname = nickname.value.trim().ifBlank { "探索者" },
            avatar = avatar.value,
            stage = stage.value ?: "",
            vision = vision.value.trim(),
        )
        val picks = focus.map { t ->
            Role.focuses.first { it.first == t }.let { it.first to it.second }
        }
        GameStore.completeOnboarding(role, picks)
    }
}
