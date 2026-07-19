package com.medminder.app

import com.medminder.app.core.time.TimeProvider
import com.medminder.app.core.time.ZoneProvider
import com.medminder.app.domain.model.ActionSource
import com.medminder.app.domain.model.DoseEvent
import com.medminder.app.domain.model.HiddenIntakeLog
import com.medminder.app.domain.model.IntakeAction
import com.medminder.app.domain.model.Medication
import com.medminder.app.domain.model.MedicationInput
import com.medminder.app.domain.repository.MedicationRepository
import com.medminder.app.domain.usecase.MarkMissedDosesUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkMissedDosesUseCaseTest {

    @Test
    fun `marks events before start of day in current zone`() = runTest {
        val now = Instant.parse("2026-03-28T22:30:00Z")
        val zone = ZoneId.of("Asia/Jerusalem")
        val repository = RecordingMissedRepository()
        val useCase = MarkMissedDosesUseCase(
            repository = repository,
            clock = MarkMissedFixedTimeProvider(now),
            zoneProvider = MarkMissedFixedZoneProvider(zone)
        )

        useCase()

        assertEquals(Instant.parse("2026-03-28T21:00:00Z"), repository.lastThreshold)
    }

    @Test
    fun `returns repository result and computes threshold in western timezone`() = runTest {
        val now = Instant.parse("2026-03-10T23:15:00Z")
        val zone = ZoneId.of("America/New_York")
        val repository = RecordingMissedRepository(markMissedReturnValue = 4)
        val useCase = MarkMissedDosesUseCase(
            repository = repository,
            clock = MarkMissedFixedTimeProvider(now),
            zoneProvider = MarkMissedFixedZoneProvider(zone)
        )

        val updated = useCase()

        assertEquals(4, updated)
        assertEquals(Instant.parse("2026-03-10T04:00:00Z"), repository.lastThreshold)
    }
}

private class MarkMissedFixedZoneProvider(private val zone: ZoneId) : ZoneProvider {
    override fun currentZone(): ZoneId = zone
}

private class MarkMissedFixedTimeProvider(private val now: Instant) : TimeProvider {
    override fun now(): Instant = now
}

private class RecordingMissedRepository(
    private val markMissedReturnValue: Int = 0
) : MedicationRepository {
    var lastThreshold: Instant? = null

    override fun observeTodayDoses(ownerProfileId: String, date: LocalDate): Flow<List<DoseEvent>> = emptyFlow()
    override fun observeHistory(ownerProfileId: String, from: LocalDate, to: LocalDate): Flow<List<DoseEvent>> = emptyFlow()
    override fun observeHiddenIntakeLogs(ownerProfileId: String): Flow<List<HiddenIntakeLog>> = emptyFlow()
    override fun observeMedications(ownerProfileId: String): Flow<List<Medication>> = emptyFlow()
    override suspend fun saveMedication(input: MedicationInput): String = ""
    override suspend fun updateMedication(input: MedicationInput) = Unit
    override suspend fun disableMedication(medicationId: String) = Unit
    override suspend fun restoreMedication(medicationId: String) = Unit
    override suspend fun deleteMedication(medicationId: String) = Unit
    override suspend fun generateDoseEvents(ownerProfileId: String, from: LocalDate, days: Int) = Unit
    override suspend fun applyDoseAction(doseEventId: String, action: IntakeAction, source: ActionSource, actedAt: Instant) = Unit
    override suspend fun updateDoseEventTime(doseEventId: String, newLocalTime: LocalTime) = Unit
    override suspend fun deleteDoseEvent(doseEventId: String) = Unit
    override suspend fun addAsNeededMedication(ownerProfileId: String, name: String): String = ""
    override suspend fun addOneTimeMedicationIntake(ownerProfileId: String, name: String) = Unit
    override suspend fun renameMedication(medicationId: String, name: String) = Unit
    override suspend fun markMedicationTakenNow(medicationId: String) = Unit
    override suspend fun getDoseEvent(doseEventId: String): DoseEvent? = null
    override suspend fun getPendingDoseEvents(ownerProfileId: String): List<DoseEvent> = emptyList()
    override suspend fun markMissedBefore(threshold: Instant): Int {
        lastThreshold = threshold
        return markMissedReturnValue
    }

    override suspend fun registerReminderAttempt(
        doseEventId: String,
        repeatIntervalMinutes: Int,
        repeatMaxCount: Int
    ): Instant? = null
}
