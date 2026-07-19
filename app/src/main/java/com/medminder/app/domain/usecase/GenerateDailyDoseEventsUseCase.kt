package com.medminder.app.domain.usecase

import com.medminder.app.domain.repository.MedicationRepository
import java.time.LocalDate
import javax.inject.Inject

class GenerateDailyDoseEventsUseCase @Inject constructor(
    private val repository: MedicationRepository
) {
    suspend operator fun invoke(ownerProfileId: String, from: LocalDate, days: Int = 2) {
        repository.generateDoseEvents(ownerProfileId, from, days)
    }
}
