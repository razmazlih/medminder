package com.medminder.app.data.repository

import com.medminder.app.core.time.TimeProvider
import com.medminder.app.core.time.ZoneProvider
import com.medminder.app.data.local.dao.DoseEventDao
import com.medminder.app.data.local.dao.HiddenIntakeLogDao
import com.medminder.app.data.local.dao.MedicationDao
import com.medminder.app.data.local.entity.DoseEventEntity
import com.medminder.app.data.local.entity.HiddenIntakeLogEntity
import com.medminder.app.data.local.entity.MedicationEntity
import com.medminder.app.data.local.entity.MedicationTimeEntity
import com.medminder.app.data.local.entity.MedicationWeekdayEntity
import com.medminder.app.data.local.entity.MedicationWithSchedule
import com.medminder.app.data.sync.CloudSyncManager
import com.medminder.app.domain.model.ActionSource
import com.medminder.app.domain.model.DoseEvent
import com.medminder.app.domain.model.DoseStatus
import com.medminder.app.domain.model.FoodRelation
import com.medminder.app.domain.model.HiddenIntakeLog
import com.medminder.app.domain.model.IntakeLogOperation
import com.medminder.app.domain.model.IntakeAction
import com.medminder.app.domain.model.Medication
import com.medminder.app.domain.model.MedicationInput
import com.medminder.app.domain.model.ScheduleMode
import com.medminder.app.domain.repository.MedicationRepository
import com.medminder.app.domain.usecase.ScheduleEngine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class MedicationRepositoryImpl @Inject constructor(
    private val medicationDao: MedicationDao,
    private val doseEventDao: DoseEventDao,
    private val hiddenIntakeLogDao: HiddenIntakeLogDao,
    private val zoneProvider: ZoneProvider,
    private val clock: TimeProvider,
    private val scheduleEngine: ScheduleEngine,
    private val cloudSyncManager: CloudSyncManager
) : MedicationRepository {

    private companion object {
        const val DELETE_WINDOW_MS: Long = 2 * 60 * 60 * 1000
    }

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun observeTodayDoses(ownerProfileId: String, date: LocalDate): Flow<List<DoseEvent>> {
        return doseEventDao.observeToday(ownerProfileId, date.format(dateFormatter)).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeHistory(ownerProfileId: String, from: LocalDate, to: LocalDate): Flow<List<DoseEvent>> {
        return doseEventDao.observeHistory(
            ownerProfileId = ownerProfileId,
            fromDate = from.format(dateFormatter),
            toDate = to.format(dateFormatter)
        ).map { items -> items.map { it.toDomain() } }
    }

    override fun observeHiddenIntakeLogs(ownerProfileId: String): Flow<List<HiddenIntakeLog>> {
        return hiddenIntakeLogDao.observeForOwner(ownerProfileId).map { items ->
            items.map { it.toDomain() }
        }
    }

    override fun observeMedications(ownerProfileId: String): Flow<List<Medication>> {
        return medicationDao.observeAllWithSchedule(ownerProfileId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun saveMedication(input: MedicationInput): String {
        val now = System.currentTimeMillis()
        val medicationId = input.id ?: UUID.randomUUID().toString()
        medicationDao.insertMedication(
            MedicationEntity(
                id = medicationId,
                ownerProfileId = input.ownerProfileId,
                name = input.name,
                doseText = input.doseText,
                doseUnit = input.doseUnit,
                foodRelation = input.foodRelation.name,
                scheduleMode = input.scheduleMode.name,
                intervalHours = input.intervalHours,
                windowStartTime = input.windowStartTime?.format(timeFormatter),
                windowEndTime = input.windowEndTime?.format(timeFormatter),
                startDate = input.startDate.format(dateFormatter),
                endDate = input.endDate?.format(dateFormatter),
                notes = input.notes,
                isActive = input.isActive,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )

        medicationDao.clearWeekdays(medicationId)
        medicationDao.clearTimes(medicationId)

        medicationDao.insertWeekdays(input.weekdays.distinct().sorted().map {
            MedicationWeekdayEntity(medicationId = medicationId, weekday = it)
        })

        medicationDao.insertTimes(input.times.sorted().mapIndexed { idx, time ->
            MedicationTimeEntity(
                id = UUID.randomUUID().toString(),
                medicationId = medicationId,
                localTime = time.format(timeFormatter),
                sortOrder = idx
            )
        })

        safeEnqueueSync {
            cloudSyncManager.queueMedicationUpsert(medicationId)
        }
        return medicationId
    }

    override suspend fun updateMedication(input: MedicationInput) {
        val medicationId = requireNotNull(input.id) { "Medication id is required for update" }
        val now = System.currentTimeMillis()
        val existing = medicationDao.getById(medicationId)
        medicationDao.updateMedication(
            MedicationEntity(
                id = medicationId,
                ownerProfileId = input.ownerProfileId,
                name = input.name,
                doseText = input.doseText,
                doseUnit = input.doseUnit,
                foodRelation = input.foodRelation.name,
                scheduleMode = input.scheduleMode.name,
                intervalHours = input.intervalHours,
                windowStartTime = input.windowStartTime?.format(timeFormatter),
                windowEndTime = input.windowEndTime?.format(timeFormatter),
                startDate = input.startDate.format(dateFormatter),
                endDate = input.endDate?.format(dateFormatter),
                notes = input.notes,
                isActive = input.isActive,
                createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                updatedAtEpochMs = now
            )
        )

        medicationDao.clearWeekdays(medicationId)
        medicationDao.clearTimes(medicationId)

        medicationDao.insertWeekdays(input.weekdays.distinct().sorted().map {
            MedicationWeekdayEntity(medicationId = medicationId, weekday = it)
        })

        medicationDao.insertTimes(input.times.sorted().mapIndexed { idx, time ->
            MedicationTimeEntity(
                id = UUID.randomUUID().toString(),
                medicationId = medicationId,
                localTime = time.format(timeFormatter),
                sortOrder = idx
            )
        })

        val deletedDoseEventIds = doseEventDao.getNonTakenIdsByMedication(medicationId)
        doseEventDao.deleteNonTakenByMedication(medicationId)
        safeEnqueueSync {
            deletedDoseEventIds.forEach { doseEventId ->
                cloudSyncManager.queueDoseEventTombstone(doseEventId, now)
            }
            cloudSyncManager.queueMedicationUpsert(medicationId)
        }
    }

    override suspend fun disableMedication(medicationId: String) {
        val now = System.currentTimeMillis()
        val deletedDoseEventIds = doseEventDao.getNonTakenIdsByMedication(medicationId)
        medicationDao.deactivateMedication(medicationId, now)
        doseEventDao.deleteNonTakenByMedication(medicationId)
        safeEnqueueSync {
            deletedDoseEventIds.forEach { doseEventId ->
                cloudSyncManager.queueDoseEventTombstone(doseEventId, now)
            }
            cloudSyncManager.queueMedicationUpsert(medicationId)
        }
    }

    override suspend fun restoreMedication(medicationId: String) {
        medicationDao.activateMedication(medicationId, System.currentTimeMillis())
        safeEnqueueSync {
            cloudSyncManager.queueMedicationUpsert(medicationId)
        }
    }

    override suspend fun deleteMedication(medicationId: String) {
        val now = System.currentTimeMillis()
        val deletedDoseEventIds = doseEventDao.getNonTakenIdsByMedication(medicationId)
        medicationDao.deactivateMedication(medicationId, now)
        doseEventDao.deleteNonTakenByMedication(medicationId)
        safeEnqueueSync {
            deletedDoseEventIds.forEach { doseEventId ->
                cloudSyncManager.queueDoseEventTombstone(doseEventId, now)
            }
            cloudSyncManager.queueMedicationUpsert(medicationId)
        }
    }

    override suspend fun generateDoseEvents(ownerProfileId: String, from: LocalDate, days: Int) {
        val meds = medicationDao.getActiveWithSchedule(ownerProfileId)
        val zone = zoneProvider.currentZone()
        val nowEpochMs = clock.now().toEpochMilli()
        val todayInZone = clock.now().atZone(zone).toLocalDate()
        val entities = buildList {
            meds.forEach { med ->
                val start = LocalDate.parse(med.medication.startDate, dateFormatter)
                val end = med.medication.endDate?.let { LocalDate.parse(it, dateFormatter) }
                val medWeekdays = med.weekdays.map { it.weekday }
                val scheduleMode = med.medication.scheduleMode
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { ScheduleMode.valueOf(it) }.getOrNull() }
                    ?: ScheduleMode.FIXED_TIMES
                val intervalDays = med.medication.intervalHours ?: 1
                repeat(days) { offset ->
                    val date = from.plusDays(offset.toLong())
                    val isEligible = when (scheduleMode) {
                        ScheduleMode.EVERY_X_DAYS -> scheduleEngine.isDateEligibleEveryXDays(
                            date = date,
                            start = start,
                            end = end,
                            intervalDays = intervalDays
                        )
                        ScheduleMode.FIXED_TIMES,
                        ScheduleMode.EVERY_X_HOURS -> scheduleEngine.isDateEligible(date, start, end, medWeekdays)
                    }
                    if (!isEligible) return@repeat

                    med.times.forEach { timeEntity ->
                        val localTime = LocalTime.parse(timeEntity.localTime, timeFormatter)
                        val scheduled = scheduleEngine.toEpochMs(date, localTime, zone)
                        if (!shouldCreatePendingDoseEvent(scheduleMode, scheduled, date, todayInZone, nowEpochMs)) {
                            return@forEach
                        }
                        add(
                            DoseEventEntity(
                                id = "${med.medication.id}_$scheduled",
                                ownerProfileId = ownerProfileId,
                                medicationId = med.medication.id,
                                medicationName = med.medication.name,
                                scheduledEpochMs = scheduled,
                                localDate = date.format(dateFormatter),
                                localTime = localTime.format(timeFormatter),
                                status = DoseStatus.PENDING.name,
                                actionSource = null,
                                reminderAttemptCount = 0,
                                snoozeUntilEpochMs = null,
                                respondedEpochMs = null,
                                createdAtEpochMs = System.currentTimeMillis(),
                                updatedAtEpochMs = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
        if (entities.isNotEmpty()) {
            doseEventDao.insertAll(entities)
            entities.forEach { entity ->
                safeEnqueueSync {
                    cloudSyncManager.queueDoseEventUpsert(entity.id)
                }
            }
        }
    }

    override suspend fun applyDoseAction(
        doseEventId: String,
        action: IntakeAction,
        source: ActionSource,
        actedAt: Instant
    ) {
        val current = doseEventDao.getById(doseEventId) ?: return
        val newStatus = when (action) {
            IntakeAction.TAKEN -> DoseStatus.TAKEN
        }
        doseEventDao.updateStatus(
            doseEventId = doseEventId,
            status = newStatus.name,
            actionSource = source.name,
            attemptCount = current.reminderAttemptCount,
            snoozeUntil = null,
            respondedAt = actedAt.toEpochMilli(),
            updatedAt = actedAt.toEpochMilli()
        )
        safeEnqueueSync {
            cloudSyncManager.queueDoseEventUpsert(doseEventId)
        }
        if (action == IntakeAction.TAKEN) {
            val logId = logHiddenIntake(
                ownerProfileId = current.ownerProfileId,
                doseEventId = current.id,
                medicationId = current.medicationId,
                medicationName = current.medicationName,
                operationType = IntakeLogOperation.CHANGE,
                operationDetails = "status:PENDING->TAKEN",
                source = source,
                takenAtEpochMs = actedAt.toEpochMilli()
            )
            safeEnqueueSync {
                cloudSyncManager.queueHiddenLogUpsert(logId)
            }
        }
    }

    override suspend fun updateDoseEventTime(doseEventId: String, newLocalTime: LocalTime) {
        val current = doseEventDao.getById(doseEventId) ?: return
        val date = LocalDate.parse(current.localDate, dateFormatter)
        val zone = zoneProvider.currentZone()
        val scheduledEpochMs = scheduleEngine.toEpochMs(date, newLocalTime, zone)
        val now = clock.now().toEpochMilli()
        doseEventDao.updateTime(
            doseEventId = doseEventId,
            localTime = newLocalTime.format(timeFormatter),
            scheduledEpochMs = scheduledEpochMs,
            respondedAt = if (current.respondedEpochMs != null) scheduledEpochMs else null,
            updatedAt = now
        )
        safeEnqueueSync {
            cloudSyncManager.queueDoseEventUpsert(doseEventId)
        }
        val logId = logHiddenIntake(
            ownerProfileId = current.ownerProfileId,
            doseEventId = current.id,
            medicationId = current.medicationId,
            medicationName = current.medicationName,
            operationType = IntakeLogOperation.UPDATE,
            operationDetails = "localTime:${current.localTime}->${newLocalTime.format(timeFormatter)}",
            source = current.actionSource?.toActionSource() ?: ActionSource.APP,
            takenAtEpochMs = now
        )
        safeEnqueueSync {
            cloudSyncManager.queueHiddenLogUpsert(logId)
        }
    }

    override suspend fun deleteDoseEvent(doseEventId: String) {
        val current = doseEventDao.getById(doseEventId) ?: return
        val now = clock.now().toEpochMilli()
        val millisSinceLastEdit = now - current.updatedAtEpochMs
        val hasCloudDeletionMark = hiddenIntakeLogDao.hasCloudDeletionForDoseEventOrMedication(
            ownerProfileId = current.ownerProfileId,
            doseEventId = current.id,
            medicationId = current.medicationId
        )
        if (millisSinceLastEdit > DELETE_WINDOW_MS && !hasCloudDeletionMark) {
            val logId = logHiddenIntake(
                ownerProfileId = current.ownerProfileId,
                doseEventId = current.id,
                medicationId = current.medicationId,
                medicationName = current.medicationName,
                operationType = IntakeLogOperation.CHANGE,
                operationDetails = "delete_denied:outside_edit_window",
                source = current.actionSource?.toActionSource() ?: ActionSource.APP,
                takenAtEpochMs = now
            )
            safeEnqueueSync {
                cloudSyncManager.queueHiddenLogUpsert(logId)
            }
            throw IllegalStateException("Cannot delete dose event after 2 hours from last edit")
        }
        doseEventDao.deleteById(doseEventId)
        safeEnqueueSync {
            cloudSyncManager.queueDoseEventTombstone(doseEventId = doseEventId, deletedAtEpochMs = now)
        }
        val logId = logHiddenIntake(
            ownerProfileId = current.ownerProfileId,
            doseEventId = current.id,
            medicationId = current.medicationId,
            medicationName = current.medicationName,
            operationType = IntakeLogOperation.DELETE,
            operationDetails = "event_deleted",
            source = current.actionSource?.toActionSource() ?: ActionSource.APP,
            takenAtEpochMs = now
        )
        safeEnqueueSync {
            cloudSyncManager.queueHiddenLogUpsert(logId)
        }
    }

    override suspend fun addAsNeededMedication(ownerProfileId: String, name: String): String {
        val now = System.currentTimeMillis()
        val medicationId = UUID.randomUUID().toString()
        val today = LocalDate.now(zoneProvider.currentZone())
        medicationDao.insertMedication(
            MedicationEntity(
                id = medicationId,
                ownerProfileId = ownerProfileId,
                name = name.trim(),
                doseText = "1",
                doseUnit = "יחידה",
                foodRelation = FoodRelation.NONE.name,
                scheduleMode = ScheduleMode.FIXED_TIMES.name,
                intervalHours = null,
                windowStartTime = null,
                windowEndTime = null,
                startDate = today.format(dateFormatter),
                endDate = null,
                notes = "",
                isActive = true,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )
        medicationDao.clearWeekdays(medicationId)
        medicationDao.clearTimes(medicationId)
        safeEnqueueSync {
            cloudSyncManager.queueMedicationUpsert(medicationId)
        }
        return medicationId
    }

    override suspend fun addOneTimeMedicationIntake(ownerProfileId: String, name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        val now = clock.now()
        val nowEpochMs = now.toEpochMilli()
        val zone = zoneProvider.currentZone()
        val zonedNow = ZonedDateTime.ofInstant(now, zone)
        val medicationId = UUID.randomUUID().toString()
        val today = zonedNow.toLocalDate()

        medicationDao.insertMedication(
            MedicationEntity(
                id = medicationId,
                ownerProfileId = ownerProfileId,
                name = trimmedName,
                doseText = "1",
                doseUnit = "יחידה",
                foodRelation = FoodRelation.NONE.name,
                scheduleMode = ScheduleMode.FIXED_TIMES.name,
                intervalHours = null,
                windowStartTime = null,
                windowEndTime = null,
                startDate = today.format(dateFormatter),
                endDate = today.format(dateFormatter),
                notes = "",
                isActive = false,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        )
        medicationDao.clearWeekdays(medicationId)
        medicationDao.clearTimes(medicationId)

        val eventId = UUID.randomUUID().toString()
        doseEventDao.upsert(
            DoseEventEntity(
                id = eventId,
                ownerProfileId = ownerProfileId,
                medicationId = medicationId,
                medicationName = trimmedName,
                scheduledEpochMs = nowEpochMs,
                localDate = today.format(dateFormatter),
                localTime = zonedNow.toLocalTime().format(timeFormatter),
                status = DoseStatus.TAKEN.name,
                actionSource = ActionSource.APP.name,
                reminderAttemptCount = 0,
                snoozeUntilEpochMs = null,
                respondedEpochMs = nowEpochMs,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        )
        safeEnqueueSync {
            cloudSyncManager.queueMedicationUpsert(medicationId)
            cloudSyncManager.queueDoseEventUpsert(eventId)
        }
        val logId = logHiddenIntake(
            ownerProfileId = ownerProfileId,
            doseEventId = eventId,
            medicationId = medicationId,
            medicationName = trimmedName,
            operationType = IntakeLogOperation.ADD,
            operationDetails = "one_time_intake_added",
            source = ActionSource.APP,
            takenAtEpochMs = nowEpochMs
        )
        safeEnqueueSync {
            cloudSyncManager.queueHiddenLogUpsert(logId)
        }
    }

    override suspend fun renameMedication(medicationId: String, name: String) {
        medicationDao.updateMedicationName(
            medicationId = medicationId,
            name = name.trim(),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        safeEnqueueSync {
            cloudSyncManager.queueMedicationUpsert(medicationId)
        }
    }

    override suspend fun markMedicationTakenNow(medicationId: String) {
        val medication = medicationDao.getById(medicationId) ?: return
        val now = clock.now()
        val zone = zoneProvider.currentZone()
        val zonedNow = ZonedDateTime.ofInstant(now, zone)
        val nowEpochMs = now.toEpochMilli()
        val eventId = UUID.randomUUID().toString()
        doseEventDao.upsert(
            DoseEventEntity(
                id = eventId,
                ownerProfileId = medication.ownerProfileId,
                medicationId = medicationId,
                medicationName = medication.name,
                scheduledEpochMs = nowEpochMs,
                localDate = zonedNow.toLocalDate().format(dateFormatter),
                localTime = zonedNow.toLocalTime().format(timeFormatter),
                status = DoseStatus.TAKEN.name,
                actionSource = ActionSource.APP.name,
                reminderAttemptCount = 0,
                snoozeUntilEpochMs = null,
                respondedEpochMs = nowEpochMs,
                createdAtEpochMs = nowEpochMs,
                updatedAtEpochMs = nowEpochMs
            )
        )
        safeEnqueueSync {
            cloudSyncManager.queueDoseEventUpsert(eventId)
        }
        val logId = logHiddenIntake(
            ownerProfileId = medication.ownerProfileId,
            doseEventId = eventId,
            medicationId = medicationId,
            medicationName = medication.name,
            operationType = IntakeLogOperation.ADD,
            operationDetails = "as_needed_intake_added",
            source = ActionSource.APP,
            takenAtEpochMs = nowEpochMs
        )
        safeEnqueueSync {
            cloudSyncManager.queueHiddenLogUpsert(logId)
        }
    }

    override suspend fun getDoseEvent(doseEventId: String): DoseEvent? = doseEventDao.getById(doseEventId)?.toDomain()

    override suspend fun getPendingDoseEvents(ownerProfileId: String): List<DoseEvent> {
        return doseEventDao.getPending(ownerProfileId).map { it.toDomain() }
    }

    override suspend fun markMissedBefore(threshold: Instant): Int {
        val staleEvents = doseEventDao.getPendingBefore(threshold.toEpochMilli())
        if (staleEvents.isEmpty()) return 0

        val nowEpochMs = clock.now().toEpochMilli()
        val staleIds = staleEvents.map { it.id }
        val updated = doseEventDao.updatePendingStatuses(
            doseEventIds = staleIds,
            status = DoseStatus.MISSED.name,
            actionSource = ActionSource.SYSTEM.name,
            respondedAt = nowEpochMs,
            updatedAt = nowEpochMs
        )
        if (updated <= 0) return 0

        staleEvents.forEach { event ->
            safeEnqueueSync {
                cloudSyncManager.queueDoseEventUpsert(event.id)
            }
            val logId = logHiddenIntake(
                ownerProfileId = event.ownerProfileId,
                doseEventId = event.id,
                medicationId = event.medicationId,
                medicationName = event.medicationName,
                operationType = IntakeLogOperation.CHANGE,
                operationDetails = "status:PENDING->MISSED",
                source = ActionSource.SYSTEM,
                takenAtEpochMs = nowEpochMs
            )
            safeEnqueueSync {
                cloudSyncManager.queueHiddenLogUpsert(logId)
            }
        }
        return updated
    }

    override suspend fun registerReminderAttempt(
        doseEventId: String,
        repeatIntervalMinutes: Int,
        repeatMaxCount: Int
    ): Instant? {
        val event = doseEventDao.getById(doseEventId) ?: return null
        val status = event.status.toDoseStatus()
        if (status != DoseStatus.PENDING) return null
        val nextAttempt = event.reminderAttemptCount + 1
        if (nextAttempt > repeatMaxCount) return null
        doseEventDao.incrementAttempt(doseEventId, updatedAt = System.currentTimeMillis())
        safeEnqueueSync {
            cloudSyncManager.queueDoseEventUpsert(doseEventId)
        }
        return Instant.ofEpochMilli(System.currentTimeMillis() + repeatIntervalMinutes * 60_000L)
    }

    private fun MedicationWithSchedule.toDomain(): Medication {
        return Medication(
            id = medication.id,
            ownerProfileId = medication.ownerProfileId,
            name = medication.name,
            doseText = medication.doseText,
            doseUnit = medication.doseUnit,
            foodRelation = FoodRelation.valueOf(medication.foodRelation),
            scheduleMode = medication.scheduleMode
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { ScheduleMode.valueOf(it) }.getOrNull() }
                ?: ScheduleMode.FIXED_TIMES,
            intervalHours = medication.intervalHours,
            windowStartTime = medication.windowStartTime?.let { LocalTime.parse(it, timeFormatter) },
            windowEndTime = medication.windowEndTime?.let { LocalTime.parse(it, timeFormatter) },
            startDate = LocalDate.parse(medication.startDate, dateFormatter),
            endDate = medication.endDate?.let { LocalDate.parse(it, dateFormatter) },
            notes = medication.notes,
            weekdays = weekdays.map { it.weekday }.sorted(),
            times = times.sortedBy { it.sortOrder }.map { LocalTime.parse(it.localTime, timeFormatter) },
            isActive = medication.isActive
        )
    }

    private fun DoseEventEntity.toDomain(): DoseEvent {
        return DoseEvent(
            id = id,
            ownerProfileId = ownerProfileId,
            medicationId = medicationId,
            medicationName = medicationName,
            scheduledAt = Instant.ofEpochMilli(scheduledEpochMs),
            localDate = LocalDate.parse(localDate, dateFormatter),
            localTime = LocalTime.parse(localTime, timeFormatter),
            status = status.toDoseStatus(),
            actionSource = actionSource?.toActionSource(),
            reminderAttemptCount = reminderAttemptCount,
            snoozeUntil = snoozeUntilEpochMs?.let(Instant::ofEpochMilli),
            respondedAt = respondedEpochMs?.let(Instant::ofEpochMilli),
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMs)
        )
    }

    private fun String.toDoseStatus(): DoseStatus {
        return when (this) {
            DoseStatus.TAKEN.name -> DoseStatus.TAKEN
            DoseStatus.MISSED.name -> DoseStatus.MISSED
            else -> DoseStatus.PENDING
        }
    }

    private fun String.toActionSource(): ActionSource? {
        return runCatching { ActionSource.valueOf(this) }.getOrNull()
    }

    private suspend fun logHiddenIntake(
        ownerProfileId: String,
        doseEventId: String?,
        medicationId: String,
        medicationName: String,
        operationType: IntakeLogOperation,
        operationDetails: String?,
        source: ActionSource,
        takenAtEpochMs: Long
    ): String {
        val logId = UUID.randomUUID().toString()
        hiddenIntakeLogDao.insert(
            HiddenIntakeLogEntity(
                id = logId,
                ownerProfileId = ownerProfileId,
                doseEventId = doseEventId,
                medicationId = medicationId,
                medicationName = medicationName,
                operationType = operationType.name,
                operationDetails = operationDetails,
                actionSource = source.name,
                takenAtEpochMs = takenAtEpochMs,
                createdAtEpochMs = takenAtEpochMs
            )
        )
        return logId
    }

    private suspend fun safeEnqueueSync(block: suspend () -> Unit) {
        runCatching { block() }
    }

    private fun HiddenIntakeLogEntity.toDomain(): HiddenIntakeLog {
        return HiddenIntakeLog(
            id = id,
            doseEventId = doseEventId,
            medicationId = medicationId,
            medicationName = medicationName,
            operationType = operationType.toIntakeLogOperation(),
            operationDetails = operationDetails,
            actionSource = actionSource.toActionSource() ?: ActionSource.APP,
            takenAt = Instant.ofEpochMilli(takenAtEpochMs)
        )
    }

    private fun String.toIntakeLogOperation(): IntakeLogOperation {
        return runCatching { IntakeLogOperation.valueOf(this) }.getOrDefault(IntakeLogOperation.CHANGE)
    }
}

internal fun shouldCreatePendingDoseEvent(
    scheduleMode: ScheduleMode,
    scheduledEpochMs: Long,
    eventDate: LocalDate,
    today: LocalDate,
    nowEpochMs: Long
): Boolean {
    return scheduledEpochMs >= nowEpochMs ||
        (scheduleMode == ScheduleMode.EVERY_X_DAYS && eventDate == today)
}
