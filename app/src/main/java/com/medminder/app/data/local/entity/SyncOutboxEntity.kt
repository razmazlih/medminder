package com.medminder.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["recordType", "recordId"], unique = true),
        Index(value = ["queuedAtEpochMs"])
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey val id: String,
    val recordType: String,
    val recordId: String,
    val operation: String,
    val payloadJson: String?,
    val updatedAtEpochMs: Long,
    val deletedAtEpochMs: Long?,
    val attemptCount: Int,
    val lastAttemptAtEpochMs: Long?,
    val queuedAtEpochMs: Long
)
