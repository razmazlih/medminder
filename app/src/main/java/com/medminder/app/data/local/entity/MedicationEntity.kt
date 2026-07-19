package com.medminder.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medication",
    indices = [Index(value = ["ownerProfileId", "isActive"])]
)
data class MedicationEntity(
    @PrimaryKey val id: String,
    val ownerProfileId: String,
    val name: String,
    val doseText: String,
    val doseUnit: String,
    val foodRelation: String,
    val scheduleMode: String,
    val intervalHours: Int?,
    val windowStartTime: String?,
    val windowEndTime: String?,
    val startDate: String,
    val endDate: String?,
    val notes: String,
    val isActive: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
