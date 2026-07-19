package com.medminder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.medminder.app.data.local.entity.HiddenIntakeLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenIntakeLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(value: HiddenIntakeLogEntity)

    @Query(
        "SELECT * FROM hidden_intake_log WHERE ownerProfileId = :ownerProfileId " +
            "ORDER BY takenAtEpochMs DESC"
    )
    fun observeForOwner(ownerProfileId: String): Flow<List<HiddenIntakeLogEntity>>

    @Query(
        "SELECT * FROM hidden_intake_log WHERE ownerProfileId = :ownerProfileId " +
            "ORDER BY createdAtEpochMs ASC"
    )
    suspend fun getAll(ownerProfileId: String): List<HiddenIntakeLogEntity>

    @Query("SELECT * FROM hidden_intake_log WHERE id = :logId LIMIT 1")
    suspend fun getById(logId: String): HiddenIntakeLogEntity?

    @Query("SELECT COUNT(*) FROM hidden_intake_log WHERE operationDetails = :operationDetails")
    suspend fun countByOperationDetails(operationDetails: String): Int

    @Query(
        "SELECT EXISTS(" +
            "SELECT 1 FROM hidden_intake_log " +
            "WHERE ownerProfileId = :ownerProfileId " +
            "AND (" +
            "operationDetails LIKE 'cloud_deleted:DOSE_EVENT:' || :doseEventId || ':%' " +
            "OR operationDetails LIKE 'cloud_deleted:MEDICATION:' || :medicationId || ':%'" +
            ")" +
            ")"
    )
    suspend fun hasCloudDeletionForDoseEventOrMedication(
        ownerProfileId: String,
        doseEventId: String,
        medicationId: String
    ): Boolean
}
