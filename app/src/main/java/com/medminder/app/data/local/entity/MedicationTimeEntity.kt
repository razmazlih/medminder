package com.medminder.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medication_time",
    indices = [Index(value = ["medicationId"])],
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MedicationTimeEntity(
    @PrimaryKey val id: String,
    val medicationId: String,
    val localTime: String,
    val sortOrder: Int
)
