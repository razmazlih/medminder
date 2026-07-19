package com.medminder.app.domain.repository

import com.medminder.app.domain.model.ActionSource
import com.medminder.app.domain.model.DoseEvent
import com.medminder.app.domain.model.IntakeAction
import com.medminder.app.domain.model.Medication
import com.medminder.app.domain.model.MedicationInput
import com.medminder.app.domain.model.HiddenIntakeLog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

interface MedicationRepository {
    fun observeTodayDoses(ownerProfileId: String, date: LocalDate): Flow<List<DoseEvent>>
    fun observeHistory(ownerProfileId: String, from: LocalDate, to: LocalDate): Flow<List<DoseEvent>>
    fun observeHiddenIntakeLogs(ownerProfileId: String): Flow<List<HiddenIntakeLog>>
    fun observeMedications(ownerProfileId: String): Flow<List<Medication>>
    suspend fun saveMedication(input: MedicationInput): String
    suspend fun updateMedication(input: MedicationInput)
    suspend fun disableMedication(medicationId: String)
    suspend fun restoreMedication(medicationId: String)
    suspend fun deleteMedication(medicationId: String)
    suspend fun generateDoseEvents(ownerProfileId: String, from: LocalDate, days: Int)
    suspend fun applyDoseAction(doseEventId: String, action: IntakeAction, source: ActionSource, actedAt: Instant)
    suspend fun updateDoseEventTime(doseEventId: String, newLocalTime: LocalTime)
    suspend fun deleteDoseEvent(doseEventId: String)
    suspend fun addAsNeededMedication(ownerProfileId: String, name: String): String
    suspend fun addOneTimeMedicationIntake(ownerProfileId: String, name: String)
    suspend fun renameMedication(medicationId: String, name: String)
    suspend fun markMedicationTakenNow(medicationId: String)
    suspend fun getDoseEvent(doseEventId: String): DoseEvent?
    suspend fun getPendingDoseEvents(ownerProfileId: String): List<DoseEvent>
    suspend fun markMissedBefore(threshold: Instant): Int
    suspend fun registerReminderAttempt(
        doseEventId: String,
        repeatIntervalMinutes: Int,
        repeatMaxCount: Int
    ): Instant?
}
