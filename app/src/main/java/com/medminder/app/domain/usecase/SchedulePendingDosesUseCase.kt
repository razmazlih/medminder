package com.medminder.app.domain.usecase

import com.medminder.app.core.time.TimeProvider
import com.medminder.app.domain.model.DoseStatus
import com.medminder.app.domain.repository.MedicationRepository
import com.medminder.app.reminder.ReminderScheduler
import javax.inject.Inject

class SchedulePendingDosesUseCase @Inject constructor(
    private val repository: MedicationRepository,
    private val scheduler: ReminderScheduler,
    private val clock: TimeProvider
) {
    suspend operator fun invoke(ownerProfileId: String) {
        val now = clock.now()
        repository.getPendingDoseEvents(ownerProfileId).forEach { event ->
            if (event.status != DoseStatus.PENDING) return@forEach
            val triggerAt = event.snoozeUntil ?: event.scheduledAt
            if (triggerAt.isBefore(now)) return@forEach
            scheduler.scheduleDose(event.id, triggerAt, exactPreferred = true)
        }
    }
}
