package com.medminder.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.medminder.app.data.local.dao.SettingsDao
import com.medminder.app.domain.repository.MedicationRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    private companion object {
        const val ESCALATION_DELAY_MINUTES = 5
    }

    @Inject
    lateinit var notificationFactory: NotificationFactory

    @Inject
    lateinit var settingsDao: SettingsDao

    @Inject
    lateinit var repository: MedicationRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getStringExtra(ReminderConstants.EXTRA_DOSE_EVENT_ID) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentEvent = repository.getDoseEvent(doseId) ?: return@launch
                val settings = settingsDao.getSettings()
                val escalatedEnabled = settings?.escalatedReminderEnabled ?: true
                val isEscalatedAttempt = escalatedEnabled && currentEvent.reminderAttemptCount >= 1

                val shown = notificationFactory.showDoseReminder(doseId, escalated = isEscalatedAttempt)
                if (!shown) return@launch
                val interval = settings?.repeatIntervalMinutes ?: 10
                val maxCount = settings?.repeatMaxCount ?: 3
                val retryInterval = if (escalatedEnabled && currentEvent.reminderAttemptCount == 0) {
                    ESCALATION_DELAY_MINUTES
                } else {
                    interval
                }
                val nextRetry = repository.registerReminderAttempt(doseId, retryInterval, maxCount)
                if (nextRetry != null) {
                    reminderScheduler.scheduleDose(doseId, nextRetry, exactPreferred = true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
