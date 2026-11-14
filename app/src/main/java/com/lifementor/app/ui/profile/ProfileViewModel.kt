package com.lifementor.app.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.Profile
import com.lifementor.app.llm.AiOperationRegistry
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    val profile = mutableStateOf(AppStorage.loadProfile())
    var savedAt by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            AiOperationRegistry.resetVersion.drop(1).collect {
                profile.value = Profile()
                savedAt = null
            }
        }
    }

    fun update(transform: (Profile) -> Profile) {
        profile.value = transform(profile.value)
    }

    fun save() {
        AppStorage.saveProfile(profile.value)
        savedAt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
    }
}
