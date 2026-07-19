package com.medminder.app.reminder

import java.time.Instant

interface ReminderScheduler {
    suspend fun scheduleDose(doseEventId: String, triggerAt: Instant, exactPreferred: Boolean)
    suspend fun cancelDose(doseEventId: String)
    suspend fun reschedulePending(ownerProfileId: String)
}
