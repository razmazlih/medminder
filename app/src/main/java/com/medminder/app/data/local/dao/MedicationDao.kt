package com.medminder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.medminder.app.data.local.entity.MedicationEntity
import com.medminder.app.data.local.entity.MedicationTimeEntity
import com.medminder.app.data.local.entity.MedicationWeekdayEntity
import com.medminder.app.data.local.entity.MedicationWithSchedule
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Transaction
    @Query("SELECT * FROM medication WHERE ownerProfileId = :ownerProfileId ORDER BY name")
    fun observeAllWithSchedule(ownerProfileId: String): Flow<List<MedicationWithSchedule>>

    @Transaction
    @Query("SELECT * FROM medication WHERE id = :medicationId LIMIT 1")
    suspend fun getWithScheduleById(medicationId: String): MedicationWithSchedule?

    @Transaction
    @Query("SELECT * FROM medication WHERE ownerProfileId = :ownerProfileId AND isActive = 1 ORDER BY name")
    suspend fun getActiveWithSchedule(ownerProfileId: String): List<MedicationWithSchedule>

    @Transaction
    @Query("SELECT * FROM medication WHERE ownerProfileId = :ownerProfileId ORDER BY name")
    suspend fun getAllWithSchedule(ownerProfileId: String): List<MedicationWithSchedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(entity: MedicationEntity)

    @Query("SELECT * FROM medication WHERE id = :medicationId LIMIT 1")
    suspend fun getById(medicationId: String): MedicationEntity?

    @Update
    suspend fun updateMedication(entity: MedicationEntity)

    @Query("UPDATE medication SET isActive = 0, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :medicationId")
    suspend fun deactivateMedication(medicationId: String, updatedAtEpochMs: Long)

    @Query("UPDATE medication SET isActive = 1, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :medicationId")
    suspend fun activateMedication(medicationId: String, updatedAtEpochMs: Long)

    @Query("DELETE FROM medication WHERE id = :medicationId")
    suspend fun deleteMedication(medicationId: String)

    @Query("UPDATE medication SET name = :name, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :medicationId")
    suspend fun updateMedicationName(medicationId: String, name: String, updatedAtEpochMs: Long)

    @Query("DELETE FROM medication_weekday WHERE medicationId = :medicationId")
    suspend fun clearWeekdays(medicationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeekdays(values: List<MedicationWeekdayEntity>)

    @Query("DELETE FROM medication_time WHERE medicationId = :medicationId")
    suspend fun clearTimes(medicationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimes(values: List<MedicationTimeEntity>)
}
