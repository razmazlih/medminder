package com.medminder.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.medminder.app.data.local.entity.SyncOutboxEntity

@Dao
interface SyncOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox ORDER BY queuedAtEpochMs ASC LIMIT :limit")
    suspend fun getPending(limit: Int): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "UPDATE sync_outbox SET attemptCount = attemptCount + 1, lastAttemptAtEpochMs = :attemptedAtEpochMs " +
            "WHERE id = :id"
    )
    suspend fun markAttempt(id: String, attemptedAtEpochMs: Long)

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun pendingCount(): Int
}
