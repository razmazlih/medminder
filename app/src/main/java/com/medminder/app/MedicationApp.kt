package com.medminder.app

import android.app.Application
import com.medminder.app.reminder.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MedicationApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
    }
}
