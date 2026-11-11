package com.lifementor.app

import android.app.Application
import com.lifementor.app.data.AppStorage

class LifeMentorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppStorage.init(this)
    }
}
