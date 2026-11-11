package com.lifementor.app.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.Profile

class ProfileViewModel : ViewModel() {
    val profile = mutableStateOf(AppStorage.loadProfile())
    var savedAt by mutableStateOf<String?>(null)
        private set

    fun update(transform: (Profile) -> Profile) {
        profile.value = transform(profile.value)
    }

    fun save() {
        AppStorage.saveProfile(profile.value)
        savedAt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
    }
}
