package com.medminder.app.domain.usecase

import com.medminder.app.domain.model.DoseEvent
import com.medminder.app.domain.repository.MedicationRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetTodayTimelineUseCase @Inject constructor(
    private val repository: MedicationRepository
) {
    operator fun invoke(ownerProfileId: String, date: LocalDate): Flow<List<DoseEvent>> {
        return repository.observeTodayDoses(ownerProfileId, date)
    }
}
