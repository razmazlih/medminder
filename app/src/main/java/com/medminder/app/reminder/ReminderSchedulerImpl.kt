package com.medminder.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.medminder.app.domain.repository.MedicationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val repository: MedicationRepository
) : ReminderScheduler {

    override suspend fun scheduleDose(doseEventId: String, triggerAt: Instant, exactPreferred: Boolean) {
        val pendingIntent = pendingIntentForDose(doseEventId)
        val triggerMs = triggerAt.toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        if (exactPreferred && canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
        }
    }

    override suspend fun cancelDose(doseEventId: String) {
        alarmManager.cancel(pendingIntentForDose(doseEventId))
    }

    override suspend fun reschedulePending(ownerProfileId: String) {
        repository.getPendingDoseEvents(ownerProfileId).forEach { dose ->
            val trigger = dose.snoozeUntil ?: dose.scheduledAt
            scheduleDose(dose.id, trigger, exactPreferred = true)
        }
    }

    private fun pendingIntentForDose(doseEventId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(ReminderConstants.EXTRA_DOSE_EVENT_ID, doseEventId)
        return PendingIntent.getBroadcast(
            context,
            doseEventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
