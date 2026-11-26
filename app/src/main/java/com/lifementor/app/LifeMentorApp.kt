package com.lifementor.app

import android.app.Application
import android.util.Log
import com.lifementor.app.data.AppStorage
import com.lifementor.app.data.GameStore
import com.lifementor.app.notify.TaskReminders

class LifeMentorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStorage.init(this)
        runCatching { GameStore.recoverDecomposition() }
            .onFailure { Log.w("LifeMentor", "Could not finish saving an AI decomposition", it) }
        TaskReminders.initialize(this)
    }
}
