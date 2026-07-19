package com.medminder.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dose_event",
    indices = [
        Index(value = ["localDate", "status"]),
        Index(value = ["scheduledEpochMs"]),
        Index(value = ["medicationId", "scheduledEpochMs"], unique = true),
        Index(value = ["ownerProfileId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DoseEventEntity(
    @PrimaryKey val id: String,
    val ownerProfileId: String,
    val medicationId: String,
    val medicationName: String,
    val scheduledEpochMs: Long,
    val localDate: String,
    val localTime: String,
    val status: String,
    val actionSource: String?,
    val reminderAttemptCount: Int,
    val snoozeUntilEpochMs: Long?,
    val respondedEpochMs: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
