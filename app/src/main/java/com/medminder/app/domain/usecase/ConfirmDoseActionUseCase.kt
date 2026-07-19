package com.medminder.app.domain.usecase

import com.medminder.app.core.time.TimeProvider
import com.medminder.app.domain.model.ActionSource
import com.medminder.app.domain.model.IntakeAction
import com.medminder.app.domain.repository.MedicationRepository
import com.medminder.app.reminder.NotificationCanceller
import com.medminder.app.reminder.ReminderScheduler
import javax.inject.Inject

class ConfirmDoseActionUseCase @Inject constructor(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
    private val notificationCanceller: NotificationCanceller,
    private val clock: TimeProvider
) {
    suspend operator fun invoke(doseEventId: String, action: IntakeAction, source: ActionSource) {
        repository.applyDoseAction(doseEventId, action, source, clock.now())
        scheduler.cancelDose(doseEventId)
        notificationCanceller.cancelDoseNotification(doseEventId)
    }
}
