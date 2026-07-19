package com.medminder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.medminder.app.data.local.entity.DoseEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseEventDao {

    @Query(
        "SELECT * FROM dose_event WHERE ownerProfileId = :ownerProfileId " +
            "AND localDate = :localDate ORDER BY scheduledEpochMs"
    )
    fun observeToday(ownerProfileId: String, localDate: String): Flow<List<DoseEventEntity>>

    @Query(
        "SELECT * FROM dose_event WHERE ownerProfileId = :ownerProfileId " +
            "AND localDate BETWEEN :fromDate AND :toDate " +
            "AND status = 'TAKEN' ORDER BY scheduledEpochMs DESC"
    )
    fun observeHistory(ownerProfileId: String, fromDate: String, toDate: String): Flow<List<DoseEventEntity>>

    @Query("SELECT * FROM dose_event WHERE id = :doseEventId LIMIT 1")
    suspend fun getById(doseEventId: String): DoseEventEntity?

    @Query(
        "SELECT * FROM dose_event WHERE ownerProfileId = :ownerProfileId " +
            "AND status = 'PENDING' ORDER BY scheduledEpochMs"
    )
    suspend fun getPending(ownerProfileId: String): List<DoseEventEntity>

    @Query(
        "SELECT * FROM dose_event WHERE ownerProfileId = :ownerProfileId " +
            "ORDER BY updatedAtEpochMs ASC"
    )
    suspend fun getAll(ownerProfileId: String): List<DoseEventEntity>

    @Query(
        "SELECT * FROM dose_event WHERE status = 'PENDING' " +
            "AND scheduledEpochMs < :thresholdEpochMs ORDER BY scheduledEpochMs"
    )
    suspend fun getPendingBefore(thresholdEpochMs: Long): List<DoseEventEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(values: List<DoseEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: DoseEventEntity)

    @Query("DELETE FROM dose_event WHERE medicationId = :medicationId")
    suspend fun deleteByMedication(medicationId: String)

    @Query("DELETE FROM dose_event WHERE medicationId = :medicationId AND status != 'TAKEN'")
    suspend fun deleteNonTakenByMedication(medicationId: String)

    @Query("SELECT id FROM dose_event WHERE medicationId = :medicationId AND status != 'TAKEN'")
    suspend fun getNonTakenIdsByMedication(medicationId: String): List<String>

    @Query("DELETE FROM dose_event WHERE id = :doseEventId")
    suspend fun deleteById(doseEventId: String)

    @Query(
        "UPDATE dose_event SET status = :status, actionSource = :actionSource, reminderAttemptCount = :attemptCount, " +
            "snoozeUntilEpochMs = :snoozeUntil, respondedEpochMs = :respondedAt, updatedAtEpochMs = :updatedAt " +
            "WHERE id = :doseEventId"
    )
    suspend fun updateStatus(
        doseEventId: String,
        status: String,
        actionSource: String?,
        attemptCount: Int,
        snoozeUntil: Long?,
        respondedAt: Long?,
        updatedAt: Long
    )

    @Query(
        "UPDATE dose_event SET reminderAttemptCount = reminderAttemptCount + 1, updatedAtEpochMs = :updatedAt " +
            "WHERE id = :doseEventId"
    )
    suspend fun incrementAttempt(doseEventId: String, updatedAt: Long)

    @Query(
        "UPDATE dose_event SET localTime = :localTime, scheduledEpochMs = :scheduledEpochMs, " +
            "respondedEpochMs = :respondedAt, updatedAtEpochMs = :updatedAt WHERE id = :doseEventId"
    )
    suspend fun updateTime(
        doseEventId: String,
        localTime: String,
        scheduledEpochMs: Long,
        respondedAt: Long?,
        updatedAt: Long
    )

    @Query(
        "UPDATE dose_event SET status = :status, actionSource = :actionSource, " +
            "snoozeUntilEpochMs = NULL, respondedEpochMs = :respondedAt, updatedAtEpochMs = :updatedAt " +
            "WHERE id IN (:doseEventIds) AND status = 'PENDING'"
    )
    suspend fun updatePendingStatuses(
        doseEventIds: List<String>,
        status: String,
        actionSource: String,
        respondedAt: Long,
        updatedAt: Long
    ): Int

}
