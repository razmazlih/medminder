package com.medminder.app

import com.medminder.app.core.time.TimeProvider
import com.medminder.app.domain.model.ActionSource
import com.medminder.app.domain.model.DoseEvent
import com.medminder.app.domain.model.DoseStatus
import com.medminder.app.domain.model.HiddenIntakeLog
import com.medminder.app.domain.model.IntakeAction
import com.medminder.app.domain.model.Medication
import com.medminder.app.domain.model.MedicationInput
import com.medminder.app.domain.repository.MedicationRepository
import com.medminder.app.domain.usecase.SchedulePendingDosesUseCase
import com.medminder.app.reminder.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePendingDosesUseCaseTest {

    @Test
    fun `schedules only current and future pending events`() = runTest {
        val now = Instant.parse("2026-02-13T10:00:00Z")
        val repo = FakePendingRepository(
            events = listOf(
                fakeDose("past", "2026-02-13T09:59:00Z"),
                fakeDose("now", "2026-02-13T10:00:00Z"),
                fakeDose("future", "2026-02-13T10:01:00Z"),
                fakeDose("future_missed", "2026-02-13T10:02:00Z", status = DoseStatus.MISSED),
                fakeDose("future_taken", "2026-02-13T10:03:00Z", status = DoseStatus.TAKEN)
            )
        )
        val scheduler = RecordingReminderScheduler()
        val useCase = SchedulePendingDosesUseCase(repo, scheduler, FixedTimeProvider(now))

        useCase("owner")

        assertEquals(listOf("now", "future"), scheduler.scheduledDoseIds)
        assertTrue(scheduler.calls.all { it.exactPreferred })
    }

    @Test
    fun `uses snooze time as trigger when snooze is set`() = runTest {
        val now = Instant.parse("2026-02-13T10:00:00Z")
        val snoozeTarget = Instant.parse("2026-02-13T10:15:00Z")
        val repo = FakePendingRepository(
            events = listOf(
                fakeDose(
                    id = "snoozed",
                    isoTime = "2026-02-13T10:05:00Z",
                    snoozeUntil = snoozeTarget
                )
            )
        )
        val scheduler = RecordingReminderScheduler()
        val useCase = SchedulePendingDosesUseCase(repo, scheduler, FixedTimeProvider(now))

        useCase("owner")

        assertEquals(1, scheduler.calls.size)
        assertEquals("snoozed", scheduler.calls.first().doseEventId)
        assertEquals(snoozeTarget, scheduler.calls.first().triggerAt)
        assertTrue(scheduler.calls.first().exactPreferred)
    }

    @Test
    fun `skips event when snooze time is already in the past`() = runTest {
        val now = Instant.parse("2026-02-13T10:00:00Z")
        val repo = FakePendingRepository(
            events = listOf(
                fakeDose(
                    id = "snooze_past",
                    isoTime = "2026-02-13T11:00:00Z",
                    snoozeUntil = Instant.parse("2026-02-13T09:59:00Z")
                )
            )
        )
        val scheduler = RecordingReminderScheduler()
        val useCase = SchedulePendingDosesUseCase(repo, scheduler, FixedTimeProvider(now))

        useCase("owner")

        assertTrue(scheduler.calls.isEmpty())
    }
}

private fun fakeDose(
    id: String,
    isoTime: String,
    status: DoseStatus = DoseStatus.PENDING,
    snoozeUntil: Instant? = null
): DoseEvent {
    val scheduledAt = Instant.parse(isoTime)
    return DoseEvent(
        id = id,
        ownerProfileId = "owner",
        medicationId = "m1",
        medicationName = "Med",
        scheduledAt = scheduledAt,
        localDate = LocalDate.of(2026, 2, 13),
        localTime = LocalTime.of(10, 0),
        status = status,
        actionSource = null,
        reminderAttemptCount = 0,
        snoozeUntil = snoozeUntil,
        respondedAt = null
    )
}

private class FixedTimeProvider(private val now: Instant) : TimeProvider {
    override fun now(): Instant = now
}

private class RecordingReminderScheduler : ReminderScheduler {
    data class ScheduledCall(
        val doseEventId: String,
        val triggerAt: Instant,
        val exactPreferred: Boolean
    )

    val scheduledDoseIds = mutableListOf<String>()
    val calls = mutableListOf<ScheduledCall>()

    override suspend fun scheduleDose(doseEventId: String, triggerAt: Instant, exactPreferred: Boolean) {
        scheduledDoseIds += doseEventId
        calls += ScheduledCall(doseEventId = doseEventId, triggerAt = triggerAt, exactPreferred = exactPreferred)
    }

    override suspend fun cancelDose(doseEventId: String) = Unit
    override suspend fun reschedulePending(ownerProfileId: String) = Unit
}

private class FakePendingRepository(private val events: List<DoseEvent>) : MedicationRepository {
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
    override suspend fun applyDoseAction(
        doseEventId: String,
        action: IntakeAction,
        source: ActionSource,
        actedAt: Instant
    ) = Unit

    override suspend fun updateDoseEventTime(doseEventId: String, newLocalTime: LocalTime) = Unit
    override suspend fun deleteDoseEvent(doseEventId: String) = Unit
    override suspend fun addAsNeededMedication(ownerProfileId: String, name: String): String = ""
    override suspend fun addOneTimeMedicationIntake(ownerProfileId: String, name: String) = Unit
    override suspend fun renameMedication(medicationId: String, name: String) = Unit
    override suspend fun markMedicationTakenNow(medicationId: String) = Unit

    override suspend fun getDoseEvent(doseEventId: String): DoseEvent? = null
    override suspend fun getPendingDoseEvents(ownerProfileId: String): List<DoseEvent> = events
    override suspend fun markMissedBefore(threshold: Instant): Int = 0
    override suspend fun registerReminderAttempt(
        doseEventId: String,
        repeatIntervalMinutes: Int,
        repeatMaxCount: Int
    ): Instant? = null
}
