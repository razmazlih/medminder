package com.medminder.app.domain.usecase

import com.medminder.app.domain.model.MedicationInput
import com.medminder.app.domain.repository.MedicationRepository
import javax.inject.Inject

class SaveMedicationUseCase @Inject constructor(
    private val repository: MedicationRepository,
    private val generateDailyDoseEventsUseCase: GenerateDailyDoseEventsUseCase,
    private val schedulePendingDosesUseCase: SchedulePendingDosesUseCase
) {
    suspend operator fun invoke(input: MedicationInput): String {
        val id = if (input.id == null) repository.saveMedication(input) else {
            repository.updateMedication(input)
            input.id
        }
        generateDailyDoseEventsUseCase(input.ownerProfileId, input.startDate, 3)
        schedulePendingDosesUseCase(input.ownerProfileId)
        return id ?: ""
    }
}
