package com.medminder.app.domain.usecase

import com.medminder.app.domain.model.DoseEvent
import com.medminder.app.domain.repository.MedicationRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class Get30DayHistoryUseCase @Inject constructor(
    private val repository: MedicationRepository
) {
    operator fun invoke(ownerProfileId: String, today: LocalDate): Flow<List<DoseEvent>> {
        return repository.observeHistory(ownerProfileId, today.minusDays(29), today)
    }
}
