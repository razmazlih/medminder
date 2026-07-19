package com.medminder.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "medication_weekday",
    primaryKeys = ["medicationId", "weekday"],
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
data class MedicationWeekdayEntity(
    val medicationId: String,
    val weekday: Int
)
