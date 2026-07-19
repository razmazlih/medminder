package com.medminder.app.reminder

interface NotificationCanceller {
    fun cancelDoseNotification(doseEventId: String)
}
