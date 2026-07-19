package com.medminder.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class MedicationWithSchedule(
    @Embedded val medication: MedicationEntity,
    @Relation(parentColumn = "id", entityColumn = "medicationId")
    val weekdays: List<MedicationWeekdayEntity>,
    @Relation(parentColumn = "id", entityColumn = "medicationId")
    val times: List<MedicationTimeEntity>
)
