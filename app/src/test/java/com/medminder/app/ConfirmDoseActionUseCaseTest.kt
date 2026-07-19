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
import com.medminder.app.domain.usecase.ConfirmDoseActionUseCase
import com.medminder.app.reminder.NotificationCanceller
import com.medminder.app.reminder.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ConfirmDoseActionUseCaseTest {

    @Test
    fun `taken action cancels alarm`() = runTest {
        val repo = FakeMedicationRepository()
        val scheduler = FakeReminderScheduler()
        val notificationFactory = FakeNotificationFactory()
        val useCase = ConfirmDoseActionUseCase(repo, scheduler, notificationFactory, FakeTimeProvider)

        useCase("dose_1", IntakeAction.TAKEN, ActionSource.APP)

        assertEquals("dose_1", scheduler.cancelledDoseId)
        assertEquals("dose_1", notificationFactory.cancelledDoseId)
    }

    @Test
    fun `taken action updates repository and cancels alarm`() = runTest {
        val repo = FakeMedicationRepository()
        val scheduler = FakeReminderScheduler()
        val notificationFactory = FakeNotificationFactory()
        val useCase = ConfirmDoseActionUseCase(repo, scheduler, notificationFactory, FakeTimeProvider)

        useCase("dose_1", IntakeAction.TAKEN, ActionSource.NOTIFICATION)

        assertEquals("dose_1", scheduler.cancelledDoseId)
        assertEquals("dose_1", notificationFactory.cancelledDoseId)
        assertEquals("dose_1", repo.lastUpdatedDoseId)
        assertEquals(IntakeAction.TAKEN, repo.lastAction)
        assertEquals(ActionSource.NOTIFICATION, repo.lastSource)
        assertEquals(FakeTimeProvider.now(), repo.lastActedAt)
        assertEquals(1, repo.applyDoseActionCalls)
    }

    @Test
    fun `use case always passes current time to repository action`() = runTest {
        val repo = FakeMedicationRepository()
        val scheduler = FakeReminderScheduler()
        val notificationFactory = FakeNotificationFactory()
        val useCase = ConfirmDoseActionUseCase(repo, scheduler, notificationFactory, FakeTimeProvider)

        useCase("dose_2", IntakeAction.TAKEN, ActionSource.SYSTEM)

        assertNotNull(repo.lastActedAt)
        assertEquals(Instant.parse("2026-02-13T10:00:00Z"), repo.lastActedAt)
    }
}

private object FakeTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.parse("2026-02-13T10:00:00Z")
}

private class FakeReminderScheduler : ReminderScheduler {
    var cancelledDoseId: String? = null
    var scheduledDoseId: String? = null

    override suspend fun scheduleDose(doseEventId: String, triggerAt: Instant, exactPreferred: Boolean) {
        scheduledDoseId = doseEventId
    }

    override suspend fun cancelDose(doseEventId: String) {
        cancelledDoseId = doseEventId
    }

    override suspend fun reschedulePending(ownerProfileId: String) = Unit
}

private class FakeNotificationFactory : NotificationCanceller {
    var cancelledDoseId: String? = null

    override fun cancelDoseNotification(doseEventId: String) {
        cancelledDoseId = doseEventId
    }
}

private class FakeMedicationRepository : MedicationRepository {
    var lastUpdatedDoseId: String? = null
    var lastAction: IntakeAction? = null
    var lastSource: ActionSource? = null
    var lastActedAt: Instant? = null
    var applyDoseActionCalls: Int = 0
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
    ) {
        lastUpdatedDoseId = doseEventId
        lastAction = action
        lastSource = source
        lastActedAt = actedAt
        applyDoseActionCalls += 1
    }

    override suspend fun updateDoseEventTime(doseEventId: String, newLocalTime: LocalTime) = Unit
    override suspend fun deleteDoseEvent(doseEventId: String) = Unit
    override suspend fun addAsNeededMedication(ownerProfileId: String, name: String): String = ""
    override suspend fun addOneTimeMedicationIntake(ownerProfileId: String, name: String) = Unit
    override suspend fun renameMedication(medicationId: String, name: String) = Unit
    override suspend fun markMedicationTakenNow(medicationId: String) = Unit

    override suspend fun getDoseEvent(doseEventId: String): DoseEvent? {
        return DoseEvent(
            id = doseEventId,
            ownerProfileId = "owner",
            medicationId = "m1",
            medicationName = "Med",
            scheduledAt = Instant.parse("2026-02-13T10:00:00Z"),
            localDate = LocalDate.of(2026, 2, 13),
            localTime = java.time.LocalTime.of(10, 0),
            status = DoseStatus.PENDING,
            actionSource = ActionSource.APP,
            reminderAttemptCount = 1,
            snoozeUntil = null,
            respondedAt = Instant.parse("2026-02-13T10:00:00Z")
        )
    }

    override suspend fun getPendingDoseEvents(ownerProfileId: String): List<DoseEvent> = emptyList()
    override suspend fun markMissedBefore(threshold: Instant): Int = 0
    override suspend fun registerReminderAttempt(
        doseEventId: String,
        repeatIntervalMinutes: Int,
        repeatMaxCount: Int
    ): Instant? = null
}
