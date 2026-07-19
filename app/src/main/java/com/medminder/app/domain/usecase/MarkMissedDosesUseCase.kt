package com.medminder.app.domain.usecase

import com.medminder.app.core.time.TimeProvider
import com.medminder.app.core.time.ZoneProvider
import com.medminder.app.domain.repository.MedicationRepository
import javax.inject.Inject

class MarkMissedDosesUseCase @Inject constructor(
    private val repository: MedicationRepository,
    private val clock: TimeProvider,
    private val zoneProvider: ZoneProvider
) {
    suspend operator fun invoke(): Int {
        val zone = zoneProvider.currentZone()
        val threshold = clock.now().atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        return repository.markMissedBefore(threshold)
    }
}
