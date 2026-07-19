package com.medminder.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hidden_intake_log",
    indices = [
        Index(value = ["ownerProfileId", "takenAtEpochMs"])
    ]
)
data class HiddenIntakeLogEntity(
    @PrimaryKey val id: String,
    val ownerProfileId: String,
    val doseEventId: String?,
    val medicationId: String,
    val medicationName: String,
    val operationType: String,
    val operationDetails: String?,
    val actionSource: String,
    val takenAtEpochMs: Long,
    val createdAtEpochMs: Long
)
