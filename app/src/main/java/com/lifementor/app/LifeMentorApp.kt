package com.lifementor.app

import android.app.Application
import com.lifementor.app.data.AppStorage
import com.lifementor.app.notify.TaskReminders

class LifeMentorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStorage.init(this)
        TaskReminders.initialize(this)
    }
}
